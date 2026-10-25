import base64
import copy
import io
import json
from pathlib import Path
import sys
import unittest
from unittest.mock import patch
from urllib.error import HTTPError, URLError

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from publish_announcement import (Gitee, merge_announcements, publish_release,
                                  validate_announcement, version_code)

TAG = "v1.0.74"
NOTES = "第一条更新\n第二条更新"
ANNOUNCEMENT = {
    "id": "20260912_v1_0_74_release", "title": "更新说明", "content": "## 新功能\n\n课程提醒",
    "audience": "app", "contentType": "markdown", "showOnce": True,
    "created_at": "2026-09-12T12:00:00Z",
}
OLD = {"id": "old", "title": "旧公告", "content": "原文", "audience": "web"}
URL = "https://gitee.com/sonder123i/capybara-course-helper/releases/download/v1.0.74/app-release.apk"


class FakeGitee:
    def __init__(self, announcements=None):
        self.announcements = copy.deepcopy(announcements)
        self.version = {"versionName": "1.0.74", "versionCode": 10074, "releaseNotes": NOTES, "downloadUrl": URL}
        self.release = {"assets": [{"name": "app-release.apk", "browser_download_url": URL}]}
        self.writes = []
        self.apply_write = True

    def read_json_file(self, name, allow_missing=False):
        if name == "version.json":
            return {"sha": "version-sha"}, copy.deepcopy(self.version)
        metadata = None if self.announcements is None else {"sha": "announcement-sha"}
        return metadata, copy.deepcopy(self.announcements)

    def request(self, method, path, payload=None, **kwargs):
        if path == "releases/tags/" + TAG:
            return self.release
        self.writes.append((method, path, copy.deepcopy(payload)))
        if self.apply_write:
            self.announcements = json.loads(base64.b64decode(payload["content"]))
        return {"content": {"sha": "published-sha"}}


