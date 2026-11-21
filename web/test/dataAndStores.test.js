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

const { applyLocalFilter } = await import('../src/data/api/mediaFilterMatcher.js');
const { MediaTypeFilter } = await import('../src/data/models/filterState.js');

const {
  DEFAULT_SETTINGS,
  applySettingsSnapshotMap,
  settingsStore,
  toSettingsSnapshotMap,
} = await import('../src/data/local/settingsStore.js');

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

  assert.equal(isEmbedUrl('https://ebd.cda.pl/620x368/12345'), true);
  assert.equal(isEmbedUrl('https://cdn.test/video.m3u8'), false);
  assert.equal(extractYoutubeVideoId('https://www.youtube.com/watch?v=dQw4w9WgXcQ'), 'dQw4w9WgXcQ');
  assert.equal(extractYoutubeVideoId('https://youtu.be/dQw4w9WgXcQ'), 'dQw4w9WgXcQ');
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
