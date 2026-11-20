import { favoritesStore } from '../local/favoritesStore.js';
import { progressStore } from '../local/progressStore.js';
import { canonicalUrl, parseMediaRef } from '../models/mediaRef.js';
import { TmdbFeed, fetchFeedPage } from './tmdbClient.js';

export async function getPersonalizedRecommendations(page = 1) {
  const favorites = favoritesStore.get();
  const progress = progressStore.get();

  const favRefs = favorites
    .map((m) => parseMediaRef(m.url, m.isTvShow)?.parentKey)
    .filter(Boolean)
    .map((k) => parseMediaRef(k));
  const favSeedKeys = new Set(favRefs.map((r) => r.canonicalKey));

  const progressRefs = progress
    .map((p) => {
      const ref = parseMediaRef(p.parentUrl || p.url, p.season != null || String(p.parentUrl).startsWith('show_'));
      if (!ref) return null;
      return parseMediaRef(ref.parentKey, ref.isTvShow);
    })
    .filter(Boolean);

  const seedMap = new Map();
  for (const ref of [...favRefs.slice(0, 6), ...progressRefs]) {
    if (!seedMap.has(ref.canonicalKey)) {
      seedMap.set(ref.canonicalKey, ref);
      if (seedMap.size >= 8) break;
    }
  }
  const seedRefs = Array.from(seedMap.values());
  if (seedRefs.length === 0) {
    return { movies: [], hasMore: false };
  }

  const excludedKeys = new Set(seedRefs.map((r) => r.canonicalKey));
  for (const p of progress) {
    if (p.url) excludedKeys.add(canonicalUrl(p.url));
    if (p.parentUrl) excludedKeys.add(canonicalUrl(p.parentUrl));
  }

  const seedPages = await Promise.all(
    seedRefs.map(async (ref) => {
      try {
        const res = await fetchFeedPage({ type: TmdbFeed.RECOMMENDATIONS, ref }, page);
        return { ref, res };
      } catch {
        return { ref, res: null };
      }
    }),
  );

  const hasMore = seedPages.some((p) => p.res && p.res.hasMore);
  const scores = new Map();
  const candidatesByKey = new Map();

  for (const { ref, res } of seedPages) {
    if (!res || !Array.isArray(res.movies)) continue;
    const isFavSeed = favSeedKeys.has(ref.canonicalKey);
    const weight = isFavSeed ? 3 : 1;
    for (const item of res.movies) {
      if (!item.posterUrl || excludedKeys.has(item.url)) continue;
      if (!candidatesByKey.has(item.url)) {
        candidatesByKey.set(item.url, item);
      }
      scores.set(item.url, (scores.get(item.url) || 0) + weight);
    }
  }

  if (candidatesByKey.size === 0) {
    return { movies: [], hasMore: false };
  }

  const top = Array.from(candidatesByKey.values())
    .sort((a, b) => (scores.get(b.url) || 0) - (scores.get(a.url) || 0))
    .slice(0, 15);

  return { movies: top, hasMore };
}
