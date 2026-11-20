# Tiwi Web 🎬

A lightweight React web client designed to match the dark cinematic aesthetic of the Tiwi Android app with streamlined functionality focused purely on **searching videos and playing them**.

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
