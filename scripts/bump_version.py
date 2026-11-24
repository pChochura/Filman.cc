#!/usr/bin/env python3
"""
Synchronized version bumping tool for Filman / Tiwi (Android + Web).
Ensures Android and Web versions are always strictly aligned in lockstep.
"""

import argparse
import json
import re
import sys
from pathlib import Path

def find_project_root() -> Path:
    current = Path(__file__).resolve().parent
    while current != current.parent:
        if (current / "settings.gradle.kts").exists() or (current / "app" / "build.gradle.kts").exists():
            return current
        current = current.parent
    return Path.cwd()

def main():
    parser = argparse.ArgumentParser(description="Synchronize version bumps across Android and Web apps.")
    parser.add_argument("--version", "-v", dest="new_version", help="Explicit version name (e.g., 2.29)")
    parser.add_argument("--code", "-c", dest="new_code", type=int, help="Explicit Android versionCode")
    parser.add_argument("--show", action="store_true", help="Display current versions without modifying files")
    args = parser.parse_args()

    root = find_project_root()
    gradle_file = root / "app" / "build.gradle.kts"
    pkg_file = root / "web" / "package.json"
    lock_file = root / "web" / "package-lock.json"
    settings_file = root / "web" / "src" / "menus" / "appSettingsMenu.js"

    # Read current Android version
    gradle_content = gradle_file.read_text(encoding="utf-8")
    code_match = re.search(r'versionCode\s*=\s*(\d+)', gradle_content)
    name_match = re.search(r'versionName\s*=\s*"([^"]+)"', gradle_content)
    if not code_match or not name_match:
        print("❌ Error: Could not parse versionCode or versionName from app/build.gradle.kts", file=sys.stderr)
        sys.exit(1)

    cur_code = int(code_match.group(1))
    cur_name = name_match.group(1)

    # Read current Web version
    pkg_content = pkg_file.read_text(encoding="utf-8")
    pkg_data = json.loads(pkg_content)
    cur_web_version = pkg_data.get("version", "")

    print("==================================================")
    print("📌 Current Configuration:")
    print(f"   Android versionName: {cur_name}")
    print(f"   Android versionCode: {cur_code}")
    print(f"   Web version:         {cur_web_version}")
    print("==================================================")

    if args.show:
        return

    # Determine next versions
    if args.new_version:
        new_name = args.new_version.strip()
    else:
        # Increment minor (e.g., 2.28 -> 2.29)
        parts = cur_name.split(".")
        if len(parts) >= 2 and parts[1].isdigit():
            new_name = f"{parts[0]}.{int(parts[1]) + 1}"
        else:
            print(f"❌ Error: Could not determine next version from '{cur_name}'. Specify --version X.Y", file=sys.stderr)
            sys.exit(1)

    if args.new_code is not None:
        new_code = args.new_code
    else:
        new_code = cur_code + 1

    web_semver = f"{new_name}.0" if len(new_name.split(".")) == 2 else new_name

    print("🎯 Bumping to:")
    print(f"   Android versionName: {new_name}")
    print(f"   Android versionCode: {new_code}")
    print(f"   Web package version: {web_semver}")
    print(f"   Web UI footer label: {new_name} (web)")
    print("==================================================")

    # 1. Update app/build.gradle.kts
    new_gradle = re.sub(r'(versionCode\s*=\s*)\d+', rf'\g<1>{new_code}', gradle_content)
    new_gradle = re.sub(r'(versionName\s*=\s*")[^"]+(")', rf'\g<1>{new_name}\g<2>', new_gradle)
    gradle_file.write_text(new_gradle, encoding="utf-8")

    # 2. Update web/package.json
    new_pkg = re.sub(r'("version":\s*")[^"]+(")', rf'\g<1>{web_semver}\g<2>', pkg_content)
    pkg_file.write_text(new_pkg, encoding="utf-8")

    # 3. Update web/package-lock.json if it exists
    if lock_file.exists():
        lock_content = lock_file.read_text(encoding="utf-8")
        # Update top-level version and packages[""].version
        new_lock = re.sub(r'("name":\s*"tiwi-web",\s*"version":\s*")[^"]+(")', rf'\g<1>{web_semver}\g<2>', lock_content, count=1)
        new_lock = re.sub(r'("":\s*\{\s*"name":\s*"tiwi-web",\s*"version":\s*")[^"]+(")', rf'\g<1>{web_semver}\g<2>', new_lock, count=1)
        lock_file.write_text(new_lock, encoding="utf-8")

    # 4. Update web/src/menus/appSettingsMenu.js
    if settings_file.exists():
        settings_content = settings_file.read_text(encoding="utf-8")
        new_settings = re.sub(
            r"(label:\s*t\('overlay_menu_app_version',\s*')[^']+(\(web\)'\))",
            rf"\g<1>{new_name} \g<2>",
            settings_content
        )
        settings_file.write_text(new_settings, encoding="utf-8")

    print("✓ Successfully synchronized and bumped versions across all files!")

if __name__ == "__main__":
    main()
