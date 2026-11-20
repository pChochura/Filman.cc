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
} = await import('../src/data/models/mediaRef.js');

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
