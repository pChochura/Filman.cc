# Filman 🎬

![Filman Banner](docs/images/banner_placeholder.png) <!-- Placeholder for a banner image -->

Filman is a comprehensive, open-source Android application designed for streaming movies and TV
shows. Built with a modern Android tech stack, it provides a seamless and immersive viewing
experience across both **Mobile** devices and **Android TV** (Leanback support).

## 📱 Screenshots

|                  Mobile - Home                   |                    Mobile - Details                    |                   Mobile - Player                    |
|:------------------------------------------------:|:------------------------------------------------------:|:----------------------------------------------------:|
| ![Home](docs/images/mobile_home_placeholder.png) | ![Details](docs/images/mobile_details_placeholder.png) | ![Player](docs/images/mobile_player_placeholder.png) |

|                Android TV - Home                |                 Android TV - Details                  |                 Android TV - Player                 |
|:-----------------------------------------------:|:-----------------------------------------------------:|:---------------------------------------------------:|
| ![TV Home](docs/images/tv_home_placeholder.png) | ![TV Details](docs/images/tv_details_placeholder.png) | ![TV Player](docs/images/tv_player_placeholder.png) |

*(Note: Replace the placeholder image paths with actual screenshots of the application)*

---

## ✨ Features

* **Multi-Platform Support:** Fully optimized for both Android smartphones/tablets and Android TV.
* **Extensive Content Library:** Browse through a vast collection of Movies and TV Shows.
* **Kids Mode:** Dedicated "For Kids" section for safe family viewing.
* **Advanced Player:** Custom media player built on top of Media3/ExoPlayer for high-performance
  streaming.
* **Subtitles:** OpenSubtitles and Wyzie searches use the media's TMDB ID when available, including
  season and episode for TV shows. When no ID is available, they fall back to title-based matching.
  OpenSubtitles shows alternative releases and labels forced-only and hearing-impaired tracks; select
  a result to download it. Tracks not flagged as forced-only are ranked first, with exact video filename matches
  preferred when available. Warnings about sparse cues, long gaps, or early endings are approximate:
  a matching ID or a clean warning check does not guarantee complete or synchronized subtitles.
* **Polish voice-over:** Select Polish subtitles and enable Lektor. On slower devices, choose
  **System voice (faster)** in the Lektor voice menu; it requires an installed offline Polish voice
  in Android's text-to-speech settings. After choosing it, select and preview any installed offline
  Polish system voice; refresh the list after installing voice data. Downloaded AI voices remain
  available as separate choices. **Marek Neural (online)** and **Zofia Neural (online)** send subtitle
  text to Microsoft and require an internet connection. Lektor joins nearby subtitle fragments into
  complete sentences when possible. Voice-over starts 0.5 seconds before the first subtitle fragment
  by default; adjust **Voice-over timing** in Lektor settings to synchronize it with playback.
  Video audio remains at full volume by default; optional reductions are available in Lektor settings.
  Choose **Voice-over only** in Lektor settings to hide subtitles while Lektor is enabled. The selected
  subtitle remains available for narration; subtitles reappear when Lektor is turned off.
  Some embedded players may draw their own captions outside the app's control.
  Pausing preserves prepared narration; online and downloaded voices resume interrupted audio,
  while system voices restart the interrupted sentence.
* **Watch History:** Keep track of what you've watched and resume playback where you left off.
* **Detailed Info:** View comprehensive details about movies, shows, and actors.
* **Search:** Find movies and TV shows using TMDB metadata; results without posters are hidden.
* **User Authentication:** Login system to sync preferences and watch history.
* **Screensaver:** Custom screensaver integration for Android TV.
* **Deep Linking:** Support for deep links (e.g., `filman://details`) to launch specific content
  directly.

## 🛠️ Tech Stack & Architecture

Filman is built entirely with **Kotlin** and follows modern Android development best practices. It
utilizes a highly modularized architecture (`core` and `feature` modules) to ensure scalability,
maintainability, and fast build times.

### Core Technologies:

