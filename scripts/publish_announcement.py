#!/usr/bin/env python3
"""Publish an archived release announcement after verifying Gitee delivery."""
import argparse
import base64
import json
import os
from pathlib import Path
import re
import time
from urllib.error import HTTPError, URLError
from urllib.parse import urlencode
from urllib.request import Request, urlopen

API_ROOT = "https://gitee.com/api/v5/repos/sonder123i/capybara-course-helper"
DOWNLOAD_ROOT = "https://gitee.com/sonder123i/capybara-course-helper/releases/download"
REPO_ROOT = Path(__file__).resolve().parents[1]


def version_code(tag):
    """把 vX.Y.Z 换算成 versionCode：major*10000 + minor*100 + patch。

    必须与 scripts/release.sh 和 release.yml 的 Resolve Version From Tag 保持一致。
    不要退回「取 patch 位」的老写法：v1.1.7 的真实 code 是 10107 而非 7，
    按 patch 位推算会把一次正常发布判成「与版本不符」，卡住公告发布。
    """
    major, minor, patch = (int(part) for part in tag[1:].split("."))
    return major * 10000 + minor * 100 + patch


def validate_announcement(announcement, tag):
    if re.fullmatch(r"v\d+\.\d+\.\d+", tag) is None:
        raise ValueError("Release tag must have the form vX.Y.Z")
    if not isinstance(announcement, dict):
        raise ValueError("Announcement must be an object")
    for field in ("id", "title", "content", "created_at"):
        if not isinstance(announcement.get(field), str) or not announcement[field].strip():
            raise ValueError(f"Announcement requires a nonempty {field}")
    if not announcement["id"].endswith("_" + tag.replace(".", "_") + "_release"):
        raise ValueError("Announcement ID does not match the release tag")
    if announcement.get("audience") != "app" or announcement.get("contentType") != "markdown":
        raise ValueError("Release announcement must target the app and use Markdown")
    if announcement.get("showOnce") is not True:
        raise ValueError("Release announcement must use showOnce=true")
    return announcement


def merge_announcements(existing, incoming):
    """Keep all unrelated announcements and root metadata, including legacy formats."""
    if existing is None:
        result, entries = {}, []
    elif isinstance(existing, list):
        result, entries = {}, existing
    elif isinstance(existing, dict) and isinstance(existing.get("announcements"), list):
        result, entries = dict(existing), existing["announcements"]
    elif isinstance(existing, dict) and all(key in existing for key in ("id", "title", "content")):
        result, entries = {}, [existing]
    else:
        raise ValueError("Unknown announcement format; refusing to replace it")
    if any(not isinstance(entry, dict) or not entry.get("id") for entry in entries):
        raise ValueError("Malformed historical announcement; refusing to replace it")
    result["announcements"] = [incoming] + [entry for entry in entries if entry["id"] != incoming["id"]]
    return result


class Gitee:
    def __init__(self, token):
        if not token:
            raise ValueError("GITEE_TOKEN is required")
        self.token = token

    def request(self, method, path, payload=None, allow_missing=False):
        data = None
        if method == "GET":
            separator = "&" if "?" in path else "?"
            path += separator + urlencode({"access_token": self.token})
        else:
            data = json.dumps(dict(payload or {}, access_token=self.token)).encode("utf-8")
        request = Request(
            API_ROOT + "/" + path, data=data, method=method,
            headers={"Accept": "application/json", "Content-Type": "application/json",
                     "User-Agent": "academic-assistant-release", "Cache-Control": "no-cache"},
        )
        try:
            with urlopen(request, timeout=30) as response:
                return json.load(response)
        except HTTPError as error:
            if allow_missing and error.code == 404:
                return None
            # Never print the authenticated URL, response body or token.
            raise RuntimeError(f"Gitee returned HTTP {error.code}") from None
        except URLError:
            raise RuntimeError("Gitee request failed") from None

    def read_json_file(self, name, allow_missing=False):
        metadata = self.request("GET", f"contents/{name}?ref=main", allow_missing=allow_missing)
        # Gitee 与 GitHub 不同：文件不存在时它返回 HTTP 200 + 空数组 []，而不是 404。
        # 只认 None 会把「首次创建该文件」这一步误判成响应格式错误。
        if metadata is None or metadata == []:
            if allow_missing:
                return None, None
            raise RuntimeError(f"Gitee file {name} does not exist")
        if not isinstance(metadata, dict) or metadata.get("encoding") != "base64":
            raise RuntimeError(f"Invalid Gitee file response for {name}")
        content = base64.b64decode(metadata["content"]).decode("utf-8-sig")
        return metadata, json.loads(content)


def verify_delivery(client, tag, notes):
    _, version = client.read_json_file("version.json")
    expected_url = f"{DOWNLOAD_ROOT}/{tag}/app-release.apk"
    if not isinstance(version, dict) or (
        version.get("versionName") != tag[1:]
        or version.get("versionCode") != version_code(tag)
        or version.get("downloadUrl") != expected_url
        or str(version.get("releaseNotes", "")).strip() != notes.strip()
    ):
        raise RuntimeError("Gitee version.json does not match this release")
    release = client.request("GET", f"releases/tags/{tag}")
    assets = release.get("assets", []) if isinstance(release, dict) else []
    if not any(asset.get("name") == "app-release.apk"
               and asset.get("browser_download_url") == expected_url for asset in assets):
        raise RuntimeError("Gitee release APK is missing; announcement was not published")


def publish_release(client, tag, announcement, notes):
    validate_announcement(announcement, tag)
    if not notes.strip():
        raise ValueError("Release notes must not be empty")
    verify_delivery(client, tag, notes)
    metadata, existing = client.read_json_file("announcement.json", allow_missing=True)
    merged = merge_announcements(existing, announcement)
    if merged == existing:
        print(f"Announcement {announcement['id']} is already published")
        return False
    content = json.dumps(merged, ensure_ascii=False, indent=2) + "\n"
    payload = {"message": f"announce: {tag}", "branch": "main",
               "content": base64.b64encode(content.encode("utf-8")).decode("ascii")}
    if metadata is not None:
        payload["sha"] = metadata["sha"]
    client.request("PUT" if metadata is not None else "POST", "contents/announcement.json", payload)
    for attempt in range(3):
        _, published = client.read_json_file("announcement.json")
        if published == merged:
            print(f"Published {announcement['id']}; preserved {len(merged['announcements']) - 1} historical announcements")
            return True
        if attempt < 2:
            time.sleep(1)
    raise RuntimeError("Announcement write could not be verified")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--tag", required=True)
    parser.add_argument("--validate-only", action="store_true")
    args = parser.parse_args()
    if re.fullmatch(r"v\d+\.\d+\.\d+", args.tag) is None:
        parser.error("tag must have the form vX.Y.Z")
    archive = REPO_ROOT / "release-notes" / f"{args.tag}-announcement.json"
    announcement = validate_announcement(json.loads(archive.read_text(encoding="utf-8")), args.tag)
    if args.validate_only:
        print(f"Validated {archive.name}")
        return
    publish_release(Gitee(os.environ.get("GITEE_TOKEN", "")), args.tag, announcement,
                    os.environ.get("RELEASE_NOTES", ""))


if __name__ == "__main__":
    try:
        main()
    except (OSError, ValueError, RuntimeError) as error:
        raise SystemExit(str(error)) from None
