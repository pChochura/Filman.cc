# Project Instructions & Deployment Protocol

This document defines the mandatory development, versioning, and deployment lifecycle for this project.
Every agent and developer working on this codebase must follow these instructions strictly.

---

## 🚀 Automatic Deployment on Every Update

Every time a new update is introduced—whether it is a new feature, improvement, architectural change, or bug fix:
1. The update must be verified across test suites.
2. If the change is substantial, the user must be offered local testing and must approve before proceeding.
3. Both Android and Web apps must bump their versions in lockstep.
4. Changes must be committed and pushed to git.
5. The Android app must be published using the release script.
6. The Web app must be deployed to the production server via SSH.

---

## 📋 Standard Workflow

```
[ Make Changes ]
       │
       ▼
[ Verify Test Suites ]
  - Web: npm test
  - Android: ./gradlew testDebugUnitTest
       │
       ▼
[ Assess Change Impact ]
  ├── If Substantial ──► Prompt user to test locally & WAIT for approval
  └── If Non-Substantial ──► Proceed directly
       │
       ▼
[ Bump Versions (Lockstep Parity) ]
  - ./scripts/bump_version.sh
       │
       ▼
[ Commit & Push ]
  - git add -A
  - git commit -m "..."
  - git push origin master
       │
       ▼
[ Deploy Both Platforms ]
  ├── Android: ./scripts/publish_internal.sh --build -n "..."
  └── Web:     ./scripts/deploy_web.sh (via SSH to configured host)
```

---

## 🔍 Step-by-Step Instructions

### Step 1: Verification & Local Testing Decision

#### 1.1 Run Both Test Suites
Always ensure both test suites pass before proceeding:
```bash
# Android Unit Tests
./gradlew testDebugUnitTest

# Web Client Tests & Production Build
cd web && npm test && npm run build && cd ..
```

#### 1.2 Assess Change Impact (Substantial vs Non-Substantial)
You (the assistant) must evaluate whether the update represents a **substantial change**:

- **Substantial changes** include:
  - Major UI/UX redesigns, layout modifications, or new screens.
  - Video player core modifications, stream discovery algorithms, or link resolver refactoring.
  - Subtitle ranking, parser, cross-correlation, or TTS voiceover engine adjustments.
  - Cloud sync (`tmdb_sync`), database migrations, or authentication / session handling modifications.
  - Breaking changes, protocol modifications, or major external dependency upgrades.

- **Non-substantial changes** include:
  - Minor bug fixes or edge-case null checks.
  - Localization / string additions or typo corrections.
  - Styling adjustments that do not alter layout flows.
  - Internal doc updates or build tooling refactors without functional impact.

#### 1.3 Gate on User Approval for Substantial Changes
If the change is **substantial**:
1. **DO NOT** bump versions or deploy yet.
2. Offer the user instructions to test locally:
   - **Web App**:
     ```bash
     cd web && npm run dev
     # Open http://localhost:3000
     ```
   - **Android TV App**:
     ```bash
     ./gradlew assembleDebug
     # Or install directly to connected TV / emulator:
     adb install -r app/build/outputs/apk/debug/app-debug.apk
     ```
3. Ask the user for their feedback and wait for their explicit approval.
4. Only proceed to Step 2 once the user approves.

---

### Step 2: Version Bump (Strict Dual-Platform Lockstep)

Both Android and Web apps **must always be aligned to the exact same version name**. Never bump one platform without bumping the other.

Use the synchronized bump script:
```bash
./scripts/bump_version.sh
```
Or specify explicit versions if needed:
```bash
./scripts/bump_version.sh --version <X.Y> --code <Z>
```

This updates all version references in lockstep:
1. `app/build.gradle.kts`:
   - Increments `versionCode` (e.g., `97` -> `98`).
   - Increments `versionName` (e.g., `"2.28"` -> `"2.29"`).
2. `web/package.json` & `web/package-lock.json`:
   - Updates `"version"` to match `versionName` (e.g., `"2.29.0"`).
3. `web/src/menus/appSettingsMenu.js`:
   - Updates the settings overlay footer version string:
     `label: t('overlay_menu_app_version', '2.29 (web)')`.

Verify version alignment:
```bash
./scripts/bump_version.sh --show
```

---

### Step 3: Commit and Push Changes

Commit all modified files following conventional commits, noting the new version and version code:

```bash
git add -A
git commit -m "feat/fix: <summary of changes> and bump version to <VERSION> (code <CODE>)"
git push origin master
```

---

### Step 4: Publish Android App to Google Play

Publish the Android App Bundle to the Google Play Console Internal Testing track using the automated script:

```bash
./scripts/publish_internal.sh --build -n "<Brief release notes for this update>"
```

- This runs `./gradlew :app:bundleRelease` to build the release `.aab`.
- It signs the bundle using keys configured in `local.properties`.
- It uploads and promotes the release on the Google Play internal testing track using the service account credentials in `scripts/play-store/service-account.json`.

---

### Step 5: Deploy Web App via SSH

Deploy the updated web application to the production server:

```bash
./scripts/deploy_web.sh
```

**Host Configuration**:
`scripts/deploy_web.sh` automatically reads `DEPLOY_HOST` and `DEPLOY_USER` from uncommitted local configuration (`local.properties` or `web/.env`) or environment variables, keeping credentials and server IPs out of version control:
- `local.properties`: `DEPLOY_HOST=<ip>`, `DEPLOY_USER=ubuntu`
- Or pass via environment: `DEPLOY_HOST=<ip> ./scripts/deploy_web.sh`

**Under the hood, this script executes:**
```bash
ssh -o BatchMode=yes "${DEPLOY_USER}@${DEPLOY_HOST}" << 'EOF'
set -euo pipefail
cd /home/ubuntu/filman
git pull origin master
cd web
docker compose up -d --build
docker ps --filter "name=tiwi-web"
EOF
```

#### Verification:
The deployment script verifies container health automatically after restarting:
```bash
ssh -o BatchMode=yes "${DEPLOY_USER}@${DEPLOY_HOST}" "docker ps --filter name=tiwi-web && curl -s http://localhost:3000/healthz"
```

---

## 🛡️ Key Safety Rules & Guidelines

1. **Dual-Platform Parity**: The Web client and Android TV app must remain feature and algorithm parity aligned (see `docs/ARCHITECTURE.md` and `web/README.md`).
2. **Zero Hardcoded Strings**: All user-facing strings must be localized in `strings.xml` (English and Polish for Android) and `strings.en.js` / `strings.pl.js` (Web).
3. **No Unapproved Deployments**: Never bypass the local testing gate when a change is substantial.
4. **Clean Git State**: Always ensure working directory is clean and tests pass before triggering deployment.
