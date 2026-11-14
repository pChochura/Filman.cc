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
* **Search:** Powerful search functionality to quickly find your favorite content.
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

### Provider challenges

The Filman source is disabled by default. Enable it under Settings > Playback > Filman source;
this is the same preference used by Sources priority. Existing saved source preferences are retained.
At app launch, Filman, Zaluknij and CDA-HD provider WebViews are checked in parallel,
even when a source is disabled. They run in the background and the fullscreen prompt appears
only when an actionable verification control is detected. If verification times out without
such a control, the app leaves it retryable when a provider is needed.
Some challenges place controls inside cross-origin frames that the app cannot inspect; those
may not be detected as interactive and can time out instead of opening the prompt.
Before a provider is marked verified, the app repeats the challenged safe request with a native
HTTP client when available; otherwise it checks the provider's base URL.
Successful verification triggers a retry of the affected provider while retaining other sources.
This check does not guarantee that another URL, a native player, or an interactive challenge
will succeed; the app continues to offer available alternatives.

The shared native client obtains request cookies from Android's `CookieManager`, which applies
the stored cookies' scope and expiry. Legacy session strings are still restored for existing
accounts and do not preserve the original cookie attributes. TLS certificate and hostname
verification remain enabled for native HTTP clients.

### Embedded playback

The embedded player stays behind the loading screen until a visible, decoded video has begun
advancing. Media and Web Audio in the WebView are muted during loading, and audio is enabled
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
