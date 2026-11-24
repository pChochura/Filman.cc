import test from 'node:test';
import assert from 'node:assert/strict';

// Minimal in-memory localStorage stub before importing stores
const memoryStorage = new Map();
if (typeof globalThis.localStorage === 'undefined') {
  globalThis.localStorage = {
    getItem(k) {
      return memoryStorage.has(k) ? memoryStorage.get(k) : null;
    },
    setItem(k, v) {
      memoryStorage.set(k, String(v));
    },
    removeItem(k) {
      memoryStorage.delete(k);
    },
  };
}

const {
  makeMovieKey,
  makeShowKey,
  makeEpisodeKey,
  parseMediaRef,
  canonicalUrl,
  getTvShowKey,
  getMediaKey,
} = await import('../src/data/models/mediaRef.js');

const {
  classifyContent,
  ContentCategory,
  AudioPreferenceMode,
  DEFAULT_CONTENT_RULES,
} = await import('../src/data/models/contentClassification.js');

const {
  ACTIVE_WATCH_WINDOW_MS,
  filterUnwatchedWatchlist,
  groupWatchHistoryByDate,
  isSampled,
  toActiveContinueWatchingItems,
  toJumpBackInItems,
} = await import('../src/data/local/continueWatchingMapper.js');

const { extractVideoSignature } = await import('../src/data/api/videoSignatureExtractor.js');
const {
  calculateCandidateScore,
  rankOpenSubtitleCandidates,
} = await import('../src/data/api/subtitleRanking.js');

const {
  extractYoutubeVideoId,
  findBestSource,
  isEmbedUrl,
  sortVideos,
} = await import('../src/data/api/videoUrlResolver.js');

const { buildPlayerMenu } = await import('../src/menus/playerMenuBuilder.js');
const { MENU_SOURCES_ID, MENU_QUALITY_ID } = await import('../src/data/models/playerConstants.js');

const { applyLocalFilter } = await import('../src/data/api/mediaFilterMatcher.js');
const { MediaTypeFilter } = await import('../src/data/models/filterState.js');

const {
  DEFAULT_SETTINGS,
  applySettingsSnapshotMap,
  settingsStore,
  toSettingsSnapshotMap,
  updateSetting,
} = await import('../src/data/local/settingsStore.js');

const {
  languageCode,
  languageTag,
  isPolish,
} = await import('../src/i18n/locale.js');

const { t } = await import('../src/i18n/index.js');
const { buildAppSettingsMenu } = await import('../src/menus/appSettingsMenu.js');

test('mediaRef canonical key helpers parse and normalize movies, shows, and episodes', () => {
  assert.equal(makeMovieKey(603), 'movie_603');
  assert.equal(makeShowKey(1396), 'show_1396');
  assert.equal(makeEpisodeKey(1396, 2, 5), 'show_1396_2_5');

  const ep = parseMediaRef('show_1396_2_5');
  assert.deepEqual(
    { tmdbId: ep.tmdbId, season: ep.season, episode: ep.episode, isTvShow: ep.isTvShow, parentKey: ep.parentKey },
    { tmdbId: 1396, season: 2, episode: 5, isTvShow: true, parentKey: 'show_1396' },
  );

  assert.equal(canonicalUrl('search_movie_603'), 'movie_603');
  assert.equal(getTvShowKey({ url: 'show_1396_2_5', isTvShow: true }), 'show_1396');
});

