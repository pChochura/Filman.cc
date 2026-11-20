export const MediaType = {
  MOVIE: 'MOVIE',
  TV: 'TV',
};

export function makeMovieKey(tmdbId) {
  return `movie_${tmdbId}`;
}

export function makeShowKey(tmdbId) {
  return `show_${tmdbId}`;
}

export function makeEpisodeKey(tmdbId, season, episode) {
  return `show_${tmdbId}_${season}_${episode}`;
}

export function parseMediaRef(raw, isTvShowHint = null) {
  if (!raw || typeof raw !== 'string') return null;
  const trimmed = raw.trim();
  if (!trimmed) return null;
  if (trimmed.startsWith('http://') || trimmed.startsWith('https://') || trimmed.includes('/')) {
    return null;
  }

  let explicitlyTv = null;
  if (trimmed.startsWith('show_')) explicitlyTv = true;
  else if (trimmed.startsWith('movie_')) explicitlyTv = false;

  const clean = trimmed
    .replace(/^search_/, '')
    .replace(/^show_/, '')
    .replace(/^movie_/, '');
  const parts = clean.split('_');
  const tmdbId = Number.parseInt(parts[0], 10);
  if (!Number.isFinite(tmdbId) || tmdbId <= 0) return null;

  const season = parts[1] !== undefined ? Number.parseInt(parts[1], 10) : null;
  const episode = parts[2] !== undefined ? Number.parseInt(parts[2], 10) : null;
  const validSeason = Number.isFinite(season) ? season : null;
  const validEpisode = Number.isFinite(episode) ? episode : null;

  const isTv =
    explicitlyTv !== null
      ? explicitlyTv
      : isTvShowHint !== null
        ? Boolean(isTvShowHint)
        : validSeason !== null && validEpisode !== null;

  if (isTv) {
    const canonicalKey =
      validSeason !== null && validEpisode !== null
        ? makeEpisodeKey(tmdbId, validSeason, validEpisode)
        : makeShowKey(tmdbId);
    return {
      type: MediaType.TV,
      tmdbId,
      season: validSeason,
      episode: validEpisode,
      isTvShow: true,
      isEpisode: validSeason !== null && validEpisode !== null,
      canonicalKey,
      parentKey: makeShowKey(tmdbId),
    };
  }

  const key = makeMovieKey(tmdbId);
  return {
    type: MediaType.MOVIE,
    tmdbId,
    season: null,
    episode: null,
    isTvShow: false,
    isEpisode: false,
    canonicalKey: key,
    parentKey: key,
  };
}

export function canonicalUrl(url, isTvHint = null) {
  return parseMediaRef(url, isTvHint)?.canonicalKey ?? url;
}

export function getParentKey(url, isTvHint = null) {
  return parseMediaRef(url, isTvHint)?.parentKey ?? url;
}

export function getTvShowKey(item) {
  if (!item) return null;
  if (item.seriesUrl) return parseMediaRef(item.seriesUrl, true)?.parentKey ?? item.seriesUrl;
  const ref = parseMediaRef(item.url, item.isTvShow);
  if (!ref || !ref.isTvShow) return null;
  return ref.parentKey;
}

export function getMediaKey(item) {
  if (!item) return null;
  const showKey = getTvShowKey(item);
  if (showKey) return showKey;
  const ref = parseMediaRef(item.url, item.isTvShow);
  return ref ? ref.parentKey : null;
}

export function sanitizeMovieItemToTmdb(item) {
  if (!item) return null;
  const ref = parseMediaRef(item.url, item.isTvShow);
  if (!ref) return null;
  return {
    ...item,
    url: ref.canonicalKey,
    isTvShow: ref.isTvShow,
    seasonNumber: item.seasonNumber ?? ref.season ?? null,
    episodeNumber: item.episodeNumber ?? ref.episode ?? null,
    seriesUrl: item.seriesUrl
      ? (parseMediaRef(item.seriesUrl, true)?.parentKey ?? ref.parentKey)
      : ref.isTvShow
        ? ref.parentKey
        : null,
  };
}
