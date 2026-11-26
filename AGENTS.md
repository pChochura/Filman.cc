# Project Instructions & Deployment Protocol

Please refer to [GEMINI.md](file:///Users/pipistrelus/AndroidStudioProjects/filman.cc/GEMINI.md) for the complete and authoritative project instructions and deployment lifecycle.

## Summary Checklist for Updates:

1. **Verify**: Run `npm test` (in `web/`) and `./gradlew testDebugUnitTest`.
2. **Local Testing Gate**: If the change is substantial (player engine, algorithms, auth, major UI), stop, prompt the user to test locally, and wait for approval.
3. **Bump Versions**: Run `./scripts/bump_version.sh` to bump Android (`versionCode`, `versionName`) and Web (`package.json`, `appSettingsMenu.js`) in lockstep.
4. **Commit & Push**: `git add -A && git commit -m "..." && git push origin master`.
5. **Publish Android App**: `./scripts/publish_internal.sh --build -n "Release notes"`.
6. **Web App Deployment**: Deployed automatically upon pushing to `master` (do not run `./scripts/deploy_web.sh`).
