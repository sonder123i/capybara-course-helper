#!/usr/bin/env python3
"""生成自建更新清单（schemaVersion=2，ECDSA P-256 签名）。

用法（发版流水线内调用，见 .github/workflows/release.yml）：
  python scripts/make_update_manifest.py \
    --apk app/build/outputs/apk/release/app-universal-release.apk \
    --mirror gh="GitHub Release"="https://github.com/sonder123i/capybara-course-helper/releases/download/vX.Y.Z/app-release.apk" \
    --mirror gitee="Gitee 附件"="https://gitee.com/sonder123i/capybara-course-helper/releases/download/vX.Y.Z/app-release.apk" \
    --notes-file release-notes/vX.Y.Z.md \
    --key-file "$UPDATE_SIGNING_KEY_FILE" \
    --out docs/update/stable.json

字段规则与 app/src/main/java/com/tyust/course/update/UpdateManifest.kt 的
UpdateManifestVerifier.verify() 一一对应；密钥对见
C:/Users/k2767/projects/capybara-update-signing（仓库外，公钥钉在
app/src/main/assets/app-update-keys.json，keyId=capybara-update-2026-10）。
"""
import argparse
import base64
import hashlib
import json
import os
import re
import subprocess
import tempfile
import time
from datetime import datetime, timezone
from pathlib import Path

REPO = Path(__file__).resolve().parents[1]
KEY_ID = "capybara-update-2026-10"


def run(cmd, data=None):
    result = subprocess.run(cmd, input=data, capture_output=True)
    if result.returncode != 0:
        raise SystemExit(f"命令失败: {' '.join(cmd)}\n{result.stderr.decode(errors='replace')}")
    return result.stdout


