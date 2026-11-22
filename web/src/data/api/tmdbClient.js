import {
  countryCode,
  getKidsCertification,
  languageCode,
  languageTag,
  mapGenreIdsForDiscover,
} from '../../i18n/locale.js';
import { getSortOption, getDecadeOption } from '../models/filterState.js';
import { formatDuration } from '../../i18n/index.js';
import { makeEpisodeKey, makeMovieKey, makeShowKey, parseMediaRef } from '../models/mediaRef.js';

export let TMDB_API_KEY =
  (typeof window !== 'undefined' && window.__ENV__ && window.__ENV__.VITE_TMDB_API_KEY) ||
  (typeof import.meta !== 'undefined' && import.meta.env && import.meta.env.VITE_TMDB_API_KEY) ||
  (typeof process !== 'undefined' && process.env && (process.env.VITE_TMDB_API_KEY || process.env.TMDB_API_KEY)) ||
  '';

export function getTmdbApiKey() {
  const key =
    (typeof window !== 'undefined' && window.__ENV__ && window.__ENV__.VITE_TMDB_API_KEY) ||
    (typeof import.meta !== 'undefined' && import.meta.env && import.meta.env.VITE_TMDB_API_KEY) ||
    (typeof process !== 'undefined' && process.env && (process.env.VITE_TMDB_API_KEY || process.env.TMDB_API_KEY)) ||
    '';
  if (key) {
    TMDB_API_KEY = key;
  }
  return key;
}
const BASE_URL = 'https://api.themoviedb.org/3';
export const POSTER_BASE_URL = 'https://image.tmdb.org/t/p/w500';
export const BACKDROP_BASE_URL = 'https://image.tmdb.org/t/p/w1280';
export const PROFILE_BASE_URL = 'https://image.tmdb.org/t/p/w342';

export const TmdbFeed = {
  TRENDING_ALL_DAY: 'TRENDING_ALL_DAY',
  MOVIES_NOW_PLAYING: 'MOVIES_NOW_PLAYING',
  MOVIES_POPULAR: 'MOVIES_POPULAR',
  MOVIES_TOP_RATED: 'MOVIES_TOP_RATED',
  MOVIES_NEW_RELEASES: 'MOVIES_NEW_RELEASES',
  TRENDING_TV_WEEK: 'TRENDING_TV_WEEK',
  TV_ON_THE_AIR: 'TV_ON_THE_AIR',
  TV_POPULAR: 'TV_POPULAR',
  TV_TOP_RATED: 'TV_TOP_RATED',
  TV_NEW_RELEASES: 'TV_NEW_RELEASES',
  KIDS_POPULAR_MOVIES: 'KIDS_POPULAR_MOVIES',
  KIDS_TV_SHOWS: 'KIDS_TV_SHOWS',
  KIDS_TOP_RATED_FAMILY: 'KIDS_TOP_RATED_FAMILY',
  KIDS_DISNEY_PIXAR: 'KIDS_DISNEY_PIXAR',
  KIDS_DREAMWORKS_GHIBLI: 'KIDS_DREAMWORKS_GHIBLI',
  DISCOVER_BY_GENRE: 'DISCOVER_BY_GENRE',
  DISCOVER_FILTERED: 'DISCOVER_FILTERED',
  RECOMMENDATIONS: 'RECOMMENDATIONS',
  PERSONALIZED_FOR_YOU: 'PERSONALIZED_FOR_YOU',
};

const sparseKidsCountries = new Set();
let cachedMovieCategories = null;
let cachedTvCategories = null;

function todayIsoDate() {
  return new Date().toISOString().slice(0, 10);
}