test('continueWatchingMapper handles sampling filter, active vs jump back in, and date grouping', () => {
  const now = 1_750_000_000_000;
  const sampledMovie = {
    kind: 'InProgress',
    url: 'movie_10',
    parentUrl: 'movie_10',
    progressPercentage: 0.01,
    progressMs: 20_000,
    timestamp: now - 1000,
    titlePl: 'Sampled',
  };
  const activeMovie = {
    kind: 'InProgress',
    url: 'movie_11',
    parentUrl: 'movie_11',
    progressPercentage: 0.4,
    progressMs: 1_200_000,
    timestamp: now - 5000,
    titlePl: 'Active',
  };
  const droppedMovie = {
    kind: 'InProgress',
    url: 'movie_12',
    parentUrl: 'movie_12',
    progressPercentage: 0.35,
    progressMs: 900_000,
    timestamp: now - ACTIVE_WATCH_WINDOW_MS - 10_000,
    titlePl: 'Dropped',
  };
  const watchedMovie = {
    kind: 'Watched',
    url: 'movie_13',
    parentUrl: 'movie_13',
    progressPercentage: 1.0,
    progressMs: 0,
    timestamp: now - 2000,
    titlePl: 'Watched',
  };

  assert.equal(isSampled(sampledMovie, [sampledMovie]), true);
  assert.equal(isSampled(activeMovie, [activeMovie]), false);

  const active = toActiveContinueWatchingItems([sampledMovie, activeMovie, droppedMovie], now);
  assert.deepEqual(active.map((i) => i.url), ['movie_11']);

  const jumpBack = toJumpBackInItems([sampledMovie, activeMovie, droppedMovie], now);
  assert.deepEqual(jumpBack.map((i) => i.url), ['movie_12']);

  const unwatched = filterUnwatchedWatchlist(
    [{ url: 'movie_13' }, { url: 'movie_99' }],
    [watchedMovie],
  );
  assert.deepEqual(unwatched.map((i) => i.url), ['movie_99']);

  const grouped = groupWatchHistoryByDate([activeMovie, watchedMovie]);
  assert.ok(grouped.length >= 1);
  assert.ok(grouped.some((g) => g.items.some((m) => m.url === 'movie_11')));
});

test('videoSignatureExtractor & subtitleRanking prioritize moviehash and matching release group', () => {
  const sig = extractVideoSignature({
    videoUrl: '/api/proxy?url=' + encodeURIComponent('https://cdn.test/files/Inception.2010.1080p.WEB-DL.x264-FLUX.mp4'),
  });
  assert.equal(sig, 'Inception.2010.1080p.WEB-DL.x264-FLUX');

  const candHash = {
    fileId: 1,
    fileName: 'other.srt',
    release: 'Other-XYZ',
    moviehashMatch: true,
    downloadCount: 5,
  };
  const candGroup = {
    fileId: 2,
    fileName: 'Inception.2010.1080p.WEB-DL.x264-FLUX.srt',
    release: 'Inception.2010.1080p.WEB-DL.x264-FLUX',
    moviehashMatch: false,
    downloadCount: 200,
  };
  const candCam = {
    fileId: 3,
    fileName: 'Inception.2010.CAM-LOW.srt',
    release: 'Inception.2010.CAM-LOW',
    moviehashMatch: false,
    downloadCount: 20,
  };

  const ranked = rankOpenSubtitleCandidates([candCam, candGroup, candHash], sig);
  assert.equal(ranked[0].fileId, 1);
  assert.equal(ranked[1].fileId, 2);
  assert.ok(calculateCandidateScore(candGroup, sig) > calculateCandidateScore(candCam, sig));

  const sigWithFilename = extractVideoSignature({
    videoUrl: 'https://cdn.test/stream.m3u8',
    filename: 'Whatever.It.Takes.2000.1080p.WEBRip.x265-RARBG.mp4',
  });
  assert.equal(sigWithFilename, 'Whatever.It.Takes.2000.1080p.WEBRip.x265-RARBG');

  const sigMultiline = extractVideoSignature({
    videoUrl: 'https://cdn.test/stream.m3u8',
    serverName: 'Castle\nWhatever.It.Takes.2000.1080p.WEBRip.x265-RARBG.mp4',
  });
  assert.equal(sigMultiline, 'Whatever.It.Takes.2000.1080p.WEBRip.x265-RARBG');
});

