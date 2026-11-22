#!/usr/bin/env python3
"""
Automated publishing script for Google Play Console using google-api-python-client.
Publishes an Android App Bundle (.aab) to the internal testing track (or any other track).
"""

import argparse
import json
import os
import re
import sys
from pathlib import Path
from google.oauth2 import service_account
from googleapiclient.discovery import build
from googleapiclient.errors import HttpError
from googleapiclient.http import MediaFileUpload

PLAY_CONSOLE_SCOPES = ["https://www.googleapis.com/auth/androidpublisher"]

def find_project_root() -> Path:
    current = Path(__file__).resolve().parent
    while current != current.parent:
        if (current / "settings.gradle.kts").exists() or (current / "build.gradle.kts").exists():
            return current
        current = current.parent
    return Path.cwd()

def extract_package_name_from_gradle(root_dir: Path) -> str | None:
    gradle_file = root_dir / "app" / "build.gradle.kts"
    if not gradle_file.exists():
        return None
    content = gradle_file.read_text(encoding="utf-8")
    # match applicationId = "..." or namespace = "..."
    match = re.search(r'applicationId\s*=\s*["\']([^"\']+)["\']', content)
    if match:
        return match.group(1)
    match = re.search(r'namespace\s*=\s*["\']([^"\']+)["\']', content)
    if match:
        return match.group(1)
    return None

def find_default_credentials(root_dir: Path) -> Path | None:
    candidates = [
        os.environ.get("PLAY_SERVICE_ACCOUNT_KEY"),
        os.environ.get("GOOGLE_APPLICATION_CREDENTIALS"),
        root_dir / "scripts" / "play-store" / "service-account.json",
        root_dir / "service-account.json",
        root_dir / "play-service-account.json",
        root_dir / "play-credentials.json",
    ]
    for c in candidates:
        if c:
            p = Path(c)
            if p.is_file():
                return p
    return None

def find_default_aab(root_dir: Path) -> Path | None:
    candidates = [
        os.environ.get("AAB_PATH"),
        root_dir / "app" / "build" / "outputs" / "bundle" / "release" / "app-release.aab",
        root_dir / "app" / "release" / "app-release.aab",
    ]
    for c in candidates:
        if c:
            p = Path(c)
            if p.is_file():
                return p
    return None

def parse_args():
    parser = argparse.ArgumentParser(
        description="Publish Android App Bundle to Google Play internal testing track."
    )
    parser.add_argument(
        "--credentials",
        "-c",
        type=str,
        help="Path to Google Cloud Service Account JSON key.",
    )
    parser.add_argument(
        "--aab",
        "-a",
        type=str,
        help="Path to the .aab (Android App Bundle) file to upload.",
    )
    parser.add_argument(
        "--package-name",
        "-p",
        type=str,
        help="Android Application Package Name (e.g. com.pointlessapps.filman).",
    )
    parser.add_argument(
        "--track",
        "-t",
        type=str,
        default="internal",
        choices=["internal", "alpha", "beta", "production"],
        help="Target release track (default: internal).",
    )
    parser.add_argument(
        "--status",
        "-s",
        type=str,
        default="completed",
        choices=["completed", "draft", "inProgress", "halted"],
        help="Release status (default: completed).",
    )
    parser.add_argument(
        "--release-name",
        type=str,
        default=None,
        help="Optional user-facing release name for Google Play Console.",
    )
    parser.add_argument(
        "--notes",
        type=str,
        default=None,
        help="Release notes text (default language: en-US).",
    )
    parser.add_argument(
        "--notes-file",
        type=str,
        default=None,
        help="Path to a text file containing release notes.",
    )
    parser.add_argument(
        "--notes-lang",
        type=str,
        default="en-US",
        help="Language code for release notes (default: en-US).",
    )
    parser.add_argument(
        "--user-fraction",
        type=float,
        default=None,
        help="Rollout fraction between 0.0 and 1.0 (only applicable when status is inProgress).",
    )
    parser.add_argument(
        "--validate-only",
        action="store_true",
        help="Validate the edit with Google Play without committing the release.",
    )
    return parser.parse_args()