function mapSummaryToMovieItem(raw, defaultMediaType = null) {
  if (!raw || raw.media_type === 'person') return null;
  if (!raw.poster_path) return null;

  const rawType = raw.media_type;
  const isTv =
    rawType === 'tv' ||
    (rawType !== 'movie' &&
      (defaultMediaType === 'TV_SHOW' ||
        (defaultMediaType === null && Boolean(raw.name) && !raw.title)));

  const itemUrl = isTv ? makeShowKey(raw.id) : makeMovieKey(raw.id);
  const title = raw.title || raw.name || '';
  const originalTitle = raw.original_title || raw.original_name || null;
  const releaseDate = raw.release_date || raw.first_air_date || null;
  const year = releaseDate ? Number.parseInt(releaseDate.slice(0, 4), 10) : null;
  const voteAvg = typeof raw.vote_average === 'number' && raw.vote_average > 0 ? raw.vote_average : null;

  return {
    url: itemUrl,
    detailsRequest: { url: itemUrl, isTvShow: isTv },
    titlePl: title,
    titleEn: originalTitle,
    posterUrl: `${POSTER_BASE_URL}${raw.poster_path}`,
    backgroundUrl: raw.backdrop_path ? `${BACKDROP_BASE_URL}${raw.backdrop_path}` : '',
    description: raw.overview || '',
    year: Number.isFinite(year) ? year : null,
    tiwiRating: voteAvg,
    filmanRating: voteAvg,
    imdbRating: voteAvg,
    isTvShow: isTv,
    genreIds: Array.isArray(raw.genre_ids) ? raw.genre_ids : [],
    voteCount: typeof raw.vote_count === 'number' ? raw.vote_count : null,
    originalLanguage: raw.original_language || null,
  };
}

function buildDiscoverFilteredUrl(feedSpec, page, lang, country) {
  const isTv = feedSpec.mediaType === 'TV';
  const endpoint = isTv ? 'tv' : 'movie';
  const dateParam = isTv ? 'first_air_date' : 'primary_release_date';
  const filter = feedSpec.filter || {};
  const sortOption = getSortOption(filter.sortBy);
  const decadeOption = getDecadeOption(filter.decade);

  const params = new URLSearchParams();
  params.set('api_key', TMDB_API_KEY);
  params.set('page', String(page));
  params.set('language', lang);
  if (!isTv) params.set('region', country);
  params.set('include_adult', 'false');
  params.set('sort_by', isTv ? sortOption.tvApiValue : sortOption.movieApiValue);

  if (Array.isArray(filter.selectedGenreIds) && filter.selectedGenreIds.length > 0) {
    const genres = mapGenreIdsForDiscover(filter.selectedGenreIds, isTv);
    if (genres) params.set('with_genres', genres);
  }
  if (decadeOption.fromYear !== null) {
    params.set(`${dateParam}.gte`, `${decadeOption.fromYear}-01-01`);
  }
  const toDate =
    decadeOption.toYear !== null
      ? `${decadeOption.toYear}-12-31`
      : sortOption.id === 'RELEASE_DATE_DESC'
        ? todayIsoDate()
        : null;
  if (toDate) params.set(`${dateParam}.lte`, toDate);

  if (filter.minRating !== null && filter.minRating !== undefined) {
    params.set('vote_average.gte', String(filter.minRating));
  }
  if (sortOption.id === 'RATING_DESC' || filter.minRating !== null) {
    params.set('vote_count.gte', '50');
  } else if (sortOption.id === 'RELEASE_DATE_DESC') {
    params.set('vote_count.gte', '20');
  }

  return `${BASE_URL}/discover/${endpoint}?${params.toString()}`;
}

function kidsExtraParams(feedType) {
  switch (feedType) {
    case TmdbFeed.KIDS_POPULAR_MOVIES:
      return 'with_genres=16|10751&sort_by=popularity.desc';
    case TmdbFeed.KIDS_TOP_RATED_FAMILY:
      return 'with_genres=10751&sort_by=vote_average.desc&vote_count.gte=300';
    case TmdbFeed.KIDS_DISNEY_PIXAR:
      return 'with_companies=2|3&sort_by=popularity.desc';
    case TmdbFeed.KIDS_DREAMWORKS_GHIBLI:
      return 'with_companies=521|10342&sort_by=popularity.desc';
    default:
      return null;
  }
}

