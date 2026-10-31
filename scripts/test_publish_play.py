"""Offline tests of the publisher's contract with the Google Play API."""
import hashlib
import tempfile
import unittest
import zipfile
from pathlib import Path

from publish_play import publish_internal


class Response:
    def __init__(self, body, status=200):
        self.body, self.status_code = body, status

    def json(self):
        return self.body


class PlayServer:
    """In-memory external API boundary; tracks staged and committed changes."""
    def __init__(self, bundles=None, tracks=None, fail=None, wrong_upload=False):
        self.bundles = bundles or []
        self.tracks = tracks or [{"track": "production", "releases": [{"versionCodes": ["4"]}]}]
        self.fail = fail
        self.uploads = 0
        self.mapping = None
        self.staged = None
        self.commits = 0
        self.deleted = False
        self.wrong_upload = wrong_upload

    def request(self, method, url, **kwargs):
        if not url.startswith((
                "https://androidpublisher.googleapis.com/androidpublisher/v3/applications/com.cbulock.sinkpoint/edits",
                "https://androidpublisher.googleapis.com/upload/androidpublisher/v3/applications/com.cbulock.sinkpoint/edits")):
            raise AssertionError(f"Unexpected API destination: {url}")
        endpoint = url.split("/edits", 1)[1]
        if self.fail and self.fail in endpoint:
            return Response({"error": {"message": "Permission denied"}}, 403)
        if method == "DELETE":
            self.deleted = True
            return Response({})
        if endpoint == "" and method == "POST":
            return Response({"id": "test-edit"})
        if endpoint.endswith("/tracks") and method == "GET":
            return Response({"tracks": self.tracks})
        if endpoint.endswith("/bundles") and method == "GET":
            return Response({"bundles": self.bundles})
        if "/bundles?" in endpoint and method == "POST":
            self.uploads += 1
            self.bundles.append({"versionCode": 6 if self.wrong_upload else 5,
                                 "sha256": hashlib.sha256(kwargs["data"].read()).hexdigest()})
            return Response(self.bundles[-1])
        if "/deobfuscationFiles/proguard?" in endpoint and method == "POST":
            self.mapping = kwargs["data"].read()
            return Response({})
        if endpoint.endswith("/tracks/internal") and method == "PUT":
            self.staged = kwargs["json"]
            return Response(self.staged)
        if endpoint.endswith(":commit") and method == "POST":
            self.commits += 1
            self.tracks = [t for t in self.tracks if t["track"] != "internal"] + [self.staged]
            return Response({"id": "test-edit"})
        raise AssertionError(f"Unexpected API request: {method} {endpoint}")


class PublishTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.bundle = Path(self.temp.name) / "app.aab"
        with zipfile.ZipFile(self.bundle, "w") as archive:
            archive.writestr("BUNDLE-METADATA/com.android.tools.build.obfuscation/proguard.map", b"release mapping")
        self.mapping = Path(self.temp.name) / "mapping.txt"
        self.mapping.write_bytes(b"release mapping")
        self.digest = hashlib.sha256(self.bundle.read_bytes()).hexdigest()

    def publish(self, server):
        return publish_internal(server, self.bundle, self.mapping, 5, "1.2.2", "abc123")

    def test_upload_commits_only_internal_and_includes_mapping(self):
        server = PlayServer()
        self.assertEqual(self.publish(server), "published")
        self.assertEqual(server.commits, 1)
        self.assertIsNone(server.mapping)
        self.assertEqual(server.tracks[0], {"track": "production", "releases": [{"versionCodes": ["4"]}]})
        self.assertEqual(server.tracks[1]["track"], "internal")
        self.assertEqual(server.tracks[1]["releases"][0]["versionCodes"], ["5"])
        self.assertEqual(server.tracks[1]["releases"][0]["status"], "completed")

    def test_retry_after_commit_does_not_upload_or_commit_twice(self):
        server = PlayServer()
        self.publish(server)
        self.assertEqual(self.publish(server), "already-published")
        self.assertEqual((server.uploads, server.commits), (1, 1))

    def test_reuses_identical_bundle_already_uploaded_to_play(self):
        server = PlayServer(bundles=[{"versionCode": 5, "sha256": self.digest}])
        self.assertEqual(self.publish(server), "published")
        self.assertEqual((server.uploads, server.commits), (0, 1))

    def test_rejects_same_version_with_different_contents(self):
        server = PlayServer(bundles=[{"versionCode": 5, "sha256": "different"}])
        with self.assertRaisesRegex(RuntimeError, "different"):
            self.publish(server)
        self.assertEqual((server.uploads, server.commits), (0, 0))
        self.assertTrue(server.deleted)

    def test_rejects_rollback_even_when_newer_version_is_on_another_track(self):
        server = PlayServer(tracks=[{"track": "production", "releases": [{"versionCodes": ["6"]}]}])
        with self.assertRaisesRegex(RuntimeError, "newer"):
            self.publish(server)
        self.assertEqual((server.uploads, server.commits), (0, 0))

    def test_draft_internal_release_is_completed_instead_of_skipped(self):
        server = PlayServer(bundles=[{"versionCode": 5, "sha256": self.digest}], tracks=[
            {"track": "internal", "releases": [{"versionCodes": ["5"], "status": "draft"}]}])
        self.assertEqual(self.publish(server), "published")
        self.assertEqual(server.tracks[0]["releases"][0]["status"], "completed")

    def test_api_failure_does_not_commit(self):
        for endpoint in ("/bundles?", "/tracks/internal", ":commit"):
            with self.subTest(endpoint=endpoint):
                server = PlayServer(fail=endpoint)
                with self.assertRaisesRegex(RuntimeError, "Permission denied"):
                    self.publish(server)
                self.assertEqual(server.commits, 0)
                self.assertTrue(server.deleted)

    def test_missing_mapping_fails_before_creating_edit(self):
        self.mapping.unlink()
        server = PlayServer()
        with self.assertRaises(FileNotFoundError):
            self.publish(server)
        self.assertEqual(server.commits, 0)

    def test_mismatched_uploaded_version_is_never_released(self):
        server = PlayServer(wrong_upload=True)
        with self.assertRaisesRegex(RuntimeError, "does not match"):
            self.publish(server)
        self.assertEqual(server.commits, 0)
        self.assertIsNone(server.staged)

    def test_embedded_mapping_is_not_uploaded_separately(self):
        server = PlayServer(fail="/deobfuscationFiles/")
        self.assertEqual(self.publish(server), "published")

    def test_wrong_archived_mapping_fails_before_creating_edit(self):
        self.mapping.write_bytes(b"mapping from a different build")
        server = PlayServer()
        with self.assertRaisesRegex(RuntimeError, "mapping"):
            self.publish(server)
        self.assertEqual(server.commits, 0)


if __name__ == "__main__":
    unittest.main()