test('videoUrlResolver sorts preferred quality, matches TV show server, and identifies embeds/YouTube', () => {
  const sources = [
    { url: 'https://cdn/720.m3u8', serverName: 'StreamA', sourceWebsite: 'stream_api', quality: '720p' },
    { url: 'https://cdn/1080.m3u8', serverName: 'StreamB', sourceWebsite: 'stream_api', quality: '1080p' },
  ];
  const sorted = sortVideos(sources, '1080p');
  assert.equal(sorted[0].quality, '1080p');

  const bestForShow = findBestSource(sorted, { serverName: 'StreamA', sourceWebsite: 'stream_api' });
  assert.equal(bestForShow.serverName, 'StreamA');

  // Episode mismatch filtering
  const tvSources = [
    { url: 'https://cdn/s01e01.m3u8', filename: 'Show.Title.S01E01.720p.mkv', quality: '720p' },
    { url: 'https://cdn/s02e05.m3u8', filename: 'Show.Title.S02E05.720p.mkv', quality: '720p' },
    { url: 'https://cdn/generic.m3u8', filename: '', serverName: 'StreamC', quality: '1080p' },
  ];
  const filteredTv = sortVideos(tvSources, '1080p', {
    title: 'Show Title',
    isTvShow: true,
    season: 2,
    episode: 5,
  });
  // S01E01 must be dropped, and S02E05 with verified title should rank before unverified 1080p
  assert.equal(filteredTv.length, 2);
  assert.equal(filteredTv[0].filename, 'Show.Title.S02E05.720p.mkv');

  // Title & year confidence ranking: matching movie 720p ranked ahead of 1080p wrong year
  const movieSources = [
    { url: 'https://cdn/wrong.m3u8', filename: 'Gladiator.2000.1080p.mkv', quality: '1080p' },
    { url: 'https://cdn/right.m3u8', filename: 'Gladiator.II.2024.720p.mkv', quality: '720p' },
  ];
  const rankedMovies = sortVideos(movieSources, '1080p', {
    title: 'Gladiator II',
    year: 2024,
  });
  assert.equal(rankedMovies[0].filename, 'Gladiator.II.2024.720p.mkv');

  const mixedLangSources = [
    { url: 'https://cdn/en.m3u8', filename: 'Ice Age Collision Course 2016 1080p', quality: '1080p', serverName: 'ShowBox' },
    { url: 'https://cdn/pl.m3u8', filename: '[PL Dubbing] Epoka lodowcowa 5 1080p', quality: '1080p', serverName: 'Polish [CDA]' },
  ];
  const rankedByLang = sortVideos(mixedLangSources, '1080p', {
    title: 'Epoka lodowcowa: Mocne uderzenie',
    year: 2016,
  });
  assert.equal(rankedByLang[0].serverName, 'Polish [CDA]');

  assert.equal(isEmbedUrl('https://ebd.cda.pl/620x368/12345'), true);
  assert.equal(isEmbedUrl('https://cdn.test/video.m3u8'), false);
  assert.equal(extractYoutubeVideoId('https://www.youtube.com/watch?v=dQw4w9WgXcQ'), 'dQw4w9WgXcQ');
  assert.equal(extractYoutubeVideoId('https://youtu.be/dQw4w9WgXcQ'), 'dQw4w9WgXcQ');
});

test('buildPlayerMenu includes sources menu with loader when isLoadingSources is true', () => {
  const menuData = buildPlayerMenu({
    alternativeSources: [],
    isLoadingSources: true,
    settings: {
      lektorVoice: 'edge_marek',
      subtitleStyle: {
        fontSizeFraction: 0.05,
        textColorHex: '#FFFFFF',
        backgroundColorHex: '#CC000000',
        edgeType: 'OUTLINE',
        verticalOffsetRatio: 0.08,
      },
    },
  });
  const sourcesItem = menuData.items.find((i) => i.id === MENU_SOURCES_ID);
  assert.ok(sourcesItem);
  const loader = sourcesItem.items.find((i) => i.id === 'src_loading_indicator');
  assert.ok(loader);
});

test('buildPlayerMenu groups sources by server/version and builds quality menu respecting preferences', () => {
  const selectedSources = [];
  const v1080 = { url: 'https://cdn/1080.m3u8', serverName: 'StreamA', sourceWebsite: 'stream_api', version: 'Lektor', quality: '1080p' };
  const v720 = { url: 'https://cdn/720.m3u8', serverName: 'StreamA', sourceWebsite: 'stream_api', version: 'Lektor', quality: '720p' };
  const v480 = { url: 'https://cdn/480.m3u8', serverName: 'StreamA', sourceWebsite: 'stream_api', version: 'Lektor', quality: '480p' };
  const otherSource = { url: 'https://cdn/other.m3u8', serverName: 'StreamB', sourceWebsite: 'stream_api', version: 'Dubbing', quality: '1080p' };

  const menuData = buildPlayerMenu({
    alternativeSources: [v1080, v720, v480, otherSource],
    videoUrl: v720.url,
    settings: {
      preferredQuality: '720p',
      subtitleStyle: {},
    },
    onSelectSource: (src) => selectedSources.push(src),
  });

  const sourcesItem = menuData.items.find((i) => i.id === MENU_SOURCES_ID);
  assert.ok(sourcesItem);
  const options = sourcesItem.items.filter((i) => i.type === 'option');

  // 4 sources grouped into 2 options
  assert.equal(options.length, 2);
  const streamAOption = options.find((o) => o.label === 'StreamA • Lektor');
  assert.ok(streamAOption);
  assert.equal(streamAOption.isSelected, true);

  // Clicking StreamA option selects 720p per preferred quality
  streamAOption.onClick();
  assert.equal(selectedSources.length, 1);
  assert.equal(selectedSources[0].url, v720.url);

  // Quality menu should be present for StreamA
  const qualityItem = menuData.items.find((i) => i.id === MENU_QUALITY_ID);
  assert.ok(qualityItem);
  assert.equal(qualityItem.value, '720p');
  assert.equal(qualityItem.items.length, 3);
  assert.equal(qualityItem.items[0].label, '1080p');
  assert.equal(qualityItem.items[1].label, '720p');
  assert.equal(qualityItem.items[1].isSelected, true);

  // Selecting 1080p from quality menu
  qualityItem.items[0].onClick();
  assert.equal(selectedSources.length, 2);
  assert.equal(selectedSources[1].url, v1080.url);
});