function buildFeedUrl(feedSpec, page, lang, country) {
  const feedType = typeof feedSpec === 'string' ? feedSpec : feedSpec.type;
  const today = todayIsoDate();

  switch (feedType) {
    case TmdbFeed.TRENDING_ALL_DAY:
      return {
        url: `${BASE_URL}/trending/all/day?api_key=${TMDB_API_KEY}&page=${page}&language=${lang}&include_adult=false`,
        defaultType: null,
      };
    case TmdbFeed.TRENDING_TV_WEEK:
      return {
        url: `${BASE_URL}/trending/tv/week?api_key=${TMDB_API_KEY}&page=${page}&language=${lang}&include_adult=false`,
        defaultType: 'TV_SHOW',
      };
    case TmdbFeed.MOVIES_NOW_PLAYING:
      return {
        url: `${BASE_URL}/movie/now_playing?api_key=${TMDB_API_KEY}&page=${page}&language=${lang}&region=${country}`,
        defaultType: 'MOVIE',
      };
    case TmdbFeed.MOVIES_POPULAR:
      return {
        url: `${BASE_URL}/movie/popular?api_key=${TMDB_API_KEY}&page=${page}&language=${lang}&region=${country}`,
        defaultType: 'MOVIE',
      };
    case TmdbFeed.MOVIES_TOP_RATED:
      return {
        url: `${BASE_URL}/movie/top_rated?api_key=${TMDB_API_KEY}&page=${page}&language=${lang}&region=${country}`,
        defaultType: 'MOVIE',
      };
    case TmdbFeed.MOVIES_NEW_RELEASES:
      return {
        url: `${BASE_URL}/discover/movie?api_key=${TMDB_API_KEY}&page=${page}&language=${lang}&region=${country}&include_adult=false&sort_by=primary_release_date.desc&vote_count.gte=30&primary_release_date.lte=${today}`,
        defaultType: 'MOVIE',
      };
    case TmdbFeed.TV_ON_THE_AIR:
      return {
        url: `${BASE_URL}/tv/on_the_air?api_key=${TMDB_API_KEY}&page=${page}&language=${lang}`,
        defaultType: 'TV_SHOW',
      };
    case TmdbFeed.TV_POPULAR:
      return {
        url: `${BASE_URL}/tv/popular?api_key=${TMDB_API_KEY}&page=${page}&language=${lang}`,
        defaultType: 'TV_SHOW',
      };
    case TmdbFeed.TV_TOP_RATED:
      return {
        url: `${BASE_URL}/tv/top_rated?api_key=${TMDB_API_KEY}&page=${page}&language=${lang}`,
        defaultType: 'TV_SHOW',
      };
    case TmdbFeed.TV_NEW_RELEASES:
      return {
        url: `${BASE_URL}/discover/tv?api_key=${TMDB_API_KEY}&page=${page}&language=${lang}&include_adult=false&sort_by=first_air_date.desc&vote_count.gte=20&first_air_date.lte=${today}`,
        defaultType: 'TV_SHOW',
      };
    case TmdbFeed.KIDS_TV_SHOWS:
      return {
        url: `${BASE_URL}/discover/tv?api_key=${TMDB_API_KEY}&page=${page}&language=${lang}&include_adult=false&with_genres=10762&sort_by=popularity.desc`,
        defaultType: 'TV_SHOW',
      };
    case TmdbFeed.DISCOVER_BY_GENRE: {
      const isTv = feedSpec.mediaType === 'TV';
      const genres = mapGenreIdsForDiscover([feedSpec.genreId], isTv);
      return {
        url: `${BASE_URL}/discover/${isTv ? 'tv' : 'movie'}?api_key=${TMDB_API_KEY}&with_genres=${genres}&page=${page}&language=${lang}&include_adult=false&sort_by=popularity.desc`,
        defaultType: isTv ? 'TV_SHOW' : 'MOVIE',
      };
    }
    case TmdbFeed.DISCOVER_FILTERED:
      return {
        url: buildDiscoverFilteredUrl(feedSpec, page, lang, country),
        defaultType: feedSpec.mediaType === 'TV' ? 'TV_SHOW' : 'MOVIE',
      };
    case TmdbFeed.RECOMMENDATIONS: {
      const ref = feedSpec.ref;
      const isTv = Boolean(ref?.isTvShow);
      return {
        url: `${BASE_URL}/${isTv ? 'tv' : 'movie'}/${ref.tmdbId}/recommendations?api_key=${TMDB_API_KEY}&page=${page}&language=${lang}`,
        defaultType: isTv ? 'TV_SHOW' : 'MOVIE',
      };
    }
    default:
      return null;
  }
}

