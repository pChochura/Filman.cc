import { createPersistedStore, useStore } from './createStore.js';
import { favoritesStore } from './favoritesStore.js';
import { progressStore } from './progressStore.js';
import { getTvShowSourceSettings, saveTvShowSourceSettings } from './tvShowSettingsStore.js';
import { RECENTLY_FINISHED_WINDOW_MS } from './continueWatchingMapper.js';
import { canonicalUrl, parseMediaRef, sanitizeMovieItemToTmdb } from '../models/mediaRef.js';

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

export const newEpisodesStore = createPersistedStore(
  'tiwi_new_episodes',
  [],
  sanitizeList,
  'filman_new_episodes',
);

export function useNewEpisodes() {
  return useStore(newEpisodesStore);
}

export function clearNewEpisode(url) {
  const norm = canonicalUrl(url);
  newEpisodesStore.set((prev) => prev.filter((item) => canonicalUrl(item.url) !== norm));
}

let checkedThisSession = false;

export async function checkForNewEpisodes(tmdbClient) {
  if (checkedThisSession) return;
  checkedThisSession = true;

  try {
    const now = Date.now();
    const progress = progressStore.get();
    const favorites = favoritesStore.get();

    const candidateShowKeys = new Set();
    for (const item of progress) {
      const ref = parseMediaRef(item.parentUrl || item.url, item.season != null);
      if (!ref || !ref.isTvShow) continue;
      if (now - (item.timestamp || 0) <= RECENTLY_FINISHED_WINDOW_MS) {
        candidateShowKeys.add(ref.parentKey);
      }
    }
    for (const fav of favorites) {
      if (fav.isTvShow) {
        const ref = parseMediaRef(fav.url, true);
        if (ref) candidateShowKeys.add(ref.parentKey);
      }
    }

    if (candidateShowKeys.size === 0) return;

    const discovered = [];
    const keys = Array.from(candidateShowKeys).slice(0, 10);
    await Promise.all(
      keys.map(async (showKey) => {
        const ref = parseMediaRef(showKey, true);
        if (!ref) return;
        const details = await tmdbClient.getTvSummaryForEpisodeCheck(ref.tmdbId);
        if (!details || !details.totalEpisodes) return;

        const saved = getTvShowSourceSettings(showKey);
        const lastKnown = saved?.lastKnownEpisodeCount;
        if (typeof lastKnown === 'number' && details.totalEpisodes > lastKnown && details.latestEpisodeKey) {
          discovered.push({
            url: details.latestEpisodeKey,
            titlePl: details.title,
            posterUrl: details.posterUrl,
            backgroundUrl: details.backgroundUrl,
            isTvShow: true,
            seriesUrl: showKey,
            detailsRequest: { url: showKey, isTvShow: true },
          });
        }
        saveTvShowSourceSettings(showKey, { lastKnownEpisodeCount: details.totalEpisodes });
      }),
    );

    if (discovered.length > 0) {
      newEpisodesStore.set((prev) => sanitizeList([...discovered, ...prev]));
    }
  } catch {
    // Fail soft on background episode check
  }
}
