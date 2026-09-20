#!/usr/bin/env bash
#
# 切一个版本，一条命令做完全部前置工作。
#
# 为什么要这个脚本
# ---------------
# 发版原先有两个必须人工保持一致的东西：git tag 和 app/version.properties。
# 两者不一致时流水线会停，而且那个校验排在 assembleRelease 之后，一错就白编译
# 一整轮。更糟的是它只在"打完 tag 推上去"之后才暴露，而这个仓库的规矩是
# 已推送的 tag 不移动（见 CHANGELOG 里 1.0.67 那条），于是一次手滑就要跳一个版本号。
#
# 现在版本号的唯一权威是 tag：CI 从 tag 反写 version.properties（release.yml 的
# Resolve Version From Tag），所以"版本与 tag 不一致"在结构上不可能再发生。
# 这个脚本负责另一半——让仓库里的文件、更新日志、tag 一次性同步好再推出去。
#
# 用法
#   scripts/release.sh 1.0.69          # 本地准备好：改文件、写 CHANGELOG、提交、打 tag
#   scripts/release.sh 1.0.69 --push   # 顺带推送（推 tag 即触发发版流水线）
#
# 不加 --push 时只在本地留下一个提交和一个 tag，撤销代价极低：
#   git tag -d v1.0.69 && git reset --hard HEAD~1
#
set -euo pipefail

cd "$(dirname "$0")/.."

VERSION="${1:-}"
PUSH="${2:-}"

die() { printf 'ERROR: %s\n' "$*" >&2; exit 1; }
note() { printf '  %s\n' "$*"; }

if [ -z "$VERSION" ] || [ "$VERSION" = "-h" ] || [ "$VERSION" = "--help" ]; then
    # 打印文件开头那段连续的注释，到第一行非注释为止。
    awk 'NR > 2 { if (!/^#/) exit; sub(/^# ?/, ""); print }' "$0"
    exit 0
fi

case "$VERSION" in
    v*) die "版本号不要带 v 前缀，直接写 ${VERSION#v}" ;;
    [0-9]*.[0-9]*.[0-9]*) ;;
    *) die "版本号形如 X.Y.Z，收到: $VERSION" ;;
esac

TAG="v${VERSION}"

# ── versionCode 编码：major*10000 + minor*100 + patch ────────────────
#
# 别再改成「patch 位」或「独立递增序号」了，这里踩过一次事故，记下来：
#
# 1.0.x 时代 versionCode 恰好等于 patch 位（1.0.74 -> 74），看起来能推算。
# 进了 1.1.x 之后实际改成了独立递增序号（1.1.3->14、1.1.4->15、1.1.5->16），
# 但脚本仍按 patch 位推算，于是 v1.1.6 算出 6 —— 比上一版的 16 倒退了 10。
# 后果是连锁的：应用内 16 > 6 不成立，永远提示「已是最新」；Android 也不允许
# 低 versionCode 覆盖安装，用户连手动升级都做不到。
#
# 根因是「用文件名推导一个语义无关的整数值」。语义化编码把这个自由度消掉：
# 版本号本身决定 code，不存在「忘了递增」的可能。
#   1.1.6  -> 10106
#   1.1.7  -> 10107
#   1.2.0  -> 10200
#   2.0.0  -> 20000
#
# 上界是 2100000000（Android 的 MAX_VERSION_CODE）。major 到 2100 才溢出，
# 这辈子用不到。下面那道检查仍保留，用于拦住「有人把版本号写错」。
IFS=. read -r V_MAJOR V_MINOR V_PATCH <<< "$VERSION"
for part in "$V_MAJOR" "$V_MINOR" "$V_PATCH"; do
    case "$part" in
        ''|*[!0-9]*) die "版本号 ${VERSION} 含非数字段: '$part'" ;;
    esac
done
[ "$V_MINOR" -lt 100 ] || die "minor 位 ${V_MINOR} 超过 99，语义化编码放不下。需要换更宽的编码。"
[ "$V_PATCH" -lt 100 ] || die "patch 位 ${V_PATCH} 超过 99，语义化编码放不下。需要换更宽的编码。"

CODE=$(( V_MAJOR * 10000 + V_MINOR * 100 + V_PATCH ))