async function fetchJson(url) {
  const currentKey = getTmdbApiKey();
  if (!currentKey) {
    throw new Error('TMDB_API_KEY is not configured');
  }
  let targetUrl = url;
  if (targetUrl.includes('api_key=') && !targetUrl.includes(`api_key=${currentKey}`)) {
    targetUrl = targetUrl.replace(/api_key=[^&]*/, `api_key=${currentKey}`);
  }
  const res = await fetch(targetUrl);
  if (!res.ok) {
    throw new Error(`TMDB HTTP ${res.status}`);
  }
  return res.json();
}

export async function fetchFeedPage(feedSpec, page = 1) {
  const feedType = typeof feedSpec === 'string' ? feedSpec : feedSpec.type;
  const lang = languageTag();
  const country = countryCode();

  try {
    const kidsExtra = kidsExtraParams(feedType);
    if (kidsExtra) {
      let cert = getKidsCertification(country);
      if (sparseKidsCountries.has(cert.country)) {
        cert = { country: 'US', certificationLte: 'PG' };
      }
      const makeKidsUrl = (c) =>
        `${BASE_URL}/discover/movie?api_key=${TMDB_API_KEY}&page=${page}&language=${lang}&include_adult=false&${kidsExtra}&certification_country=${c.country}&certification.lte=${encodeURIComponent(c.certificationLte)}`;

      let data = await fetchJson(makeKidsUrl(cert));
      let results = (data.results || [])
        .map((r) => mapSummaryToMovieItem(r, 'MOVIE'))
        .filter(Boolean);

      if (page === 1 && cert.country !== 'US' && results.length < 5) {
        sparseKidsCountries.add(cert.country);
        const fallback = await fetchJson(makeKidsUrl({ country: 'US', certificationLte: 'PG' }));
        const fallbackResults = (fallback.results || [])
          .map((r) => mapSummaryToMovieItem(r, 'MOVIE'))
          .filter(Boolean);
        if (fallbackResults.length > results.length) {
          data = fallback;
          results = fallbackResults;
        }
      }

      const featured = results.filter((m) => Boolean(m.backgroundUrl));
      return {
        movies: results,
        featuredItems: (featured.length > 0 ? featured : results).slice(0, 5),
        hasMore: (data.page || page) < (data.total_pages || 1) && results.length > 0,
        page: data.page || page,
        totalPages: data.total_pages || 1,
        errorMessage: null,
      };
    }

    const built = buildFeedUrl(feedSpec, page, lang, country);
    if (!built) {
      return { movies: [], featuredItems: [], hasMore: false, page, totalPages: 1, errorMessage: null };
    }

    const data = await fetchJson(built.url);
    const movies = (data.results || [])
      .map((r) => mapSummaryToMovieItem(r, built.defaultType))
      .filter(Boolean);
    const featured = movies.filter((m) => Boolean(m.backgroundUrl));
    const currentPage = data.page || page;
    const totalPages = data.total_pages || 1;

    return {
      movies,
      featuredItems: (featured.length > 0 ? featured : movies).slice(0, 5),
      hasMore: currentPage < totalPages && movies.length > 0,
      page: currentPage,
      totalPages,
      errorMessage: null,
    };
  } catch (err) {
    return {
      movies: [],
      featuredItems: [],
      hasMore: false,
      page,
      totalPages: 1,
      errorMessage: err?.message || 'Unknown error',
    };
  }
}

export async function loadFeedCatalog(specs) {
  const entries = await Promise.all(
    specs.map(async (spec) => {
      const result = await fetchFeedPage(spec.feed, 1);
      return { spec, result };
    }),
  );

  const featuredSeen = new Set();
  const featuredItems = [];
  for (const { spec, result } of entries) {
    if (!spec.includeInFeatured) continue;
    for (const item of result.featuredItems) {
      if (!featuredSeen.has(item.url)) {
        featuredSeen.add(item.url);
        featuredItems.push(item);
        if (featuredItems.length >= 5) break;
      }
    }
    if (featuredItems.length >= 5) break;
  }

  return {
    entries,
    featuredItems,
  };
}

