# Filman Lite (`:lite`)

Kotlin Multiplatform (Android + iOS) Compose app with a single screen: search a movie/TV show, then **Watch** it or **Download** it for offline playback. It talks to the same backend as the main app (`/search`, `/shows/{id}/details`, `/movies|shows/.../links`).

- Android host: `:liteApp` (`./gradlew :liteApp:installDebug`). Backend base URL defaults to `http://10.0.2.2:8080` (see `defaultBaseUrl` in `androidMain`).
- iOS host: create an Xcode iOS App target, add `liteIosApp/FilmanLiteApp.swift`, link `FilmanLite.framework` (built by `:lite:linkDebugFrameworkIosSimulatorArm64`, or an Xcode "Run Script" phase calling `./gradlew :lite:embedAndSignAppleFrameworkForXcode`), and allow local HTTP via ATS if using a plain-HTTP backend. Base URL defaults to `http://localhost:8080`.
- Tests: `./gradlew :lite:testAndroidHostTest`.

- Filman login: the **Log in** button opens filman.cc/logowanie in an embedded browser (Android WebView / iOS WKWebView). After a successful login the session cookies and user agent are stored (`filman_session.txt`) and sent to the backend as `X-Filman-Cookie` / `X-User-Agent` on every request, overriding the server's own cookie. **Log out** clears them. An expired session (401 / `NEEDS_AUTHORIZATION`) reopens the login page.

Limitations: no Cloudflare relay (surfaced as an error), HLS downloads are segment-concatenated into a single file (encrypted streams are rejected, and MPEG-TS results may not play on iOS), and downloads don't survive process death (interrupted ones are marked failed).