def version_props():
    props = {}
    for line in (REPO / "app" / "version.properties").read_text(encoding="utf-8").splitlines():
        if "=" in line and not line.startswith("#"):
            key, value = line.split("=", 1)
            props[key.strip()] = value.strip()
    return int(props["VERSION_CODE"]), props["VERSION_NAME"]


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--apk", required=True)
    parser.add_argument("--channel", default="stable", choices=["stable", "test"])
    parser.add_argument("--mirror", action="append", default=[],
                        help='id="显示名"="下载 URL"，可重复；至少一个')
    parser.add_argument("--artifact", action="append", default=[],
                        help='abi=APK 路径，可重复。用于按 ABI 分发：2.x 的通用包 156MB，'
                             '分包 91MB，弱网差别很大')
    parser.add_argument("--artifact-mirror", action="append", default=[],
                        help='abi=id="显示名"="下载 URL"，可重复；每个 artifact 至少一个')
    parser.add_argument("--notes", default="")
    parser.add_argument("--notes-file", default="")
    parser.add_argument("--key-file", default=os.environ.get("UPDATE_SIGNING_KEY_FILE", ""))
    parser.add_argument("--key-id", default=KEY_ID)
    parser.add_argument("--force-update", action="store_true")
    parser.add_argument("--out", default="docs/update/stable.json")
    args = parser.parse_args()

    if not args.key_file:
        raise SystemExit("缺少签名私钥：--key-file 或环境变量 UPDATE_SIGNING_KEY_FILE")
    apk = Path(args.apk)
    if not apk.exists():
        raise SystemExit(f"APK 不存在: {apk}")

    notes = args.notes or (Path(args.notes_file).read_text(encoding="utf-8") if args.notes_file else "")
    version_code, version_name = version_props()
    data = apk.read_bytes()
    mirrors = []
    for spec in args.mirror:
        m = re.match(r'([a-z0-9-]{1,32})=("?)([^"]{1,40})\2=(.+)$', spec)
        if not m:
            raise SystemExit(f'--mirror 格式应为 id="名称"="URL"（引号可省），收到: {spec}')
        mirrors.append({"id": m.group(1), "name": m.group(3), "url": m.group(4)})
    if not (1 <= len(mirrors) <= 8):
        raise SystemExit("镜像数量须在 1..8")

    # 按 ABI 分发的分包（可选）。顶层 size/sha256 仍是通用包，只有挑不出 ABI 的
    # 设备才会用它；能挑出来的走 artifacts，省一半流量。
    artifact_paths = {}
    for spec in args.artifact:
        abi, _, path = spec.partition("=")
        if abi not in ("arm64-v8a", "armeabi-v7a"):
            raise SystemExit(f"--artifact 的 ABI 只能是 arm64-v8a / armeabi-v7a，收到: {abi}")
        if not Path(path).exists():
            raise SystemExit(f"--artifact 的 APK 不存在: {path}")
        artifact_paths[abi] = path

    artifact_mirrors = {}
    for spec in args.artifact_mirror:
        abi, _, rest = spec.partition("=")
        m = re.match(r'([a-z0-9-]{1,32})=("?)([^"]{1,40})\2=(.+)$', rest)
        if not m or abi not in artifact_paths:
            raise SystemExit(f'--artifact-mirror 格式应为 abi=id="名称"="URL"，收到: {spec}')
        artifact_mirrors.setdefault(abi, []).append(
            {"id": m.group(1), "name": m.group(3), "url": m.group(4)})

    artifacts = {}
    for abi, path in artifact_paths.items():
        entries = artifact_mirrors.get(abi, [])
        if not (1 <= len(entries) <= 8):
            raise SystemExit(f"{abi} 的镜像数量须在 1..8，当前 {len(entries)}")
        blob = Path(path).read_bytes()
        artifacts[abi] = {
            "size": len(blob),
            "sha256": hashlib.sha256(blob).hexdigest(),
            "mirrors": entries,
        }
        print(f"  artifact {abi}: {len(blob)} 字节, {len(entries)} 条线路")

    source_sha = run(["git", "rev-parse", "HEAD"], ).decode().strip()
    payload = {
        "channel": args.channel,
        "revision": int(time.time()),
        "packageName": "com.k2767.course",
        "versionCode": version_code,
        "versionName": version_name,
        "minSdk": 24,
        "releaseNotes": notes[:20000],
        "forceUpdate": bool(args.force_update),
        "size": len(data),
        "sha256": hashlib.sha256(data).hexdigest(),
        "sourceSha": source_sha,
        "buildId": str(int(time.time() * 1000)),
        "publishedAt": datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
        "mirrors": mirrors,
    }
    if artifacts:
        payload["artifacts"] = artifacts
    payload_bytes = json.dumps(payload, ensure_ascii=False, separators=(",", ":")).encode("utf-8")
    signature = run(["openssl", "dgst", "-sha256", "-sign", args.key_file], payload_bytes)
    envelope = {
        "schemaVersion": 2,
        "keyId": args.key_id,
        "payload": base64.b64encode(payload_bytes).decode(),
        "signature": base64.b64encode(signature).decode(),
    }

    # 自验：用私钥导出的公钥校验签名，确保流水线不会发出坏清单。
    with tempfile.NamedTemporaryFile(delete=False, suffix=".pem") as pub:
        pub.write(run(["openssl", "ec", "-in", args.key_file, "-pubout"]))
        pub_path = pub.name
    with tempfile.NamedTemporaryFile(delete=False, suffix=".sig") as sig:
        sig.write(signature)
        sig_path = sig.name
    run(["openssl", "dgst", "-sha256", "-verify", pub_path, "-signature", sig_path], payload_bytes)
    os.unlink(pub_path); os.unlink(sig_path)

    out = REPO / args.out
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(json.dumps(envelope, ensure_ascii=False, indent=2) + "\n", encoding="utf-8", newline="\n")
    print(f"清单已写入 {out}：v{version_name} ({version_code})，{len(data)} 字节，镜像 {len(mirrors)} 条，签名自验通过")


if __name__ == "__main__":
    main()