NOTES_FILE="release-notes/${TAG}.md"
VERSION_FILE="app/version.properties"
CHANGELOG="CHANGELOG.md"

echo "== 准备发布 ${TAG}（versionCode=${CODE}）=="

# ── 1. 工作区必须干净 ────────────────────────────────────────────────
# 发版提交里只应该有版本号、更新日志这几样。混进别的改动会让 tag 指向一个
# 没人 review 过的状态。
if ! git diff --quiet || ! git diff --cached --quiet; then
    die "工作区有未提交的改动，先提交或 stash。发版提交只应包含版本号与更新日志。"
fi

BRANCH="$(git rev-parse --abbrev-ref HEAD)"
if [ "$BRANCH" != "main" ]; then
    note "警告：当前在 ${BRANCH} 而不是 main"
fi

# ── 2. tag 不能已存在 ────────────────────────────────────────────────
# 已推送的 tag 不移动：Gitee 的下载地址按 tag 拼，移动 tag 会让已发出去的
# 链接指向另一个包。要重发就顺延到下一个版本号。
if git rev-parse -q --verify "refs/tags/${TAG}" >/dev/null; then
    die "${TAG} 已存在于本地。已发布的 tag 不移动，请顺延到下一个版本号。"
fi
if git ls-remote --exit-code --tags origin "refs/tags/${TAG}" >/dev/null 2>&1; then
    die "${TAG} 已存在于远端。已发布的 tag 不移动，请顺延到下一个版本号。"
fi

# ── 3. versionCode 必须单调递增 ──────────────────────────────────────
# 应用内比较的是 versionCode。它一旦不增，用户会收到一个永远"更新不掉"的提示，
# 而且 Android 会直接拒绝低 code 的包覆盖安装。
#
# 基准取「历史上真实打出过的最大 versionCode」，而不是从上一个 tag 名推算——
# 旧脚本正是栽在这里：v1.1.5 的 tag 名看起来是 5，实际发出去的 code 是 16。
# CI 把每次的 code 写进 version.properties，从 tag 里读它才是可信的。
#
# 排除本项目要打的这个 tag：虽然第 2 步已确认它不存在，但若有人在打过 tag 之后
# 重跑脚本做校验，不排除就会「跟自己比」而误判成不增。
MAX_CODE=0
MAX_TAG=""
while IFS= read -r t; do
    [ -z "$t" ] && continue
    [ "$t" = "$TAG" ] && continue
    c="$(git show "${t}:app/version.properties" 2>/dev/null \
        | sed -n 's/^VERSION_CODE=[[:space:]]*//p' | tr -d '[:space:]' || true)"
    case "$c" in
        ''|*[!0-9]*) continue ;;
    esac
    if [ "$c" -gt "$MAX_CODE" ]; then
        MAX_CODE="$c"
        MAX_TAG="$t"
    fi
done < <(git tag --list 'v[0-9]*.[0-9]*.[0-9]*')

if [ "$MAX_CODE" -gt 0 ]; then
    if [ "$CODE" -le "$MAX_CODE" ]; then
        die "versionCode 不增：${TAG} 算出 ${CODE}，而历史上最大的 ${MAX_TAG} 是 ${MAX_CODE}。
       语义化编码是 major*10000+minor*100+patch，正常不会倒退。
       请检查版本号是否写错（例如把 ${VERSION} 写成了更小的号）。"
    fi
    note "历史最大 ${MAX_TAG}（code ${MAX_CODE}）→ ${TAG}（code ${CODE}）"
fi

# ── 4. 更新日志：没有就生成骨架并停下 ────────────────────────────────
# 不回落到套话。缺日志就是发布准备没做完，与其静默发一版"自动构建发布"给所有
# 用户，不如现在停下来。CI 也会做同样的检查。
if [ ! -f "$NOTES_FILE" ]; then
    mkdir -p release-notes
    cat > "$NOTES_FILE" <<TEMPLATE
# ${TAG}

<!--
更新日志的唯一数据源。CI（.github/workflows/release.yml）从这里读，扇出到：
  - GitHub Release 的正文
  - Gitee version.json 的 releaseNotes 字段

## notes 区块的硬约束

