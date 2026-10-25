#!/usr/bin/env python3
"""把仓库根 announcement.json（1.x 简单格式）转换为 App 公告 feed 格式。

输出 {schemaVersion: 1, announcements: [...]}，字段规则与
AnnouncementManager.validateDocument 一一对应。1.x 的五字段
（id/title/content/type/contentType/showOnce/created_at）直接映射，
并支持在新格式文件中手工追加 category/revision/display/actions 等扩展字段。

用法：python scripts/make_announcement_feed.py [--in announcement.json] [--out docs/announcement.json]
"""
import argparse
import json
from pathlib import Path

REPO = Path(__file__).resolve().parents[1]
VALID_TYPES = {"info", "warning", "important"}
VALID_CONTENT = {"text", "markdown"}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--in", dest="source", default="announcement.json")
    parser.add_argument("--out", default="docs/announcement.json")
    args = parser.parse_args()

    entries = json.loads((REPO / args.source).read_text(encoding="utf-8"))["announcements"]
    items = []
    for entry in reversed(entries):  # 新的在前
        kind = entry.get("type", "info")
        if kind not in VALID_TYPES:
            raise SystemExit(f"非法 type: {kind}")
        item = {
            "id": entry["id"][:128],
            "title": entry["title"][:100],
            "content": entry["content"][:16000],
            "type": kind,
            "contentType": entry.get("contentType", "markdown"),
            "showOnce": bool(entry.get("showOnce", True)),
            "category": "release" if "_release" in entry["id"] else "general",
            "summary": entry["title"][:240],
        }
        if entry.get("created_at"):
            item["startsAt"] = entry["created_at"]
        if "_release" in entry["id"]:
            # 历史版本公告仅面向旧版本用户；新版本用户不再弹历史更新说明。
            item["maxVersionCode"] = 10202
        items.append(item)

    if len(items) > 50:
        items = items[:50]
    out = REPO / args.out
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(json.dumps({"schemaVersion": 1, "announcements": items},
                              ensure_ascii=False, indent=2) + "\n", encoding="utf-8", newline="\n")
    print(f"feed 已写入 {out}，共 {len(items)} 条")


if __name__ == "__main__":
    main()
