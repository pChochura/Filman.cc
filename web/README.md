# Tiwi Web 🎬

A modern React web client designed to match the dark cinematic aesthetic and feature set of the Tiwi Android TV app.

---

## 🔄 Mandatory Dual-Platform Parity with Android TV

> [!IMPORTANT]
> **The Web client (`/web`) and the Android TV app must always stay in 100% sync.**
>
> Tiwi treats Android TV and Web as equal first-class platforms. Whenever something changes—whether it is a bug fix, new feature, subtitle matching/ranking/alignment algorithm, Lektor voice-over logic, stream extraction, settings persistence, or player UI behavior:
> 
> 1. **Both platforms must ship the same change together.** Never implement or fix something on one platform without simultaneously shipping the matching change to the other.
> 2. **Algorithmic parity is strictly enforced:**
>    - Video signature extraction (`data/api/videoSignatureExtractor.js` $\leftrightarrow$ `VideoSignatureExtractor.kt`)
>    - Subtitle ranking & cut detection (`data/api/subtitleRanking.js`, `subtitleRepository.js` $\leftrightarrow$ `SubtitleRepository.kt`)
>    - Subtitle raster cross-correlation (`data/api/subtitleTrackAligner.js` $\leftrightarrow$ `SubtitleTrackAligner.kt`)
>    - Subtitle cue diagnostics (`data/api/subtitleDiagnostics.js` $\leftrightarrow$ `SubtitleDiagnostics.kt`)
>    - Lektor speech synthesis (`player/lektor/*` $\leftrightarrow$ `com.pointlessapps.filman.player.lektor.*`)
> 3. **Dual Verification:** Always verify both test suites:
>    - `cd web && npm test` and `npm run build`
>    - `./gradlew testDebugUnitTest` and `./gradlew assembleDebug`
> 4. **Version Bumps:** Web footer version (`web/src/menus/appSettingsMenu.js`) and Android app version (`app/build.gradle.kts`) must always be bumped in lockstep.
> 5. **Documented Platform Divergences:**
>    - **"Watch on YouTube" Full Movie Search:** Android-only. Android leverages the embedded `NewPipeExtractor` Java library and ExoPlayer's `MergingMediaSource` to stream adaptive video and audio directly on the user's IP. On web, browser CORS restrictions, server-side bot-rate-limiting risks, and YouTube embed restrictions (`allowEmbedding: false` on full movies) make web-based extraction unfeasible and fragile. See `docs/ARCHITECTURE.md` §15.5 for details.

---

## ✨ Features

- **Tiwi Design System Matching**:
  - Pure black background (`#000000`), dark surfaces (`#141414`, `#2F2F2F`), and bold crimson accent (`#E50914`).
  - Floating pill navigation bar with frosted glass effect (`backdrop-filter: blur(16px)`).
  - Cinematic hero banner with gradient scrims, IMDb rating badges (`#DBA506`), and metadata pills.
  - Responsive 3:4 portrait poster cards with hover scale animations and red glow accents.
- **Search & Discovery**:
  - Live search connected to TMDB multi-search API (`/search/multi`).
  - Filters for All ("Wszystko"), Movies ("Filmy"), and TV Series ("Seriale").
  - Instant debouncing and loading feedback.
  - Automatic trending feed on launch so the homepage is never blank.
- **Streaming & Playback**:
  - Connected directly to the self-hosted Stream API.
  - Real-time Server-Sent Events (SSE) stream discovery, loading stream sources incrementally.
  - Plays **HLS streams (`.m3u8`)** with `hls.js`, **direct MP4/WebM**, and **embed sources (`cda.pl` iframes)**.
  - Source selector dropdown to switch between servers (VideoGetter, Vidlink, Febbox FID, StreamFlix, etc.) and quality levels (4K, 1080p, 720p, Auto).
  - TV Shows support: season selector, episode list with thumbnails, and a "Next Episode" button in the player.
  - Built-in Vite streaming proxy (`/api/proxy`) for cross-origin or header-restricted media.

## 🚀 Getting Started

From the `web` folder:

```bash
cd web
npm install
npm run dev
```

The app will be available at [http://localhost:3000](http://localhost:3000).

To build for production:

```bash
npm run build
npm run preview
```