export async function getMovieCategories() {
  if (cachedMovieCategories) return cachedMovieCategories;
  const lang = languageTag();
  try {
    const data = await fetchJson(`${BASE_URL}/genre/movie/list?api_key=${TMDB_API_KEY}&language=${lang}`);
    cachedMovieCategories = (data.genres || []).map((g) => ({
      id: g.id,
      name: g.name,
      url: String(g.id),
    }));
    return cachedMovieCategories;
  } catch {
    return [];
  }
}

export async function getTvCategories() {
  if (cachedTvCategories) return cachedTvCategories;
  const lang = languageTag();
  try {
    const data = await fetchJson(`${BASE_URL}/genre/tv/list?api_key=${TMDB_API_KEY}&language=${lang}`);
    cachedTvCategories = (data.genres || []).map((g) => ({
      id: g.id,
      name: g.name,
      url: String(g.id),
    }));
    return cachedTvCategories;
  } catch {
    return [];
  }
}

export async function getMergedCategories() {
  const [movieCats, tvCats] = await Promise.all([getMovieCategories(), getTvCategories()]);
  const seen = new Set();
  const merged = [];
  for (const cat of [...movieCats, ...tvCats]) {
    if (!seen.has(cat.id)) {
      seen.add(cat.id);
      merged.push(cat);
    }
  }
  return merged;
}

export async function searchMulti(query, page = 1) {
  const trimmed = String(query || '').trim();
  if (!trimmed) return { movies: [], tvShows: [], errorMessage: null };
  const lang = languageTag();
  try {
    const data = await fetchJson(
      `${BASE_URL}/search/multi?api_key=${TMDB_API_KEY}&query=${encodeURIComponent(trimmed)}&page=${page}&language=${lang}&include_adult=false`,
    );
    const items = (data.results || [])
      .map((r) => mapSummaryToMovieItem(r, null))
      .filter(Boolean);
    return {
      movies: items.filter((m) => !m.isTvShow),
      tvShows: items.filter((m) => m.isTvShow),
      errorMessage: null,
    };
  } catch (err) {
    return {
      movies: [],
      tvShows: [],
      errorMessage: err?.message || 'Search failed',
    };
  }
}

function extractTrailerUrl(videosResult, preferredLang) {
  const list = (videosResult?.results || []).filter(
    (v) => v.site === 'YouTube' && typeof v.key === 'string' && v.key.trim().length > 0,
  );
  if (list.length === 0) return null;
  const chosen =
    list.find((v) => v.type === 'Trailer' && v.iso_639_1 === preferredLang) ||
    list.find((v) => v.type === 'Trailer') ||
    list[0];
  return `https://www.youtube.com/watch?v=${chosen.key}`;
}

function mapCreditsToActors(raw, isTv) {
  const seen = new Set();
  const actors = [];
  const pushActor = (id, name, profilePath, role) => {
    const key = `${role}_${id}`;
    if (seen.has(key) || !name) return;
    seen.add(key);
    actors.push({
      id,
      role,
      name,
      avatarUrl: profilePath ? `${PROFILE_BASE_URL}${profilePath}` : null,
      url: String(id),
    });
  };

  if (isTv && Array.isArray(raw.created_by)) {
    for (const c of raw.created_by) {
      pushActor(c.id, c.name, c.profile_path, 'WRITER');
    }
  }

  const writingJobs = new Set(['Writer', 'Screenplay', 'Story', 'Creator', 'Novel']);
  for (const crew of raw.credits?.crew || []) {
    if (crew.job === 'Director') {
      pushActor(crew.id, crew.name, crew.profile_path, 'DIRECTOR');
    } else if (crew.department === 'Writing' || writingJobs.has(crew.job)) {
      pushActor(crew.id, crew.name, crew.profile_path, 'WRITER');
    }
  }

  for (const member of raw.credits?.cast || []) {
    pushActor(member.id, member.name, member.profile_path, 'ACTOR');
  }
  return actors;
}