* **[Kotlin](https://kotlinlang.org/):** Primary language for development, utilizing Coroutines and
  Flow for asynchronous programming.
* **[Jetpack Compose](https://developer.android.com/jetpack/compose):** Modern, declarative UI
  toolkit used for building the entire user interface.
* **[Jetpack Media3 (ExoPlayer)](https://developer.android.com/media/media3):** Robust media
  playback engine for video streaming.
* **[Koin](https://insert-koin.io/):** A pragmatic lightweight dependency injection framework for
  Kotlin.
* **[Coil](https://coil-kt.github.io/coil/):** Image loading library backed by Kotlin Coroutines.
* **[Navigation 3](https://developer.android.com/guide/navigation):** For seamless in-app navigation
  and deep linking.
*
**[Jsoup](https://jsoup.org/) & [NewPipe Extractor](https://github.com/TeamNewPipe/NewPipeExtractor):**
Used for parsing and extracting media streams and metadata.
* **[OkHttp](https://square.github.io/okhttp/):** Powerful HTTP client for network requests.
* **[Kotlinx Serialization](https://github.com/Kotlin/kotlinx.serialization):** JSON parsing and
  serialization.

### Architecture (Modularization)

The project is split into distinct modules:

- `:app` - The main application module bringing everything together.
- `:core:*` - Common utilities, UI components, data layer, and player implementations (`:core:data`,
  `:core:player`, `:core:ui`).
- `:feature:*` - Independent feature modules representing different screens/functionalities
  (`:feature:home`, `:feature:details`, `:feature:movies`, `:feature:tvshows`,
  `:feature:watchhistory`, `:feature:player`, `:feature:actor`, `:feature:search`, etc.).

### Self-hosted stream API

Movie and episode streams are obtained exclusively from a separately hosted
[TMDB Embed API](https://github.com/Inside4ndroid/TMDB-Embed-API). TMDB remains
the metadata source; YouTube trailers are separate from movie and episode playback.
Configure the API base URL in the project's `local.properties` before building:

```properties
STREAM_API_BASE_URL=https://your-stream-server.example
```

The value is included in the app at build time, defaults to empty, and is not a
secret. Rebuild the app after changing it. The device must be able to reach this
address; `localhost` refers to the device itself. A path prefix is supported.
Configure the server's TMDB key and providers on the server, not in this field.
Use HTTPS, change the upstream default admin password, and restrict administrative,
configuration, debug, and optional proxy endpoints with a trusted gateway. At the
upstream revision inspected for this integration, `/api/config` is not protected
by the admin-page login. Ensure providers and media are authorized for your use.

The client uses `/api/streams/movie/{tmdbId}` and
`/api/streams/series/{tmdbId}?season={season}&episode={episode}`. Provider
configuration and priority are managed on the server. Existing source priority
preferences are retained but no longer control feature playback. API failures
and empty results leave playback unavailable; configure the server and retry.
The API call has a 50-second timeout because upstream aggregation waits for
its selected providers. Successful stream responses, including headers and
subtitles, are cached in private app storage for five minutes and reused after
restart. Settings > Clear cache removes them; failed and empty responses are
not stored on disk.

### Legacy provider challenges

The following describes the legacy provider components retained in the codebase;
they are no longer started or used to discover movie and episode streams.

The Filman source is disabled by default. Enable it under Settings > Playback > Filman source;
this is the same preference used by Sources priority. Existing saved source preferences are retained.
At app launch, Filman, Zaluknij and CDA-HD providers are checked in parallel,
even when a source is disabled. A native request first checks whether the existing scoped
cookies already work; a successful check needs no verification WebView. Otherwise the WebView
runs in the background and the fullscreen prompt appears when an actionable verification control
is detected, or when a challenge remains after the grace period. If a startup verification
does not progress in the background, the app shows the verification page instead of silently
timing it out. The user can still skip verification.
When no specific GET request is being checked, Filman verification opens its login page, as the
login screen does. Zaluknij and CDA-HD use their provider pages. The verification WebView shares
the login and player browser session setup and uses the player's wide viewport; it does not clear
existing cookies or log the user out.
When interaction is needed, a full-screen verification view keeps the original provider page
available and scrolls a recognized challenge control into view when possible. Cross-origin
challenge content cannot be isolated, so unrecognized pages remain available in full.
On Android TV, use the directional buttons to move the on-screen pointer and press OK to interact
with the page. Move the pointer to the top edge and press up to focus Skip, or press Back to skip.
Some challenges place controls inside cross-origin frames that the app cannot inspect. When a
challenge remains after 12 seconds, including at startup, the app shows that provider's
verification page even if it cannot detect the control.
Existing sessions are checked with a native request before a WebView is opened. During a
challenge, the app immediately checks a newly issued provider-scoped `cf_clearance` cookie with
a native request, without waiting for a page reload to finish. It closes the verification view
only when the provider accepts the request and the cookie is still present. A pre-existing
unchanged cookie alone does not close it. A clear page can also be confirmed with a native request.
Receiving a new cookie alone does not guarantee that the provider will accept it; if the request
is rejected, the verification view stays open and can be skipped.
Page-load events trigger immediate checks, with periodic checks for changes inside the page.
Individual native probes time out after eight seconds and WebView status checks after two seconds;
timeouts and native probe outcomes are logged without cookie values. A Cloudflare script alone
does not identify a challenge; normal provider pages can include one too.
Debug builds also log bounded JavaScript state snapshots, main-frame HTTP and network errors, and
TLS error categories without page contents, cookie values, or challenge tokens. A missing recognized
challenge frame does not rule out a cross-origin or shadow-DOM frame. TLS errors are cancelled, not
ignored. Filter Logcat by `FilmanApp` and `ChallengeVerifier` when investigating verification.
An in-progress provider WebView is retained when a startup check becomes an on-demand request.
Native verification probes read browser cookies but do not replace them with probe response cookies.
Cloudflare may still reload or retry its own challenge page.
Verification and embedded playback use the installed WebView's default user agent. Challenge
checkboxes are left for the user to operate rather than clicked automatically. WebView cookies
are flushed on completion and reused by native requests with their browser-managed
scope and expiry; expired or session-only cookies may require verification again. Ensure the
device date and time are correct. Keeping Android System WebView current and hardware acceleration
available can improve compatibility; Cloudflare
may still reject a device or network, and a successful native provider request is required.
For persistent loops, Cloudflare recommends checking the network and testing a supported browser
on another device. Its documentation notes that embedded browsers have limited support and
emulator results may differ from physical devices. See [challenge solve issues](https://developers.cloudflare.com/cloudflare-challenges/troubleshooting/challenge-solve-issues/)
and [supported browsers](https://developers.cloudflare.com/cloudflare-challenges/reference/supported-browsers/).
If the visible challenge takes longer than 45 seconds, the app offers troubleshooting guidance
without interrupting the verification or preventing the user from skipping it.
Successful verification triggers a retry of the affected provider while retaining other sources.
This check does not guarantee that another URL, a native player, or an interactive challenge
will succeed; the app continues to offer available alternatives.

For TV episodes, source extraction starts as soon as the show's metadata is available and runs
alongside the requests for season details. Playback still waits for the episode details and source
selection; the overlap avoids serializing those two network-dependent steps.

The shared native client obtains provider request cookies from Android's `CookieManager`, which
applies the stored cookies' scope and expiry. Legacy Filman login strings are still restored for
existing accounts without their original attributes, but old Cloudflare cookie strings are not
restored when absent from the browser cookie store. TLS certificate and hostname
verification remain enabled for native HTTP clients.

### Embedded playback

The embedded player stays hidden until a visible, decoded video has begun advancing. While it
loads, the player buffering indicator and source controls remain available. Media and Web Audio
in the WebView are muted during loading, and audio is enabled
only after playback is verified. Known advertising hosts and popups are blocked, and recognized ad playback is hidden.
Interactive verification remains visible when required. If playback cannot be verified within
45 seconds (excluding time spent on a verification prompt), the player tries another source.
Uninspectable cross-origin embeds, unusual player layouts, and server-side ads may not be
distinguishable from content; blocking all advertisements cannot be guaranteed.
The WebView must support AndroidX WebKit document-start scripts for safe loading audio;
otherwise the player moves to another source.

## 🚀 Getting Started

### Prerequisites

* [Android Studio Ladybug](https://developer.android.com/studio) or newer (for full Compose
  support).
* JDK 17.

### Build Instructions

1. Clone the repository:
   ```bash
   git clone https://github.com/yourusername/filman.git
   ```
2. Open the project in Android Studio.
3. Sync the project with Gradle files.
4. Select the `app` run configuration.
5. Choose your target device (Mobile or Android TV emulator/device).
6. Click **Run** (Shift + F10).

The embedded-player JavaScript regression tests can also be run with
`node --test core/ui/src/test/js/player-script.test.cjs`.

## 🤝 Contributing

Contributions, issues, and feature requests are welcome! Feel free to check
the [issues page](https://github.com/yourusername/filman/issues).

## 📝 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.
