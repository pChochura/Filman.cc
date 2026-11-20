import { canonicalUrl } from '../models/mediaRef.js';
import { formatWatchDate } from '../../i18n/index.js';

export const MIN_ENGAGEMENT_PROGRESS = 0.05;
export const MIN_ENGAGEMENT_MS = 120_000;
export const ACTIVE_WATCH_WINDOW_MS = 30 * 24 * 60 * 60 * 1000;
export const RECENTLY_FINISHED_WINDOW_MS = 180 * 24 * 60 * 60 * 1000;

export function distinctBySeries(items) {
  const seen = new Set();
  const out = [];
  for (const item of items) {
    const key = item.parentUrl || item.url;
    if (!seen.has(key)) {
      seen.add(key);
      out.push(item);
    }
  }
  return out;
}

export function toContinueWatchingItems(items) {
  const out = [];
  for (const item of items) {
    if (item.kind === 'Watched') {
      if (item.parentUrl && item.parentUrl !== item.url && item.hasNextEpisode) {
        out.push({
          ...item,
          kind: 'NextEpisode',
          titlePl: item.seriesTitle || item.titlePl,
          progressPercentage: 0,
        });
      }
    } else {
      out.push(item);
    }
  }
  return out;
}

export function extractFullyWatchedUrls(items) {
  const set = new Set();
  for (const item of items) {
    if (item.kind !== 'Watched') continue;
    if (!item.parentUrl || item.parentUrl === item.url) {
      set.add(canonicalUrl(item.url));
    } else if (!item.hasNextEpisode) {
      set.add(canonicalUrl(item.parentUrl || item.url));
    }
  }
  return set;
}

export function filterUnwatchedWatchlist(watchlist, progressItems) {
  const fullyWatched = extractFullyWatchedUrls(progressItems);
  return watchlist.filter((item) => !fullyWatched.has(canonicalUrl(item.url)));
}

export function isSampled(item, allProgressItems = []) {
  if (!item || item.kind !== 'InProgress') return false;
  const isMinimal =
    (item.progressPercentage || 0) < MIN_ENGAGEMENT_PROGRESS &&
    (item.progressMs || 0) < MIN_ENGAGEMENT_MS;
  if (!isMinimal) return false;

  const parent = item.parentUrl;
  if (parent && parent !== item.url) {
    if ((item.season ?? 1) > 1 || (item.episode ?? 1) > 1) {
      return false;
    }
    const otherItems = allProgressItems.filter(
      (it) => (it.parentUrl === parent || it.url === parent) && it.url !== item.url,
    );
    const hasPreviousOrEngaged = otherItems.some(
      (it) =>
        it.kind === 'Watched' ||
        (it.kind === 'InProgress' &&
          ((it.progressPercentage || 0) >= MIN_ENGAGEMENT_PROGRESS ||
            (it.progressMs || 0) >= MIN_ENGAGEMENT_MS)),
    );
    return !hasPreviousOrEngaged;
  }
  return true;
}

export function isActiveWatch(item, now = Date.now(), activeWindowMs = ACTIVE_WATCH_WINDOW_MS) {
  return now - (item.timestamp || 0) <= activeWindowMs;
}

export function isDroppedWatch(item, now = Date.now(), activeWindowMs = ACTIVE_WATCH_WINDOW_MS) {
  return now - (item.timestamp || 0) > activeWindowMs;
}

export function toActiveContinueWatchingItems(items, now = Date.now(), allProgressItems = items) {
  return toContinueWatchingItems(items).filter(
    (item) => !isSampled(item, allProgressItems) && isActiveWatch(item, now),
  );
}

export function toJumpBackInItems(items, now = Date.now(), allProgressItems = items) {
  return toContinueWatchingItems(items).filter(
    (item) => !isSampled(item, allProgressItems) && isDroppedWatch(item, now),
  );
}

export function formatSeasonEpisodeTag(item) {
  if (item.season == null || item.episode == null) return null;
  return `S${item.season}E${item.episode}`;
}

export function formatContinueWatchingTitle(item, nextEpisodeFormatter) {
  if (item.kind === 'NextEpisode') {
    const base = item.seriesTitle || item.titlePl;
    return nextEpisodeFormatter ? nextEpisodeFormatter(base) : `${base} - Next episode`;
  }
  if (item.seriesTitle && item.episodeTitle) {
    return `${item.seriesTitle} - ${item.episodeTitle}`;
  }
  if (item.seriesTitle && item.season != null && item.episode != null) {
    return `${item.seriesTitle} - S${item.season}E${item.episode}`;
  }
  return item.titlePl;
}

export function computeWatchHistorySummary(progressItems) {
  if (!progressItems || progressItems.length === 0) return null;
  let totalWatchTimeMs = 0;
  let moviesCount = 0;
  let episodesCount = 0;

  for (const item of progressItems) {
    if (item.kind === 'InProgress' || item.kind === 'Watched') {
      totalWatchTimeMs += Number(item.progressMs) || 0;
    }
    if (item.season != null) episodesCount++;
    else moviesCount++;
  }

  return {
    totalWatchTimeMs,
    moviesCount,
    episodesCount,
  };
}

export function groupWatchHistoryByDate(progressItems) {
  if (!progressItems || progressItems.length === 0) return [];
  const deduped = distinctBySeries(progressItems);
  const groups = new Map();
  for (const item of deduped) {
    const dateLabel = formatWatchDate(item.timestamp || Date.now());
    const url = item.parentUrl || item.url;
    const isTv = item.season != null || String(url).startsWith('show_');
    const movieItem = {
      url,
      titlePl: item.seriesTitle || item.titlePl,
      posterUrl: item.posterUrl,
      isTvShow: isTv,
      detailsRequest: { url, isTvShow: isTv },
    };
    if (!groups.has(dateLabel)) groups.set(dateLabel, []);
    groups.get(dateLabel).push(movieItem);
  }
  return Array.from(groups.entries()).map(([date, items]) => ({ date, items }));
}