const mediaDetailsCache = new Map();
const mediaDetailsInFlight = new Map();
const MAX_DETAILS_CACHE_SIZE = 150;

function buildDetailsCacheKey(ref) {
  const lang = languageTag();
  return `${ref.canonicalKey}_${ref.isTvShow ? 'tv' : 'movie'}_${lang}`;
}

export function getCachedMediaDetails(rawRequest) {
  const ref =
    typeof rawRequest === 'string'
      ? parseMediaRef(rawRequest)
      : parseMediaRef(rawRequest?.url, rawRequest?.isTvShow);
  if (!ref) return null;
  const cacheKey = buildDetailsCacheKey(ref);
  return mediaDetailsCache.get(cacheKey) || null;
}

export function clearMediaDetailsCache() {
  mediaDetailsCache.clear();
  mediaDetailsInFlight.clear();
}

async function fetchMediaDetailsDirect(ref) {
  const lang = languageTag();
  const langCode = languageCode();
  const videoLangs = [...new Set([langCode, 'en', 'null'])].join(',');
  const append = 'credits,recommendations,similar,videos,external_ids';

  if (!ref.isTvShow) {
    const data = await fetchJson(
      `${BASE_URL}/movie/${ref.tmdbId}?api_key=${TMDB_API_KEY}&language=${lang}&append_to_response=${append}&include_video_language=${encodeURIComponent(videoLangs)}`,
    );
    const voteAvg = typeof data.vote_average === 'number' && data.vote_average > 0 ? data.vote_average : null;
    const year = data.release_date ? Number.parseInt(data.release_date.slice(0, 4), 10) : null;
    return {
      tmdbId: data.id,
      imdbId: data.external_ids?.imdb_id || data.imdb_id || null,
      baseItem: {
        url: makeMovieKey(data.id),
        titlePl: data.title || '',
        titleEn: data.original_title || null,
        posterUrl: data.poster_path ? `${POSTER_BASE_URL}${data.poster_path}` : '',
        backgroundUrl: data.backdrop_path ? `${BACKDROP_BASE_URL}${data.backdrop_path}` : '',
        description: data.overview || '',
        year: Number.isFinite(year) ? year : null,
        tiwiRating: voteAvg,
        filmanRating: voteAvg,
        imdbRating: voteAvg,
        isTvShow: false,
        genreIds: (data.genres || []).map((g) => g.id),
        originalLanguage: data.original_language || null,
      },
      actors: mapCreditsToActors(data, false),
      categories: (data.genres || []).map((g) => ({ id: g.id, name: g.name, url: String(g.id) })),
      metaInfo: {
        year: Number.isFinite(year) ? year : null,
        duration: formatDuration(data.runtime),
        runtimeMinutes: data.runtime || null,
        countries: (data.production_countries || []).map((c) => c.name).filter(Boolean),
      },
      similarMovies: (data.similar?.results || [])
        .map((r) => mapSummaryToMovieItem(r, 'MOVIE'))
        .filter(Boolean),
      recommendations: (data.recommendations?.results || [])
        .map((r) => mapSummaryToMovieItem(r, 'MOVIE'))
        .filter(Boolean),
      recommendationsTotalPages: data.recommendations?.total_pages || 1,
      trailerUrl: extractTrailerUrl(data.videos, langCode),
    };
  }

  const tvData = await fetchJson(
    `${BASE_URL}/tv/${ref.tmdbId}?api_key=${TMDB_API_KEY}&language=${lang}&append_to_response=${append}&include_video_language=${encodeURIComponent(videoLangs)}`,
  );

  const validSeasons = (tvData.seasons || []).filter((s) => s.season_number > 0);
  const seasonDetailsList = await Promise.all(
    validSeasons.map((s) =>
      fetchJson(
        `${BASE_URL}/tv/${ref.tmdbId}/season/${s.season_number}?api_key=${TMDB_API_KEY}&language=${lang}`,
      ).catch(() => ({ season_number: s.season_number, name: s.name, episodes: [] })),
    ),
  );

  const mappedSeasons = seasonDetailsList.map((s) => ({
    seasonNumber: s.season_number,
    name: s.name || `Season ${s.season_number}`,
    episodes: (s.episodes || []).map((ep) => ({
      title: ep.name || `Episode ${ep.episode_number}`,
      overview: ep.overview || '',
      stillPath: ep.still_path || null,
      seasonNumber: s.season_number,
      episodeNumber: ep.episode_number,
      url: makeEpisodeKey(tvData.id, s.season_number, ep.episode_number),
    })),
  }));

  let currentEpisodeTitle = null;
  let nextEpisodeUrl = null;
  let prevEpisodeUrl = null;

  if (ref.season !== null && ref.episode !== null) {
    const sIdx = mappedSeasons.findIndex((s) => s.seasonNumber === ref.season);
    const epList = mappedSeasons[sIdx]?.episodes || [];
    const eIdx = epList.findIndex((ep) => ep.episodeNumber === ref.episode);
    if (eIdx !== -1) {
      currentEpisodeTitle = epList[eIdx].title;
      const nextEp = epList[eIdx + 1] || mappedSeasons[sIdx + 1]?.episodes?.[0] || null;
      nextEpisodeUrl = nextEp?.url || null;
      const prevEp =
        eIdx > 0
          ? epList[eIdx - 1]
          : mappedSeasons[sIdx - 1]?.episodes?.[mappedSeasons[sIdx - 1].episodes.length - 1] || null;
      prevEpisodeUrl = prevEp?.url || null;
    }
  }

  const canonicalUrl =
    ref.season !== null && ref.episode !== null
      ? makeEpisodeKey(tvData.id, ref.season, ref.episode)
      : makeShowKey(tvData.id);
  const voteAvg = typeof tvData.vote_average === 'number' && tvData.vote_average > 0 ? tvData.vote_average : null;
  const year = tvData.first_air_date ? Number.parseInt(tvData.first_air_date.slice(0, 4), 10) : null;
  const runtimeMin =
    (Array.isArray(tvData.episode_run_time) && tvData.episode_run_time[0]) ||
    tvData.last_episode_to_air?.runtime ||
    null;

  return {
    tmdbId: tvData.id,
    imdbId: tvData.external_ids?.imdb_id || null,
    baseItem: {
      url: canonicalUrl,
      seriesUrl: makeShowKey(tvData.id),
      seasonNumber: ref.season,
      episodeNumber: ref.episode,
      seriesTitle: tvData.name || '',
      episodeTitle: currentEpisodeTitle,
      nextEpisodeUrl,
      prevEpisodeUrl,
      titlePl: currentEpisodeTitle ? `${tvData.name} - S${ref.season}E${ref.episode}` : tvData.name || '',
      titleEn: tvData.original_name || null,
      posterUrl: tvData.poster_path ? `${POSTER_BASE_URL}${tvData.poster_path}` : '',
      backgroundUrl: tvData.backdrop_path ? `${BACKDROP_BASE_URL}${tvData.backdrop_path}` : '',
      description: tvData.overview || '',
      year: Number.isFinite(year) ? year : null,
      tiwiRating: voteAvg,
      filmanRating: voteAvg,
      imdbRating: voteAvg,
      isTvShow: true,
      seasons: mappedSeasons,
      genreIds: (tvData.genres || []).map((g) => g.id),
      originalLanguage: tvData.original_language || null,
    },
    actors: mapCreditsToActors(tvData, true),
    categories: (tvData.genres || []).map((g) => ({ id: g.id, name: g.name, url: String(g.id) })),
    metaInfo: {
      year: Number.isFinite(year) ? year : null,
      duration: formatDuration(runtimeMin),
      runtimeMinutes: runtimeMin,
      countries: Array.isArray(tvData.origin_country) ? tvData.origin_country : [],
    },
    similarMovies: (tvData.similar?.results || [])
      .map((r) => mapSummaryToMovieItem(r, 'TV_SHOW'))
      .filter(Boolean),
    recommendations: (tvData.recommendations?.results || [])
      .map((r) => mapSummaryToMovieItem(r, 'TV_SHOW'))
      .filter(Boolean),
    recommendationsTotalPages: tvData.recommendations?.total_pages || 1,
    trailerUrl: extractTrailerUrl(tvData.videos, langCode),
  };
}