def main():
    root_dir = find_project_root()
    args = parse_args()

    # 1. Resolve Package Name
    package_name = args.package_name or os.environ.get("PACKAGE_NAME") or extract_package_name_from_gradle(root_dir)
    if not package_name:
        print("❌ Error: Package name could not be determined. Please pass --package-name.", file=sys.stderr)
        sys.exit(1)

    # 2. Resolve Credentials File
    credentials_path = args.credentials
    if not credentials_path:
        default_cred = find_default_credentials(root_dir)
        if default_cred:
            credentials_path = str(default_cred)
        else:
            print(
                "❌ Error: Service account JSON key not found.\n"
                "Please provide --credentials <path/to/key.json> or set PLAY_SERVICE_ACCOUNT_KEY env var.\n"
                "You can also place it at scripts/play-store/service-account.json",
                file=sys.stderr,
            )
            sys.exit(1)

    credentials_file = Path(credentials_path).resolve()
    if not credentials_file.is_file():
        print(f"❌ Error: Credentials file does not exist: {credentials_file}", file=sys.stderr)
        sys.exit(1)

    # 3. Resolve AAB File
    aab_path = args.aab
    if not aab_path:
        default_aab = find_default_aab(root_dir)
        if default_aab:
            aab_path = str(default_aab)
        else:
            print(
                "❌ Error: No .aab bundle file found.\n"
                "Run `./gradlew :app:bundleRelease` or pass --aab <path/to/app-release.aab>.",
                file=sys.stderr,
            )
            sys.exit(1)

    aab_file = Path(aab_path).resolve()
    if not aab_file.is_file():
        print(f"❌ Error: AAB file does not exist: {aab_file}", file=sys.stderr)
        sys.exit(1)

    file_size_mb = aab_file.stat().st_size / (1024 * 1024)

    # 4. Resolve Release Notes
    release_notes_text = None
    if args.notes_file:
        notes_path = Path(args.notes_file).resolve()
        if notes_path.is_file():
            release_notes_text = notes_path.read_text(encoding="utf-8").strip()
    elif args.notes:
        release_notes_text = args.notes.strip()

    print("==================================================")
    print("🚀 Google Play Deployment: Starting Publication")
    print("==================================================")
    print(f"📦 Package:     {package_name}")
    print(f"🎯 Track:       {args.track}")
    print(f"📊 Status:      {args.status}")
    print(f"📁 AAB Path:    {aab_file} ({file_size_mb:.2f} MB)")
    print(f"🔑 Credentials: {credentials_file.name}")
    if release_notes_text:
        print(f"📝 Notes ({args.notes_lang}): {release_notes_text[:60]}{'...' if len(release_notes_text) > 60 else ''}")
    print("==================================================")

    # 5. Authenticate
    try:
        credentials = service_account.Credentials.from_service_account_file(
            str(credentials_file),
            scopes=PLAY_CONSOLE_SCOPES,
        )
        service = build("androidpublisher", "v3", credentials=credentials, cache_discovery=False)
    except Exception as e:
        print(f"❌ Authentication failed: {e}", file=sys.stderr)
        sys.exit(1)

    # 6. Create App Edit
    try:
        print("\n⏳ [1/4] Creating an edit transaction in Google Play...")
        edit_request = service.edits().insert(packageName=package_name, body={})
        edit_result = edit_request.execute()
        edit_id = edit_result["id"]
        print(f"   ✓ Edit session created (ID: {edit_id})")
    except HttpError as e:
        print(f"❌ Failed to create edit: {e.error_details if hasattr(e, 'error_details') else e}", file=sys.stderr)
        sys.exit(1)

    # 7. Upload App Bundle
    try:
        print("\n⏳ [2/4] Uploading Android App Bundle (.aab)...")
        media = MediaFileUpload(
            str(aab_file),
            mimetype="application/octet-stream",
            resumable=True,
            chunksize=1024 * 1024 * 2,  # 2MB chunks
        )
        upload_request = service.edits().bundles().upload(
            packageName=package_name,
            editId=edit_id,
            media_body=media,
        )

        response = None
        while response is None:
            status, response = upload_request.next_chunk()
            if status:
                progress = int(status.progress() * 100)
                print(f"   Uploading... {progress}%", end="\r", flush=True)

        version_code = response["versionCode"]
        print(f"\n   ✓ Bundle uploaded successfully! Version Code: {version_code}")
    except HttpError as e:
        print(f"\n❌ Failed to upload bundle: {e}", file=sys.stderr)
        sys.exit(1)

    # 8. Assign to Track
    try:
        print(f"\n⏳ [3/4] Assigning version {version_code} to track '{args.track}'...")
        release_obj = {
            "versionCodes": [str(version_code)],
            "status": args.status,
        }
        if args.release_name:
            release_obj["name"] = args.release_name
        if args.status == "inProgress" and args.user_fraction is not None:
            release_obj["userFraction"] = args.user_fraction
        if release_notes_text:
            release_obj["releaseNotes"] = [
                {
                    "language": args.notes_lang,
                    "text": release_notes_text,
                }
            ]

        track_body = {
            "track": args.track,
            "releases": [release_obj],
        }

        service.edits().tracks().update(
            packageName=package_name,
            editId=edit_id,
            track=args.track,
            body=track_body,
        ).execute()
        print(f"   ✓ Track '{args.track}' updated with release status '{args.status}'")
    except HttpError as e:
        print(f"❌ Failed to update track: {e}", file=sys.stderr)
        sys.exit(1)

    # 9. Validate or Commit Edit
    try:
        if args.validate_only:
            print("\n⏳ [4/4] Validating edit (dry-run mode)...")
            service.edits().validate(packageName=package_name, editId=edit_id).execute()
            print("   ✓ Validation successful! (Changes were not committed)")
        else:
            print("\n⏳ [4/4] Committing edit to Google Play Console...")
            commit_result = service.edits().commit(packageName=package_name, editId=edit_id).execute()
            print("   ✓ Edit successfully committed!")

        print("\n🎉 Deployment completed successfully!")
        print(f"Track:        https://play.google.com/console/u/0/developers/app/testing/internal-testing?appId={package_name}")
        print(f"Version Code: {version_code}")
    except HttpError as e:
        print(f"❌ Failed to commit edit: {e}", file=sys.stderr)
        sys.exit(1)

if __name__ == "__main__":
    main()
