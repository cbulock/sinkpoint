"""Publish a signed Sinkpoint bundle to Google Play internal testing only.

Credentials are read from PLAY_SERVICE_ACCOUNT_JSON, never command-line arguments.
The external HTTP session is injectable so release/retry behavior is tested offline.
"""
import argparse
import hashlib
import json
import os
import zipfile
from pathlib import Path

PACKAGE = "com.cbulock.sinkpoint"
BASE = f"https://androidpublisher.googleapis.com/androidpublisher/v3/applications/{PACKAGE}/edits"
UPLOAD = BASE.replace(".com/androidpublisher/", ".com/upload/androidpublisher/")


def request(session, method, url, **kwargs):
    response = session.request(method, url, timeout=300, **kwargs)
    if not 200 <= response.status_code < 300:
        try:
            message = response.json().get("error", {}).get("message", "Unknown error")
        except (ValueError, AttributeError):
            message = "Non-JSON response"
        raise RuntimeError(f"Google Play API returned {response.status_code}: {message}")
    return response.json() if response.status_code != 204 else {}


def publish_internal(session, bundle, mapping, version_code, version_name, source_sha):
    if version_code < 1:
        raise ValueError("versionCode must be positive")
    digest = hashlib.sha256(bundle.read_bytes()).hexdigest()
    # AGP embeds this mapping in the AAB and Play extracts it automatically.
    # A second mapping upload can be rejected. Check our private backup instead.
    mapping_digest = hashlib.sha256(mapping.read_bytes()).digest()
    with zipfile.ZipFile(bundle) as archive:
        embedded = archive.read("BUNDLE-METADATA/com.android.tools.build.obfuscation/proguard.map")
    if hashlib.sha256(embedded).digest() != mapping_digest:
        raise RuntimeError("Archived mapping does not match the bundle's embedded mapping")
    edit = request(session, "POST", BASE, json={})["id"]
    edit_url = f"{BASE}/{edit}"
    committed = False
    try:
        tracks = request(session, "GET", f"{edit_url}/tracks").get("tracks", [])
        bundles = request(session, "GET", f"{edit_url}/bundles").get("bundles", [])
        codes = [int(code) for track in tracks for release in track.get("releases", [])
                 for code in release.get("versionCodes", [])]
        codes += [int(b["versionCode"]) for b in bundles]
        if codes and max(codes) > version_code:
            raise RuntimeError(f"Play already contains a newer version ({max(codes)} > {version_code}); refusing rollback")
        existing = next((b for b in bundles if int(b["versionCode"]) == version_code), None)
        if existing and existing.get("sha256", "").lower() != digest:
            raise RuntimeError("Play already contains different bundle contents for this versionCode")
        already_published = any(
            track["track"] == "internal" and any(
                release.get("status") == "completed" and str(version_code) in release.get("versionCodes", [])
                for release in track.get("releases", [])) for track in tracks)
        if already_published:
            if not existing:
                raise RuntimeError("Cannot verify the existing internal bundle checksum")
            return "already-published"
        if not existing:
            with bundle.open("rb") as data:
                uploaded = request(session, "POST", f"{UPLOAD}/{edit}/bundles?uploadType=media",
                                   headers={"Content-Type": "application/octet-stream"}, data=data)
            if int(uploaded["versionCode"]) != version_code or uploaded.get("sha256", "").lower() != digest:
                raise RuntimeError("Uploaded bundle version or checksum does not match the selected build")
        request(session, "PUT", f"{edit_url}/tracks/internal", json={
            "track": "internal", "releases": [{
                "name": f"{version_name} ({source_sha[:7]})",
                "versionCodes": [str(version_code)], "status": "completed",
            }],
        })
        # Do not silently retry with changesNotSentForReview: an API rejection is actionable.
        request(session, "POST", f"{edit_url}:commit")
        committed = True
        return "published"
    finally:
        if not committed:
            try:
                request(session, "DELETE", edit_url)
            except Exception:
                # The original failure is more useful; edits also expire automatically.
                pass


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--bundle", type=Path, required=True)
    parser.add_argument("--mapping", type=Path, required=True)
    parser.add_argument("--version-code", type=int, required=True)
    parser.add_argument("--version-name", required=True)
    parser.add_argument("--source-sha", required=True)
    args = parser.parse_args()
    # Imported only by the CLI; offline tests need no credentials or dependencies.
    from google.oauth2 import service_account
    from google.auth.transport.requests import AuthorizedSession

    credentials = service_account.Credentials.from_service_account_info(
        json.loads(os.environ["PLAY_SERVICE_ACCOUNT_JSON"]),
        scopes=["https://www.googleapis.com/auth/androidpublisher"],
    )
    with AuthorizedSession(credentials) as session:
        result = publish_internal(session, args.bundle, args.mapping, args.version_code,
                                  args.version_name, args.source_sha)
    print(f"Google Play internal testing: {result} ({args.version_name}, code {args.version_code})")


if __name__ == "__main__":
    main()