APP 内的更新弹窗（UpdateDialog.kt）用纯 Text 渲染 releaseNotes，不解析 Markdown。
所以 notes 里不要写 #、*、-、\` 之类的标记，写了会原样显示给用户。
每行一条，CI 按行读取。
-->

## notes

## changelog

<!-- 这一段进 CHANGELOG.md 归档，可以用 Markdown。 -->

TEMPLATE
    die "已生成 ${NOTES_FILE}，请填好 '## notes' 区块后重新运行本脚本。"
fi

# 取出 <notes-file> 里某个 "## X" 区块，剥掉 HTML 注释。
#
# 注释剥离要分两步。只写 `/^<!--/,/-->$/d` 会把单行注释 `<!-- x -->` 当成区间开头，
# 而 sed 的区间结束模式只从下一行开始找，于是一路删到文件末尾——'## changelog'
# 区块正好以这样一行开头，整段就没了。先删单行注释，再删跨行区间。
block_body() {
    awk -v want="$1" '
        $0 ~ "^## " want "[[:space:]]*$" { inside = 1; next }
        /^## / { inside = 0 }
        inside { print }
    ' "$NOTES_FILE" \
        | sed -e '/^[[:space:]]*<!--.*-->[[:space:]]*$/d' -e '/^[[:space:]]*<!--/,/-->/d'
}

# notes：与 CI 用同一条管线（release.yml 的 Resolve Release Notes），连去空行都一样。
# 两边写法必须一致，否则本地过了 CI 还会挂。
extract_notes() {
    block_body notes | sed '/^[[:space:]]*$/d'
}

# changelog：要进 CHANGELOG.md，是 Markdown，内部空行必须留着（小标题与列表之间
# 少一行空行，归档出来的段落就和手写的历史版本长得不一样）。只掐掉首尾空行。
extract_changelog() {
    block_body changelog | awk '
        { line[NR] = $0; if ($0 ~ /[^[:space:]]/) { if (!first) first = NR; last = NR } }
        END { for (i = first; i <= last; i++) print line[i] }
    '
}

NOTES="$(extract_notes)"
[ -n "$NOTES" ] || die "${NOTES_FILE} 的 '## notes' 区块是空的。"

# notes 会被 UpdateDialog 当纯文本渲染，Markdown 标记会原样显示给用户。
if printf '%s\n' "$NOTES" | grep -qE '^[[:space:]]*[#*-]|`'; then
    die "'## notes' 里有 Markdown 标记（# * - 或反引号）。它会原样显示在应用内的更新弹窗里。
$(printf '%s\n' "$NOTES" | grep -nE '^[[:space:]]*[#*-]|`' | sed 's/^/       /')"
fi

note "更新日志 $(printf '%s\n' "$NOTES" | wc -l | tr -d ' ') 行，来自 ${NOTES_FILE}"

# ── 4b. 应用内公告必须存在且合规 ─────────────────────────────────────
# CI 的 Validate Release Announcement 走的是 publish_announcement.py，缺文件
# 直接失败。这个检查原先只存在于 CI，而它排在 assembleRelease 之后——漏写公告
# 要白编译一整轮才暴露。这里用同一个脚本提前拦下。
#
# 用 python 调库而不是在 bash 里手写校验，是为了只有一份规则：CI 与本地跑的是
# 同一段 validate_announcement，不会各写各的然后悄悄分叉。
ANNOUNCEMENT_FILE="release-notes/${TAG}-announcement.json"
if [ ! -f "$ANNOUNCEMENT_FILE" ]; then
    mkdir -p release-notes
    cat > "$ANNOUNCEMENT_FILE" <<TEMPLATE
{
  "id": "$(date +%Y%m%d)_$(printf '%s' "$TAG" | sed 's/\./_/g')_release",
  "title": "${TAG} 更新：",
  "content": "这里写应用内公告的正文，支持 Markdown。\\n\\n用 \\\\n 表示换行。",
  "type": "info",
  "audience": "app",
  "contentType": "markdown",
  "showOnce": true,
  "created_at": "$(date -u +%Y-%m-%dT%H:%M:%SZ)",
  "created_by": "admin"
}
TEMPLATE
    die "已生成 ${ANNOUNCEMENT_FILE}，请填好 title 与 content 后重新运行本脚本。"
