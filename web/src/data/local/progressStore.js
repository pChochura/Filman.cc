import { createPersistedStore, useStore } from './createStore.js';
import { removeFromWatchlist } from './watchlistStore.js';
import { canonicalUrl, parseMediaRef } from '../models/mediaRef.js';

export const MARK_AS_WATCHED_PROGRESS_THRESHOLD = 0.95;
const MAX_PROGRESS_ITEMS = 500;

function sanitizeProgressItem(item) {
  if (!item || typeof item !== 'object') return null;
  const ref = parseMediaRef(item.url, item.season !== null && item.season !== undefined);
  if (!ref) return null;
  const parentRef = item.parentUrl ? parseMediaRef(item.parentUrl, true) : ref.parentRef;
  const kind =
    item.kind === 'Watched' || item.kind === 'NextEpisode' || item.kind === 'InProgress'
      ? item.kind
      : item.isWatched
        ? 'Watched'
        : 'InProgress';
  return {
    kind,
    url: ref.canonicalKey,
    parentUrl: parentRef ? parentRef.parentKey : ref.parentKey,
    posterUrl: item.posterUrl || '',
    titlePl: item.titlePl || '',
    season: item.season ?? ref.season ?? null,
    episode: item.episode ?? ref.episode ?? null,
    seriesTitle: item.seriesTitle ?? null,
    episodeTitle: item.episodeTitle ?? null,
    hasNextEpisode: Boolean(item.hasNextEpisode),
    progressPercentage:
      kind === 'Watched' ? 1.0 : kind === 'NextEpisode' ? 0.0 : Number(item.progressPercentage) || 0,
    progressMs: Number(item.progressMs) || 0,
    timestamp: Number(item.timestamp) || Date.now(),
  };
}

function sanitizeList(raw) {
  if (!Array.isArray(raw)) return [];
  const seen = new Set();
  const out = [];
  for (const item of raw) {
    const clean = sanitizeProgressItem(item);
    if (clean && !seen.has(clean.url)) {
      seen.add(clean.url);
      out.push(clean);
    }
  }
  return out.slice(0, MAX_PROGRESS_ITEMS);
}

export const progressStore = createPersistedStore('filman_progress', [], sanitizeList);

export function useProgressItems() {
  return useStore(progressStore);
}

function insertOrderedProgressItem(items, item) {
  const normUrl = canonicalUrl(item.url);
  const normParent = canonicalUrl(item.parentUrl);
  const filtered = items.filter((it) => canonicalUrl(it.url) !== normUrl);
  const mostRecent = filtered.find((it) => canonicalUrl(it.parentUrl) === normParent);

  const itemSeason = item.season ?? 0;
  const itemEpisode = item.episode ?? 0;
  const recentSeason = mostRecent?.season ?? 0;
  const recentEpisode = mostRecent?.episode ?? 0;

  const isOlder =
    mostRecent &&
    (itemSeason < recentSeason || (itemSeason === recentSeason && itemEpisode < recentEpisode));

  if (isOlder) {
    const idx = filtered.indexOf(mostRecent);
    filtered.splice(idx + 1, 0, item);
  } else {
    filtered.unshift(item);
  }
  return filtered;
}

export function saveProgressItem(rawItem) {
  let item = sanitizeProgressItem(rawItem);
  if (!item) return;

  if (item.kind === 'InProgress' && item.progressPercentage >= MARK_AS_WATCHED_PROGRESS_THRESHOLD) {
    item = {
      ...item,
      kind: 'Watched',
      progressPercentage: 1.0,
    };
  }

  progressStore.set((prev) => insertOrderedProgressItem(prev, item).slice(0, MAX_PROGRESS_ITEMS));
}

export function savePlaybackProgress(movie, progressMs, durationMs) {
  const existing = getProgressForUrl(movie.url);
  const progressPercentage =
    durationMs > 0 ? progressMs / durationMs : existing?.progressPercentage ?? 0;
  const url = canonicalUrl(movie.url, movie.isTvShow);
  const parentUrl = movie.seriesUrl
    ? canonicalUrl(movie.seriesUrl, true)
    : parseMediaRef(url, movie.isTvShow)?.parentKey || url;

  saveProgressItem({
    kind: 'InProgress',
    url,
    parentUrl,
    posterUrl: movie.posterUrl || '',
    titlePl: movie.titlePl || '',
    season: movie.seasonNumber ?? null,
    episode: movie.episodeNumber ?? null,
    seriesTitle: movie.seriesTitle ?? null,
    episodeTitle: movie.episodeTitle ?? null,
    hasNextEpisode: Boolean(movie.hasNextEpisode),
    progressPercentage,
    progressMs,
    timestamp: Date.now(),
  });
}

