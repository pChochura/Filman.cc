# Google Play Automated Publishing

Automated deployment tool for publishing Android App Bundles (`.aab`) to the Google Play Store (Internal Testing track by default) using Google's official [`google-api-python-client`](https://github.com/googleapis/google-api-python-client) library.

---

## 📋 Prerequisites

### 1. Google Cloud Service Account Setup
Google Play Developer API requires authentication via a Google Cloud Service Account linked to your Google Play Console:

1. **Create Service Account**:
   - Go to [Google Cloud Console](https://console.cloud.google.com/).
   - Select or create the project linked with your Google Play developer account.
   - Go to **IAM & Admin > Service Accounts** and click **Create Service Account**.
   - Name it (e.g., `play-store-publisher`).
2. **Generate Key**:
   - Select the newly created service account > **Keys** tab > **Add Key** > **Create new key** > **JSON**.
   - Save the downloaded JSON file. You can place it at `scripts/play-store/service-account.json` (already in `.gitignore`) or specify its path with `--credentials`.
3. **Grant Permissions in Google Play Console**:
   - Go to [Google Play Console](https://play.google.com/console/).
   - Navigate to **Users and permissions** > **Invite new users**.
   - Enter your service account's email address (e.g. `play-store-publisher@<project-id>.iam.gserviceaccount.com`).
   - Under **App permissions**, select your app (`com.pointlessapps.filman`).
   - Grant permissions:
     - *Release apps to testing tracks*
     - *Manage testing tracks and edit tester lists*
     - *Release to production, exclude devices, and use Play App Signing* (if you plan to promote to production)
   - Click **Invite user** to save.

> [!IMPORTANT]
> **First-time manual upload required by Google**:
> Google Play Developer API requires at least **one** APK or AAB to have been uploaded manually through the Google Play Console web UI before the API can create automated edits for the application.

---

## 🚀 Quick Usage

### Option A: Build and publish to Internal Testing
```bash
./scripts/publish_internal.sh --build -n "Bug fixes and performance improvements"
```

### Option B: Publish an existing AAB
```bash
./scripts/publish_internal.sh -a app/release/app-release.aab -c path/to/service-account.json
```

### Option C: Validate without committing (Dry Run)
```bash
./scripts/publish_internal.sh --dry-run
```

---

## 🛠️ CLI Options

```
Usage: ./scripts/publish_internal.sh [OPTIONS]

Options:
  -b, --build             Build the release App Bundle (:app:bundleRelease) before publishing
  -c, --credentials FILE  Path to Google Cloud service account JSON credentials
                          (defaults to PLAY_SERVICE_ACCOUNT_KEY env var or scripts/play-store/service-account.json)
  -a, --aab FILE          Path to .aab file (defaults to app/build/outputs/bundle/release/app-release.aab or app/release/app-release.aab)
  -n, --notes "TEXT"      Release notes text (default language: en-US)
  -f, --notes-file FILE   Path to a text file containing release notes
  -t, --track TRACK       Target track (default: internal; choices: internal, alpha, beta, production)
  --dry-run               Validate the release with Google Play without publishing
  -h, --help              Show help message
```

---

## 🔐 Credentials Locations

The script automatically searches for service account JSON in:
1. `--credentials <path>` flag
2. `PLAY_SERVICE_ACCOUNT_KEY` environment variable
3. `GOOGLE_APPLICATION_CREDENTIALS` environment variable
4. `scripts/play-store/service-account.json`
5. `./service-account.json` or `./play-service-account.json`