test('mediaFilterMatcher filters by type, genre, decade, and minimum rating', () => {
  const catalog = [
    { url: 'movie_1', isTvShow: false, genreIds: [28, 12], year: 2022, tiwiRating: 8.2 },
    { url: 'show_2', isTvShow: true, genreIds: [35], year: 2021, tiwiRating: 7.4 },
    { url: 'movie_3', isTvShow: false, genreIds: [28], year: 1995, filmanRating: 6.1 },
  ];

  const out = applyLocalFilter(catalog, {
    sortBy: 'RATING_DESC',
    selectedGenreIds: [28],
    decade: 'YEAR_2020_2023',
    minRating: 7,
    mediaTypeFilter: MediaTypeFilter.MOVIES,
  });
  assert.deepEqual(out.map((i) => i.url), ['movie_1']);
});

test('settingsStore round-trips cloud snapshot maps accurately', () => {
  settingsStore.set({
    ...DEFAULT_SETTINGS,
    preferredQuality: '1080p',
    autoPlayNext: false,
    lektorEnabled: true,
    lektorHideSubtitles: true,
    lektorVoice: 'edge_zofia',
    lektorDuckingVolume: 0.6,
    lektorTimingOffsetMs: -1000,
    lektorEdgeRate: '+15%',
    lektorEdgePitch: '-6Hz',
    lektorEdgeVolume: '-15%',
    lektorEdgeCalmMode: false,
  });

  const snap = toSettingsSnapshotMap(settingsStore.get());
  settingsStore.set({ ...DEFAULT_SETTINGS });
  applySettingsSnapshotMap(snap);

  const restored = settingsStore.get();
  assert.equal(restored.preferredQuality, '1080p');
  assert.equal(restored.autoPlayNext, false);
  assert.equal(restored.lektorEnabled, true);
  assert.equal(restored.lektorHideSubtitles, true);
  assert.equal(restored.lektorVoice, 'edge_zofia');
  assert.equal(restored.lektorDuckingVolume, 0.6);
  assert.equal(restored.lektorTimingOffsetMs, -1000);
  assert.equal(restored.lektorEdgeRate, '+15%');
  assert.equal(restored.lektorEdgePitch, '-6Hz');
  assert.equal(restored.lektorEdgeVolume, '-15%');
  assert.equal(restored.lektorEdgeCalmMode, false);
});

