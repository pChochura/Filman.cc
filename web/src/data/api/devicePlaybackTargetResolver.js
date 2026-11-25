import { canonicalUrl, parseMediaRef } from '../models/mediaRef.js';
import { getCachedMediaDetails, getMediaDetails } from './tmdbClient.js';
import { progressStore } from '../local/progressStore.js';

/**
 * Resolves the appropriate target URL, episode URL, progressMs, and title
 * to play on remote devices (such as Android TV).
 *
 * For movies: returns { url: movieUrl, episodeUrl: null, seriesUrl: null, progressMs, title }
 * For episodes: returns { url: episodeUrl, episodeUrl, seriesUrl, progressMs, title }
 * For TV shows: returns { url: episodeUrl, episodeUrl, seriesUrl, progressMs, title },
 * resolving the currently watched (or next) episode, or using the explicit watchButtonUrl.
 */
export async function resolvePlaybackTarget(
  movie,
  { detailedMedia = null, progressItems = null, watchButtonUrl = null } = {},
) {
  if (!movie) return null;

  const items = progressItems || progressStore.get() || [];

  // 1. If explicit watchButtonUrl is provided, it is the EXACT episode/movie that the local watch button plays
  if (watchButtonUrl) {
    const isDirectEp = watchButtonUrl !== movie.url && !watchButtonUrl.startsWith('movie_');
    const seriesUrl = isDirectEp
      ? (movie.seriesUrl || canonicalUrl(movie.url, true))
      : null;
    const progressMs =
      typeof movie.progressMs === 'number' && movie.progressMs > 0
        ? movie.progressMs
        : items.find((p) => p.url === watchButtonUrl)?.progressMs || 0;
    const title = movie.titlePl || movie.seriesTitle || '';
    return {
      url: watchButtonUrl,
      episodeUrl: isDirectEp ? watchButtonUrl : null,
      seriesUrl,
      progressMs: Math.round(progressMs),
      title,
    };
  }

  // 2. Check if the item is explicitly an episode
  const isDirectEpisode = Boolean(movie.seriesUrl && movie.url && movie.url !== movie.seriesUrl);
  if (isDirectEpisode) {
    const seriesUrl = canonicalUrl(movie.seriesUrl, true);
    const episodeUrl = movie.url;
    const directProgress =
      typeof movie.progressMs === 'number' && movie.progressMs > 0
        ? movie.progressMs
        : items.find((p) => p.url === episodeUrl)?.progressMs || 0;
    const title = movie.titlePl || movie.seriesTitle || '';
    return {
      url: episodeUrl,
      episodeUrl,
      seriesUrl,
      progressMs: Math.round(directProgress),
      title,
    };
  }

  // 3. Check if the item is a TV show (series)
  const mediaRef = parseMediaRef(movie.url, movie.isTvShow);
  const isTvShow = Boolean(movie.isTvShow || (mediaRef && mediaRef.isTvShow) || movie.url?.startsWith('show_'));

  if (!isTvShow) {
    // It's a Movie
    const targetUrl = canonicalUrl(movie.url, false);
    const movieProgress =
      typeof movie.progressMs === 'number' && movie.progressMs > 0
        ? movie.progressMs
        : items.find((p) => p.url === targetUrl)?.progressMs || 0;
    const title = movie.titlePl || '';
    return {
      url: targetUrl,
      episodeUrl: null,
      seriesUrl: null,
      progressMs: Math.round(movieProgress),
      title,
    };
  }

  // 4. It's a TV show (series) -> resolve current or next episode
  const seriesUrl = canonicalUrl(movie.url, true);

  // Check progress history for this TV show
  const showProgressItems = items.filter(
    (p) => p.parentUrl && canonicalUrl(p.parentUrl, true) === seriesUrl && p.url !== seriesUrl,
  );

  const mostRecent = showProgressItems[0] || null;

  // If the most recent episode is currently in progress, play it directly with its saved progress
  if (mostRecent && mostRecent.kind === 'InProgress') {
    return {
      url: mostRecent.url,
      episodeUrl: mostRecent.url,
      seriesUrl,
      progressMs: Math.round(Number(mostRecent.progressMs) || 0),
      title: movie.titlePl || mostRecent.seriesTitle || mostRecent.titlePl || '',
    };
  }

  // To find the next episode (if mostRecent is Watched) or the first episode (if unwatched):
  // We need the episode list from seasons
  let seasons = null;
  if (detailedMedia?.baseItem?.url === seriesUrl && Array.isArray(detailedMedia?.baseItem?.seasons)) {
    seasons = detailedMedia.baseItem.seasons;
  } else {
    const cached = getCachedMediaDetails(movie);
    if (cached?.baseItem?.seasons) {
      seasons = cached.baseItem.seasons;
    } else {
      try {
        const fetched = await getMediaDetails(movie);
        if (fetched?.baseItem?.seasons) {
          seasons = fetched.baseItem.seasons;
        }
      } catch (err) {
        console.warn('[targetResolver] Failed to load seasons for TV show:', err);
      }
    }
  }

  const flatEpisodes = [];
  for (const s of seasons || []) {
    for (const ep of s.episodes || []) {
      flatEpisodes.push(ep);
    }
  }

  if (flatEpisodes.length > 0) {
    if (mostRecent && (mostRecent.kind === 'Watched' || mostRecent.kind === 'NextEpisode')) {
      const idx = flatEpisodes.findIndex((e) => canonicalUrl(e.url, true) === canonicalUrl(mostRecent.url, true));
      if (idx !== -1 && idx + 1 < flatEpisodes.length) {
        const nextEp = flatEpisodes[idx + 1];
        return {
          url: nextEp.url,
          episodeUrl: nextEp.url,
          seriesUrl,
          progressMs: 0,
          title: movie.titlePl || nextEp.seriesTitle || '',
        };
      }
    }

    // Default to the first episode if unwatched or all watched
    const firstEp = flatEpisodes[0];
    return {
      url: firstEp.url,
      episodeUrl: firstEp.url,
      seriesUrl,
      progressMs: 0,
      title: movie.titlePl || firstEp.seriesTitle || '',
    };
  }

  // Fallback if seasons couldn't be loaded
  return {
    url: mostRecent?.url || seriesUrl,
    episodeUrl: mostRecent?.url || null,
    seriesUrl,
    progressMs: Math.round(Number(mostRecent?.progressMs) || 0),
    title: movie.titlePl || '',
  };
}
