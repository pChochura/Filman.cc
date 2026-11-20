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

export const favoritesStore = createPersistedStore('tiwi_favorites', [], sanitizeList, 'filman_favorites');

export function useFavorites() {
  return useStore(favoritesStore);
}

export function isFavorite(url) {
  const key = canonicalUrl(url);
  return favoritesStore.get().some((m) => m.url === key);
}

export function toggleFavorite(movie) {
  const clean = sanitizeMovieItemToTmdb(movie);
  if (!clean) return false;
  let added = false;
  favoritesStore.set((prev) => {
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

export function mergeSyncedFavorites(activeItems, deletedUrls = new Set()) {
  favoritesStore.set((prev) => {
    const remaining = prev.filter((item) => !deletedUrls.has(canonicalUrl(item.url)));
    return sanitizeList([...activeItems, ...remaining]);
  });
}