test('runtime locale switching toggles languageCode, isPolish, t() and overlay menu between English and Polish', () => {
  // Initially null -> defaults to browser locale
  updateSetting('appLanguage', null);
  const defaultIsPolish = isPolish();

  // Switch to Polish
  updateSetting('appLanguage', 'pl');
  assert.equal(languageCode(), 'pl');
  assert.equal(languageTag(), 'pl-PL');
  assert.equal(isPolish(), true);
  assert.equal(t('overlay_menu_settings'), 'Ustawienia');
  assert.equal(t('overlay_menu_language'), 'Język');

  let menu = buildAppSettingsMenu({ settings: settingsStore.get() });
  let langItem = menu.items.find((i) => i.id === 'app_language');
  assert.ok(langItem);
  assert.equal(langItem.label, 'Język');
  assert.equal(langItem.value, 'Polski');
  assert.equal(langItem.items.length, 2);
  assert.equal(langItem.items[0].isSelected, false);
  assert.equal(langItem.items[1].isSelected, true);

  // Switch to English
  updateSetting('appLanguage', 'en');
  assert.equal(languageCode(), 'en');
  assert.equal(languageTag(), 'en-US');
  assert.equal(isPolish(), false);
  assert.equal(t('overlay_menu_settings'), 'Settings');
  assert.equal(t('overlay_menu_language'), 'Language');

  menu = buildAppSettingsMenu({ settings: settingsStore.get() });
  langItem = menu.items.find((i) => i.id === 'app_language');
  assert.ok(langItem);
  assert.equal(langItem.label, 'Language');
  assert.equal(langItem.value, 'English');
  assert.equal(langItem.items[0].isSelected, true);
  assert.equal(langItem.items[1].isSelected, false);

  // Snapshot serialization round-trip
  const snap = toSettingsSnapshotMap(settingsStore.get());
  assert.equal(snap.app_language, 'en');

  updateSetting('appLanguage', 'pl');
  const snapPl = toSettingsSnapshotMap(settingsStore.get());
  assert.equal(snapPl.app_language, 'pl');

  applySettingsSnapshotMap({ app_language: 'en' });
  assert.equal(settingsStore.get().appLanguage, 'en');

  // Disallowed languages are rejected
  applySettingsSnapshotMap({ app_language: 'fr' });
  assert.equal(settingsStore.get().appLanguage, 'en');

  // Reset to default
  updateSetting('appLanguage', null);
  assert.equal(isPolish(), defaultIsPolish);
});

test('classifyContent accurately categorizes Kids, Anime, Polish, Foreign, and Documentaries', () => {
  // Kids / Family
  assert.equal(
    classifyContent({ genreIds: [16, 10751], originalLanguage: 'en', countries: ['United States'] }),
    ContentCategory.KIDS_FAMILY,
  );
  assert.equal(
    classifyContent({ genreIds: [16], originalLanguage: 'en' }, true),
    ContentCategory.KIDS_FAMILY,
  );

  // Anime
  assert.equal(
    classifyContent({ genreIds: [16], originalLanguage: 'ja', countries: ['Japan'] }),
    ContentCategory.ANIME,
  );

  // Native Polish
  assert.equal(
    classifyContent({ genreIds: [18], originalLanguage: 'pl', countries: ['Poland'] }),
    ContentCategory.NATIVE_DOMESTIC,
  );

  // Documentary
  assert.equal(
    classifyContent({ genreIds: [99], originalLanguage: 'en' }),
    ContentCategory.DOCUMENTARY,
  );

  // Foreign Mainstream (English)
  assert.equal(
    classifyContent({ genreIds: [28, 12], originalLanguage: 'en', countries: ['United States'] }),
    ContentCategory.FOREIGN_MAINSTREAM,
  );

  // Foreign World (e.g. Korean / French)
  assert.equal(
    classifyContent({ genreIds: [18, 53], originalLanguage: 'ko', countries: ['South Korea'] }),
    ContentCategory.FOREIGN_WORLD,
  );
});

test('getMediaKey returns parentKey for episodes and canonicalKey for movies', () => {
  assert.equal(getMediaKey({ url: 'movie_603', isTvShow: false }), 'movie_603');
  assert.equal(getMediaKey({ url: 'show_1396_1_1', isTvShow: true }), 'show_1396');
  assert.equal(getMediaKey({ url: 'show_1396', isTvShow: true }), 'show_1396');
});

