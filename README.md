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
its selected providers.

### Legacy provider challenges

The following describes the legacy provider components retained in the codebase;
they are no longer started or used to discover movie and episode streams.

The Filman source is disabled by default. Enable it under Settings > Playback > Filman source;
this is the same preference used by Sources priority. Existing saved source preferences are retained.
At app launch, Filman, Zaluknij and CDA-HD providers are checked in parallel,
even when a source is disabled. A native request first checks whether the existing scoped
cookies already work; a successful check needs no verification WebView. Otherwise the WebView
runs in the background and the fullscreen prompt appears
on startup only when an actionable verification control is detected. If a startup verification
times out, the app leaves it retryable when a provider is needed.
When interaction is needed, a full-screen verification view keeps the original provider page
available and scrolls a recognized challenge control into view when possible. Cross-origin
challenge content cannot be isolated, so unrecognized pages remain available in full.
On Android TV, use the directional buttons to move the on-screen pointer and press OK to interact
with the page. Move the pointer to the top edge and press up to focus Skip, or press Back to skip.
Some challenges place controls inside cross-origin frames that the app cannot inspect. When a
provider is needed and a challenge remains after 12 seconds, the app shows that provider's
verification page even if it cannot detect the control. Startup-only checks remain in the
background unless an actionable control is detected.
Before a provider is marked verified, the app repeats the challenged safe request with a native
HTTP client when available; otherwise it checks the provider's base URL.
Page-load events trigger immediate checks, with periodic checks for changes inside the page.
Individual native probes time out after eight seconds and WebView status checks after two seconds;
timeouts and native probe outcomes are logged without cookie values. A Cloudflare script alone
does not identify a challenge; normal provider pages can include one too.
An in-progress provider WebView is retained when a startup check becomes an on-demand request.
Native verification probes read browser cookies but do not replace them with probe response cookies.
Cloudflare may still reload or retry its own challenge page.
Verification and embedded playback use the installed WebView's default user agent. Challenge
checkboxes are left for the user to operate rather than clicked automatically. Keeping Android
System WebView current and hardware acceleration available can improve compatibility; Cloudflare
may still reject a device or network, and a successful native provider request is required.
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