export async function getMediaDetails(rawRequest) {
  const ref =
    typeof rawRequest === 'string'
      ? parseMediaRef(rawRequest)
      : parseMediaRef(rawRequest?.url, rawRequest?.isTvShow);
  if (!ref) return null;

  const cacheKey = buildDetailsCacheKey(ref);
  if (mediaDetailsCache.has(cacheKey)) {
    return mediaDetailsCache.get(cacheKey);
  }
  if (mediaDetailsInFlight.has(cacheKey)) {
    return mediaDetailsInFlight.get(cacheKey);
  }

  const fetchPromise = (async () => {
    try {
      const result = await fetchMediaDetailsDirect(ref);
      if (result) {
        if (mediaDetailsCache.size >= MAX_DETAILS_CACHE_SIZE) {
          const oldestKey = mediaDetailsCache.keys().next().value;
          mediaDetailsCache.delete(oldestKey);
        }
        mediaDetailsCache.set(cacheKey, result);
      }
      return result;
    } finally {
      mediaDetailsInFlight.delete(cacheKey);
    }
  })();

  mediaDetailsInFlight.set(cacheKey, fetchPromise);
  return fetchPromise;
}

export async function getActorDetails(personId) {
  const id = Number.parseInt(String(personId), 10);
  if (!Number.isFinite(id) || id <= 0) return null;
  const lang = languageTag();
  const data = await fetchJson(
    `${BASE_URL}/person/${id}?api_key=${TMDB_API_KEY}&language=${lang}&append_to_response=combined_credits`,
  );

  const dedupeMap = (items) => {
    const seen = new Set();
    const out = [];
    for (const raw of items) {
      const mapped = mapSummaryToMovieItem(raw, null);
      if (mapped && !seen.has(mapped.url)) {
        seen.add(mapped.url);
        out.push(mapped);
      }
    }
    return out;
  };

  const crew = data.combined_credits?.crew || [];
  const cast = data.combined_credits?.cast || [];
  const writerJobs = new Set(['Writer', 'Screenplay', 'Story', 'Creator', 'Novel']);

  return {
    name: data.name || '',
    avatarUrl: data.profile_path ? `${PROFILE_BASE_URL}${data.profile_path}` : '',
    birthDate: data.birthday || null,
    birthPlace: data.place_of_birth || null,
    description: data.biography || '',
    moviesDirector: dedupeMap(crew.filter((c) => c.job === 'Director')),
    moviesWriter: dedupeMap(
      crew.filter((c) => c.department === 'Writing' || writerJobs.has(c.job)),
    ),
    moviesCast: dedupeMap(cast),
  };
}