fi

PYTHON_BIN=""
for candidate in python3 python py; do
    if command -v "$candidate" >/dev/null 2>&1; then
        PYTHON_BIN="$candidate"
        break
    fi
done

if [ -n "$PYTHON_BIN" ]; then
    "$PYTHON_BIN" - "$TAG" "$ANNOUNCEMENT_FILE" <<'PYCHECK' || die "应用内公告校验未通过，见上方报错。"
import json, sys
from pathlib import Path
sys.path.insert(0, str(Path("scripts").resolve()))
from publish_announcement import validate_announcement
tag, path = sys.argv[1], sys.argv[2]
try:
    validate_announcement(json.loads(Path(path).read_text(encoding="utf-8")), tag)
except (ValueError, json.JSONDecodeError) as exc:
    print(f"ERROR: {path} 不合规: {exc}", file=sys.stderr)
    sys.exit(1)
print(f"  公告 {Path(path).name} 校验通过")
PYCHECK
else
    note "警告：未找到 python，跳过公告校验（CI 仍会检查）"
fi

# ── 5. 写 version.properties ─────────────────────────────────────────
cat > "$VERSION_FILE" <<PROPS
#Release version. 唯一权威是 git tag：CI 会从 tag 反写这个文件
#（release.yml 的 Resolve Version From Tag），所以这里与 tag 不一致也发不错包。
#不要手改：跑 scripts/release.sh X.Y.Z，它会连更新日志和 tag 一起对齐。
VERSION_NAME=${VERSION}
VERSION_CODE=${CODE}
PROPS
note "已写入 ${VERSION_FILE}"

# ── 6. CHANGELOG 归档 ────────────────────────────────────────────────
# CHANGELOG 里自己写着"以 release-notes/vX.Y.Z.md 为唯一数据源，扇出到本文件"，
# 但此前没有任何东西真的做这件事，得靠人手抄。这里把它做实。
CHANGELOG_BLOCK="$(extract_changelog)"
if [ -n "$CHANGELOG_BLOCK" ] && [ -f "$CHANGELOG" ]; then
    if grep -qF "## [${VERSION}]" "$CHANGELOG"; then
        note "CHANGELOG 已有 [${VERSION}] 小节，跳过"
    else
        TODAY="$(date +%F)"
        TMP="$(mktemp)"
        # 插在第一个已存在的 "## [" 之前，也就是文件头说明之后、最新版本之上。
        awk -v ver="$VERSION" -v day="$TODAY" -v block="$CHANGELOG_BLOCK" '
            !done && /^## \[/ {
                printf "## [%s] - %s\n\n%s\n\n", ver, day, block
                done = 1
            }
            { print }
            END { if (!done) printf "\n## [%s] - %s\n\n%s\n", ver, day, block }
        ' "$CHANGELOG" > "$TMP"
        mv "$TMP" "$CHANGELOG"
        note "已在 ${CHANGELOG} 插入 [${VERSION}] - ${TODAY}"
    fi
else
    note "跳过 CHANGELOG（'## changelog' 区块为空）"
fi

# ── 7. 提交并打 tag ──────────────────────────────────────────────────
# 公告文件是第 4b 步校验的那个，必须一起进提交：CI 在 tag 指向的 tree 里找它。
git add "$VERSION_FILE" "$NOTES_FILE" "$ANNOUNCEMENT_FILE" "$CHANGELOG" 2>/dev/null \
    || git add "$VERSION_FILE" "$NOTES_FILE" "$ANNOUNCEMENT_FILE"
git commit -q -m "release: ${VERSION}" -m "$(printf '%s\n' "$NOTES")"
git tag -a "$TAG" -m "Release ${TAG}"
note "已提交并打好 tag ${TAG}"

if [ "$PUSH" = "--push" ]; then
    git push origin "$BRANCH"
    git push origin "refs/tags/${TAG}"
    echo "== 已推送。tag 推送即触发发版流水线 =="
else
    echo
    echo "本地已就绪，还没有推送。检查无误后："
    echo "  git push origin ${BRANCH} && git push origin refs/tags/${TAG}"
    echo
    echo "想撤销："
    echo "  git tag -d ${TAG} && git reset --hard HEAD~1"
fi
