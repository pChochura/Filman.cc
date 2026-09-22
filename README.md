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

## 🤝 Contributing

Contributions, issues, and feature requests are welcome! Feel free to check
the [issues page](https://github.com/yourusername/filman/issues).

## 📝 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.