export function toWatchedProgressItem(movie) {
  const url = canonicalUrl(movie.url, movie.isTvShow);
  const parentUrl = movie.seriesUrl
    ? canonicalUrl(movie.seriesUrl, true)
    : parseMediaRef(url, movie.isTvShow)?.parentKey || url;
  return {
    kind: 'Watched',
    url,
    parentUrl,
    posterUrl: movie.posterUrl || '',
    titlePl: movie.titlePl || '',
    season: movie.seasonNumber ?? null,
    episode: movie.episodeNumber ?? null,
    seriesTitle: movie.seriesTitle ?? null,
    episodeTitle: movie.episodeTitle ?? null,
    hasNextEpisode: Boolean(movie.hasNextEpisode),
    progressPercentage: 1.0,
    progressMs: 0,
    timestamp: Date.now(),
  };
}

export function markAsWatched(movieOrList) {
  if (Array.isArray(movieOrList)) {
    if (movieOrList.length === 0) return;
    progressStore.set((prev) => {
      let items = [...prev];
      for (const movie of movieOrList) {
        const clean = sanitizeProgressItem(toWatchedProgressItem(movie));
        if (clean) {
          items = insertOrderedProgressItem(items, clean);
        }
      }
      return items.slice(0, MAX_PROGRESS_ITEMS);
    });
    const first = movieOrList[0];
    if (first) {
      removeFromWatchlist(first.seriesUrl || first.url);
    }
    return;
  }

  saveProgressItem(toWatchedProgressItem(movieOrList));
  removeFromWatchlist(movieOrList.seriesUrl || movieOrList.url);
}

export function markAsNotWatched(url) {
  const norm = canonicalUrl(url);
  progressStore.set((prev) =>
    prev.filter((it) => !(canonicalUrl(it.url) === norm && it.kind === 'Watched')),
  );
}

export function removeProgress(url) {
  const norm = canonicalUrl(url);
  progressStore.set((prev) => prev.filter((it) => canonicalUrl(it.url) !== norm));
}

export function clearAllProgress() {
  progressStore.set([]);
}

export function getProgressForUrl(url) {
  const norm = canonicalUrl(url);
  return progressStore.get().find((it) => canonicalUrl(it.url) === norm) || null;
}

export function buildProgressMap(progressItems = progressStore.get()) {
  const map = {};
  for (const item of progressItems) {
    map[item.url] = item.progressPercentage ?? 0;
    if (item.parentUrl && item.parentUrl !== item.url && map[item.parentUrl] === undefined) {
      if (item.kind === 'Watched' && item.hasNextEpisode) {
        map[item.parentUrl] = 0;
      } else {
        map[item.parentUrl] = item.progressPercentage ?? 0;
      }
    }
  }
  return map;
}

export function mergeSyncedProgress(syncedItems) {
  if (!Array.isArray(syncedItems) || syncedItems.length === 0) return;
  progressStore.set((current) => {
    const map = new Map();
    for (const item of current) {
      map.set(canonicalUrl(item.url), item);
    }
    for (const raw of syncedItems) {
      const incoming = sanitizeProgressItem(raw);
      if (!incoming) continue;
      const key = canonicalUrl(incoming.url);
      const existing = map.get(key);
      if (!existing) {
        map.set(key, incoming);
      } else {
        let resolved = existing;
        if (incoming.timestamp > existing.timestamp) resolved = incoming;
        else if (existing.timestamp > incoming.timestamp) resolved = existing;
        else if (incoming.kind === 'Watched' && existing.kind !== 'Watched') resolved = incoming;
        else if (existing.kind === 'Watched' && incoming.kind !== 'Watched') resolved = existing;
        else if ((incoming.progressPercentage || 0) > (existing.progressPercentage || 0)) {
          resolved = incoming;
        }
        map.set(key, resolved);
      }
    }
    return Array.from(map.values())
      .sort((a, b) => (b.timestamp || 0) - (a.timestamp || 0))
      .slice(0, MAX_PROGRESS_ITEMS);
  });
}