export async function getImdbId(ref) {
  if (!ref?.tmdbId) return null;
  try {
    const data = await fetchJson(
      `${BASE_URL}/${ref.isTvShow ? 'tv' : 'movie'}/${ref.tmdbId}/external_ids?api_key=${TMDB_API_KEY}`,
    );
    return data.imdb_id || null;
  } catch {
    return null;
  }
}

export async function getTvSummaryForEpisodeCheck(tmdbId) {
  const lang = languageTag();
  try {
    const data = await fetchJson(`${BASE_URL}/tv/${tmdbId}?api_key=${TMDB_API_KEY}&language=${lang}`);
    const lastEp = data.last_episode_to_air;
    const latestEpisodeKey =
      lastEp?.season_number != null && lastEp?.episode_number != null
        ? makeEpisodeKey(tmdbId, lastEp.season_number, lastEp.episode_number)
        : null;
    return {
      title: data.name || '',
      posterUrl: data.poster_path ? `${POSTER_BASE_URL}${data.poster_path}` : '',
      backgroundUrl: data.backdrop_path ? `${BACKDROP_BASE_URL}${data.backdrop_path}` : '',
      totalEpisodes: Number(data.number_of_episodes) || 0,
      latestEpisodeKey,
    };
  } catch {
    return null;
  }
}