class AnnouncementTests(unittest.TestCase):
    def test_preserves_root_metadata_and_unrelated_announcements(self):
        current = {"announcements": [OLD], "schema": 2}
        result = merge_announcements(current, ANNOUNCEMENT)
        self.assertEqual(result, {"announcements": [ANNOUNCEMENT, OLD], "schema": 2})
        self.assertEqual(current, {"announcements": [OLD], "schema": 2})

    def test_preserves_legacy_single_object(self):
        self.assertEqual(merge_announcements(OLD, ANNOUNCEMENT)["announcements"], [ANNOUNCEMENT, OLD])

    def test_preserves_legacy_array(self):
        self.assertEqual(merge_announcements([OLD], ANNOUNCEMENT)["announcements"], [ANNOUNCEMENT, OLD])

    def test_replaces_same_id_once_without_dropping_other_entries(self):
        old_release = dict(ANNOUNCEMENT, content="旧文案")
        result = merge_announcements({"announcements": [OLD, old_release, old_release]}, ANNOUNCEMENT)
        self.assertEqual(result["announcements"], [ANNOUNCEMENT, OLD])
        self.assertEqual(merge_announcements(result, ANNOUNCEMENT), result)

    def test_unknown_or_malformed_history_is_rejected(self):
        for current in ({}, {"announcements": {}}, [None], [{"title": "missing id"}]):
            with self.subTest(current=current), self.assertRaises(ValueError):
                merge_announcements(current, ANNOUNCEMENT)

    def test_version_code_uses_semantic_encoding(self):
        """v1.1.7 的真实 versionCode 是 10107，不是 tag 末段的 7。

        旧实现按 tag 末段推算，把一次正常发布判成「与版本不符」，
        公告因此发不出去；这里钉住与 release.sh 一致的编码。
        """
        self.assertEqual(version_code("v1.0.74"), 10074)
        self.assertEqual(version_code("v1.1.7"), 10107)
        self.assertEqual(version_code("v1.2.0"), 10200)
        self.assertEqual(version_code("v2.0.0"), 20000)

    def test_archive_must_match_tag_and_schema(self):
        self.assertEqual(validate_announcement(ANNOUNCEMENT, TAG), ANNOUNCEMENT)
        for changes in ({"id": "wrong"}, {"content": ""}, {"audience": "web"}, {"showOnce": False}):
            with self.subTest(changes=changes), self.assertRaises(ValueError):
                validate_announcement(dict(ANNOUNCEMENT, **changes), TAG)
        with self.assertRaises(ValueError):
            validate_announcement(ANNOUNCEMENT, "../../elsewhere")

    def test_first_publication_creates_file(self):
        client = FakeGitee()
        self.assertTrue(publish_release(client, TAG, ANNOUNCEMENT, NOTES))
        self.assertEqual(client.writes[0][0], "POST")
        self.assertNotIn("sha", client.writes[0][2])

    def test_update_uses_current_sha_and_keeps_history(self):
        client = FakeGitee({"announcements": [OLD], "schema": 2})
        self.assertTrue(publish_release(client, TAG, ANNOUNCEMENT, NOTES))
        method, path, payload = client.writes[0]
        self.assertEqual((method, path, payload["sha"]), ("PUT", "contents/announcement.json", "announcement-sha"))
        self.assertEqual(client.announcements, {"announcements": [ANNOUNCEMENT, OLD], "schema": 2})

    def test_repeat_does_not_write(self):
        client = FakeGitee({"announcements": [ANNOUNCEMENT, OLD]})
        self.assertFalse(publish_release(client, TAG, ANNOUNCEMENT, NOTES))
        self.assertEqual(client.writes, [])

    def test_wrong_version_or_notes_never_publishes(self):
        for changes in ({"versionCode": 10073}, {"versionName": "1.0.73"},
                        {"releaseNotes": "旧日志"}, {"downloadUrl": "https://example.invalid/wrong.apk"}):
            with self.subTest(changes=changes):
                client = FakeGitee({"announcements": [OLD]})
                client.version.update(changes)
                with self.assertRaises(RuntimeError):
                    publish_release(client, TAG, ANNOUNCEMENT, NOTES)
                self.assertEqual(client.writes, [])

    def test_missing_apk_never_publishes(self):
        client = FakeGitee()
        client.release["assets"] = []
        with self.assertRaises(RuntimeError):
            publish_release(client, TAG, ANNOUNCEMENT, NOTES)
        self.assertEqual(client.writes, [])

    def test_empty_notes_never_publishes(self):
        client = FakeGitee()
        with self.assertRaises(ValueError):
            publish_release(client, TAG, ANNOUNCEMENT, "")
        self.assertEqual(client.writes, [])

    @patch("publish_announcement.time.sleep")
    def test_unverified_write_fails(self, _sleep):
        client = FakeGitee({"announcements": [OLD]})
        client.apply_write = False
        with self.assertRaisesRegex(RuntimeError, "could not be verified"):
            publish_release(client, TAG, ANNOUNCEMENT, NOTES)

    @patch("publish_announcement.urlopen")
    def test_http_error_never_exposes_token(self, open_url):
        token = "private-test-token"
        open_url.side_effect = HTTPError("https://example.invalid/?access_token=" + token, 403, token, {}, None)
        with self.assertRaises(RuntimeError) as caught:
            Gitee(token).request("GET", "contents/announcement.json")
        self.assertEqual(str(caught.exception), "Gitee returned HTTP 403")

    @patch("publish_announcement.urlopen")
    def test_transport_error_never_exposes_token(self, open_url):
        open_url.side_effect = URLError("private-test-token")
        with self.assertRaisesRegex(RuntimeError, "^Gitee request failed$"):
            Gitee("private-test-token").request("GET", "contents/announcement.json")

    @patch("publish_announcement.urlopen")
    def test_missing_file_returned_as_empty_array_is_treated_as_absent(self, open_url):
        """Gitee 对不存在的文件返回 200 + []，而不是 404。

        首次创建 announcement.json 时会走到这条路；若只认 404，
        会把「文件还不存在」误判成响应格式错误，导致发布公告失败。
        """
        open_url.return_value.__enter__.return_value = io.StringIO("[]")
        self.assertEqual(Gitee("token").read_json_file("announcement.json", allow_missing=True), (None, None))

    @patch("publish_announcement.urlopen")
    def test_missing_file_without_allow_missing_is_an_error(self, open_url):
        open_url.return_value.__enter__.return_value = io.StringIO("[]")
        with self.assertRaisesRegex(RuntimeError, "does not exist"):
            Gitee("token").read_json_file("announcement.json")

    @patch("publish_announcement.urlopen")
    def test_unknown_response_shape_is_still_rejected(self, open_url):
        # 真正的格式异常不能被当成「文件不存在」悄悄放过。
        open_url.return_value.__enter__.return_value = io.StringIO('{"message": "unexpected"}')
        with self.assertRaisesRegex(RuntimeError, "Invalid Gitee file response"):
            Gitee("token").read_json_file("announcement.json", allow_missing=True)


if __name__ == "__main__":
    unittest.main()
