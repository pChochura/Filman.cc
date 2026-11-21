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

export const watchlistStore = createPersistedStore('tiwi_watchlist', [], sanitizeList, 'filman_watchlist');

export const deletedWatchlistStore = createPersistedStore(
  'tiwi_deleted_watchlist',
  [],
  (raw) => (Array.isArray(raw) ? Array.from(new Set(raw.map((u) => canonicalUrl(u)).filter(Boolean))) : []),
  'filman_deleted_watchlist',
);

export function clearDeletedWatchlist(urls = []) {
  if (!urls || urls.length === 0) {
    deletedWatchlistStore.set([]);
    return;
  }
  const toRemove = new Set(urls.map((u) => canonicalUrl(u)));
  deletedWatchlistStore.set((prev) => prev.filter((u) => !toRemove.has(u)));
}

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
      deletedWatchlistStore.set((del) => Array.from(new Set([...del, clean.url])));
      return prev.filter((m) => m.url !== clean.url);
    }
    added = true;
    deletedWatchlistStore.set((del) => del.filter((u) => u !== clean.url));
    return [clean, ...prev];
  });
  return added;
}

export function removeFromWatchlist(url) {
  const key = canonicalUrl(url);
  deletedWatchlistStore.set((del) => Array.from(new Set([...del, key])));
  watchlistStore.set((prev) => prev.filter((m) => m.url !== key));
}

export function mergeSyncedWatchlist(activeItems, deletedUrls = new Set()) {
  const delSet = deletedUrls instanceof Set ? deletedUrls : new Set(deletedUrls || []);
  if (delSet.size > 0) {
    clearDeletedWatchlist(Array.from(delSet));
  }
  watchlistStore.set((prev) => {
    const remaining = prev.filter((item) => !delSet.has(canonicalUrl(item.url)));
    return sanitizeList([...activeItems, ...remaining]);
  });
}