test('settingsStore preserves contentRules through cloud sync snapshot maps', () => {
  settingsStore.set({
    ...DEFAULT_SETTINGS,
    contentRules: {
      ...DEFAULT_CONTENT_RULES,
      [ContentCategory.KIDS_FAMILY]: {
        audioMode: AudioPreferenceMode.LEKTOR_PREFERRED,
        subtitlesEnabled: true,
        subtitleLanguage: 'pl',
      },
      [ContentCategory.FOREIGN_MAINSTREAM]: {
        audioMode: AudioPreferenceMode.ORIGINAL_SUBTITLES,
        subtitlesEnabled: true,
        subtitleLanguage: 'en',
      },
    },
  });

  const snap = toSettingsSnapshotMap(settingsStore.get());
  assert.ok(snap.content_rules);

  settingsStore.set({ ...DEFAULT_SETTINGS });
  applySettingsSnapshotMap(snap);

  const restored = settingsStore.get();
  assert.equal(
    restored.contentRules[ContentCategory.KIDS_FAMILY].audioMode,
    AudioPreferenceMode.LEKTOR_PREFERRED,
  );
  assert.equal(
    restored.contentRules[ContentCategory.KIDS_FAMILY].subtitlesEnabled,
    true,
  );
  assert.equal(
    restored.contentRules[ContentCategory.FOREIGN_MAINSTREAM].audioMode,
    AudioPreferenceMode.ORIGINAL_SUBTITLES,
  );
  assert.equal(
    restored.contentRules[ContentCategory.FOREIGN_MAINSTREAM].subtitleLanguage,
    'en',
  );
  assert.equal(
    restored.contentRules[ContentCategory.ANIME].audioMode,
    AudioPreferenceMode.ORIGINAL_SUBTITLES,
  );
});

test('getMediaDetails caches responses and reuses them on subsequent requests', async () => {
  process.env.VITE_TMDB_API_KEY = 'test_api_key';
  const { getMediaDetails, getCachedMediaDetails, clearMediaDetailsCache } =
    await import('../src/data/api/tmdbClient.js');

  clearMediaDetailsCache();
  assert.equal(getCachedMediaDetails('movie_99999'), null);

  const originalFetch = globalThis.fetch;
  let fetchCallCount = 0;

  globalThis.fetch = async (url) => {
    fetchCallCount += 1;
    return {
      ok: true,
      status: 200,
      json: async () => ({
        id: 99999,
        title: 'Cached Movie Title',
        original_title: 'Original Cached Movie Title',
        vote_average: 8.5,
        release_date: '2024-05-10',
        runtime: 120,
        overview: 'A movie overview test for caching',
        genres: [{ id: 28, name: 'Action' }],
        production_countries: [{ name: 'Poland' }],
        credits: { cast: [] },
        recommendations: { results: [], total_pages: 1 },
        similar: { results: [] },
        videos: { results: [] },
      }),
    };
  };

  try {
    const details1 = await getMediaDetails({ url: 'movie_99999', isTvShow: false });
    assert.ok(details1);
    assert.equal(details1.baseItem.titlePl, 'Cached Movie Title');
    assert.equal(fetchCallCount, 1);

    // Synchronous cache hit check
    const cached = getCachedMediaDetails('movie_99999');
    assert.ok(cached);
    assert.equal(cached.baseItem.titlePl, 'Cached Movie Title');

    // Second call via request object should reuse cache without network fetch
    const details2 = await getMediaDetails('movie_99999');
    assert.equal(details2, details1);
    assert.equal(fetchCallCount, 1, 'Subsequent getMediaDetails must not call fetch again');

    // Clear cache and verify
    clearMediaDetailsCache();
    assert.equal(getCachedMediaDetails('movie_99999'), null);
  } finally {
    globalThis.fetch = originalFetch;
    clearMediaDetailsCache();
  }
});

test('watchlistStore and favoritesStore track deletions as tombstones and clear on sync', async () => {
  const {
    watchlistStore,
    deletedWatchlistStore,
    removeFromWatchlist,
    toggleWatchlist,
    clearDeletedWatchlist,
  } = await import('../src/data/local/watchlistStore.js');

  const {
    favoritesStore,
    deletedFavoritesStore,
    removeFavorite,
    toggleFavorite,
    clearDeletedFavorites,
  } = await import('../src/data/local/favoritesStore.js');

  // Watchlist tombstones
  watchlistStore.set([{ url: 'movie_101', titlePl: 'Movie 101', isTvShow: false }]);
  clearDeletedWatchlist();
  assert.equal(deletedWatchlistStore.get().length, 0);

  removeFromWatchlist('movie_101');
  assert.equal(watchlistStore.get().length, 0);
  assert.deepEqual(deletedWatchlistStore.get(), ['movie_101']);

  // Re-adding removes from tombstones
  toggleWatchlist({ url: 'movie_101', titlePl: 'Movie 101', isTvShow: false });
  assert.equal(watchlistStore.get().length, 1);
  assert.equal(deletedWatchlistStore.get().length, 0);

  // Favorites tombstones
  favoritesStore.set([{ url: 'show_202', titlePl: 'Show 202', isTvShow: true }]);
  clearDeletedFavorites();
  assert.equal(deletedFavoritesStore.get().length, 0);

  removeFavorite('show_202');
  assert.equal(favoritesStore.get().length, 0);
  assert.deepEqual(deletedFavoritesStore.get(), ['show_202']);

  clearDeletedFavorites();
  assert.equal(deletedFavoritesStore.get().length, 0);
});

