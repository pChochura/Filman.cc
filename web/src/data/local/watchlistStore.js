import { createPersistedStore, useStore } from './createStore.js';
import { canonicalUrl, sanitizeMovieItemToTmdb } from '../models/mediaRef.js';

function sanitizeList(raw) {
  if (!Array.isArray(raw)) return [];
  const seen = new Set();
  const out = [];
  for (const item of raw) {
    const clean = sanitizeMovieItemToTmdb(item);
    if (clean && !seen.has(clean.url)) {
      seen.add(clean.url);
      out.push(clean);
    }
  }
  return out;
}

export const watchlistStore = createPersistedStore('filman_watchlist', [], sanitizeList);

export function useWatchlist() {
  return useStore(watchlistStore);
}

export function isInWatchlist(url) {
  const key = canonicalUrl(url);
  return watchlistStore.get().some((m) => m.url === key);
}

export function toggleWatchlist(movie) {
  const clean = sanitizeMovieItemToTmdb(movie);
  if (!clean) return false;
  let added = false;
  watchlistStore.set((prev) => {
    const exists = prev.some((m) => m.url === clean.url);
    if (exists) {
      added = false;
      return prev.filter((m) => m.url !== clean.url);
    }
    added = true;
    return [clean, ...prev];
  });
  return added;
}

export function removeFromWatchlist(url) {
  const key = canonicalUrl(url);
  watchlistStore.set((prev) => prev.filter((m) => m.url !== key));
}

export function mergeSyncedWatchlist(activeItems, deletedUrls = new Set()) {
  watchlistStore.set((prev) => {
    const remaining = prev.filter((item) => !deletedUrls.has(canonicalUrl(item.url)));
    return sanitizeList([...activeItems, ...remaining]);
  });
}
