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

export const deletedFavoritesStore = createPersistedStore(
  'tiwi_deleted_favorites',
  [],
  (raw) => (Array.isArray(raw) ? Array.from(new Set(raw.map((u) => canonicalUrl(u)).filter(Boolean))) : []),
  'filman_deleted_favorites',
);

export function clearDeletedFavorites(urls = []) {
  if (!urls || urls.length === 0) {
    deletedFavoritesStore.set([]);
    return;
  }
  const toRemove = new Set(urls.map((u) => canonicalUrl(u)));
  deletedFavoritesStore.set((prev) => prev.filter((u) => !toRemove.has(u)));
}

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
      deletedFavoritesStore.set((del) => Array.from(new Set([...del, clean.url])));
      return prev.filter((m) => m.url !== clean.url);
    }
    added = true;
    deletedFavoritesStore.set((del) => del.filter((u) => u !== clean.url));
    return [clean, ...prev];
  });
  return added;
}

export function removeFavorite(url) {
  const key = canonicalUrl(url);
  deletedFavoritesStore.set((del) => Array.from(new Set([...del, key])));
  favoritesStore.set((prev) => prev.filter((m) => m.url !== key));
}

export function mergeSyncedFavorites(activeItems, deletedUrls = new Set()) {
  const delSet = deletedUrls instanceof Set ? deletedUrls : new Set(deletedUrls || []);
  if (delSet.size > 0) {
    clearDeletedFavorites(Array.from(delSet));
  }
  favoritesStore.set((prev) => {
    const remaining = prev.filter((item) => !delSet.has(canonicalUrl(item.url)));
    return sanitizeList([...activeItems, ...remaining]);
  });
}