test('syncNow sends canonical url, parentUrl, isTvShow, and tombstones to sync server', async () => {
  const { sessionStore, saveAuthSession, clearAuthSession } = await import(
    '../src/data/local/sessionStore.js'
  );
  const { progressStore } = await import('../src/data/local/progressStore.js');
  const { watchlistStore, deletedWatchlistStore } = await import(
    '../src/data/local/watchlistStore.js'
  );
  const { favoritesStore, deletedFavoritesStore } = await import(
    '../src/data/local/favoritesStore.js'
  );
  const { syncNow } = await import('../src/data/sync/syncManager.js');

  saveAuthSession({ token: 'test_token', userId: 'u1', username: 'testuser' });

  progressStore.set([
    {
      kind: 'InProgress',
      url: 'show_1399_1_3',
      parentUrl: 'show_1399',
      posterUrl: '',
      titlePl: 'Game of Thrones',
      season: 1,
      episode: 3,
      hasNextEpisode: true,
      progressPercentage: 0.6,
      progressMs: 36000,
      timestamp: 1700000000,
    },
  ]);
  watchlistStore.set([
    {
      url: 'movie_550',
      titlePl: 'Fight Club',
      isTvShow: false,
      year: 1999,
      posterUrl: '',
    },
  ]);
  deletedWatchlistStore.set(['movie_603']);
  favoritesStore.set([]);
  deletedFavoritesStore.set(['show_1396']);

  const originalFetch = globalThis.fetch;
  let pushedPayload = null;

  globalThis.fetch = async (url, opts) => {
    const parsed = new URL(url, 'http://localhost');
    const action = parsed.searchParams.get('action');

    if (action === 'pull') {
      return {
        ok: true,
        status: 200,
        text: async () =>
          JSON.stringify({
            serverTimestamp: 1700000500,
            progress: [
              {
                url: 'show_1399_1_2',
                parentUrl: 'show_1399',
                titlePl: 'Game of Thrones',
                season: 1,
                episode: 2,
                hasNextEpisode: true,
                isWatched: true,
                updatedAt: 1699999999,
              },
            ],
            watchlist: [],
            favorites: [],
            preferences: {},
          }),
      };
    }

    if (action === 'push') {
      pushedPayload = JSON.parse(opts.body);
      return {
        ok: true,
        status: 200,
        text: async () => JSON.stringify({ success: true, serverTimestamp: 1700000600 }),
      };
    }

    return { ok: true, status: 200, text: async () => '{}' };
  };

  try {
    const success = await syncNow();
    assert.equal(success, true);
    assert.ok(pushedPayload);

    // Verify progress pushed with url and parentUrl
    const prog = pushedPayload.progress.find((p) => p.url === 'show_1399_1_3');
    assert.ok(prog);
    assert.equal(prog.parentUrl, 'show_1399');
    assert.equal(prog.tmdbId, 1399);
    assert.equal(prog.season, 1);
    assert.equal(prog.episode, 3);
    assert.equal(prog.hasNextEpisode, true);

    // Verify watchlist pushed active items and tombstones
    const activeWatch = pushedPayload.watchlist.find((w) => w.url === 'movie_550');
    assert.ok(activeWatch);
    assert.equal(activeWatch.isDeleted, false);
    assert.equal(activeWatch.isTvShow, false);

    const tombstoneWatch = pushedPayload.watchlist.find((w) => w.url === 'movie_603');
    assert.ok(tombstoneWatch);
    assert.equal(tombstoneWatch.isDeleted, true);

    // Verify tombstones were cleared after successful sync
    assert.equal(deletedWatchlistStore.get().length, 0);
    assert.equal(deletedFavoritesStore.get().length, 0);

    // Verify pulled progress item was merged into local progressStore
    const pulledItem = progressStore.get().find((p) => p.url === 'show_1399_1_2');
    assert.ok(pulledItem);
    assert.equal(pulledItem.kind, 'Watched');
    assert.equal(pulledItem.hasNextEpisode, true);
  } finally {
    globalThis.fetch = originalFetch;
    clearAuthSession();
  }
});
