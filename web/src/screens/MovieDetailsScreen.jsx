import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { t } from '../i18n/index.js';
import {
  TmdbFeed,
  fetchFeedPage,
  getMediaDetails,
} from '../data/api/tmdbClient.js';
import { resolveStreamsWithUpdates } from '../data/api/videoUrlResolver.js';
import {
  MARK_AS_WATCHED_PROGRESS_THRESHOLD,
  buildProgressMap,
  markAsWatched,
  useProgressItems,
} from '../data/local/progressStore.js';
import { useFavorites } from '../data/local/favoritesStore.js';
import { useWatchlist } from '../data/local/watchlistStore.js';
import { parseMediaRef } from '../data/models/mediaRef.js';
import { SectionFocusPrefix, pushFocusKey } from '../focus/focusRestoration.js';
import {
  ContextOption,
  buildMediaContextMenu,
} from '../components/contextMenuBuilder.js';
import { useDeviceCasting } from '../hooks/useDeviceCasting.js';
import TiwiFullscreenLoader from '../components/TiwiFullscreenLoader.jsx';
import PosterSection from '../components/sections/PosterSection.jsx';
import TabRowSection from '../components/sections/TabRowSection.jsx';
import EpisodesRowSection from '../components/sections/EpisodesRowSection.jsx';
import MoviesGridSection from '../components/sections/MoviesGridSection.jsx';
import MovieDetailsSection from '../components/sections/MovieDetailsSection.jsx';
import ErrorSection from '../components/sections/ErrorSection.jsx';

const TabId = {
  EPISODES: 0,
  RECOMMENDED: 1,
  SIMILAR: 2,
  DETAILS: 3,
};

function computeWatchButtonState({
  detailedMedia,
  movieStreams,
  isExtractingLinks,
  progressItems,
}) {
  const base = detailedMedia?.baseItem;
  if (!base) {
    return { label: t('details_unavailable'), enabled: false, isLoading: false, url: '' };
  }

  const mostRecent = progressItems.find((p) => p.parentUrl === base.url) || null;

  if (!base.isTvShow) {
    if (movieStreams.length === 0 && isExtractingLinks) {
      return { label: t('common_loading'), enabled: false, isLoading: true, url: '' };
    }
    if (movieStreams.length === 0 && !isExtractingLinks) {
      return { label: t('details_unavailable'), enabled: false, isLoading: false, url: '' };
    }
    if (mostRecent?.kind === 'InProgress') {
      return { label: t('details_continue'), enabled: true, isLoading: false, url: base.url };
    }
    if (mostRecent?.kind === 'Watched') {
      return { label: t('details_watch_again'), enabled: true, isLoading: false, url: base.url };
    }
    return { label: t('details_watch_now'), enabled: true, isLoading: false, url: base.url };
  }

  const flatEpisodes = [];
  for (const s of base.seasons || []) {
    for (const ep of s.episodes || []) {
      flatEpisodes.push(ep);
    }
  }
  if (flatEpisodes.length === 0) {
    return { label: t('details_unavailable'), enabled: false, isLoading: false, url: '' };
  }

  if (mostRecent?.kind === 'Watched') {
    const idx = flatEpisodes.findIndex((e) => e.url === mostRecent.url);
    if (idx !== -1 && idx + 1 < flatEpisodes.length) {
      const nextEp = flatEpisodes[idx + 1];
      return {
        label: t('details_watch_next_episode', nextEp.seasonNumber, nextEp.episodeNumber),
        enabled: true,
        isLoading: false,
        url: nextEp.url,
      };
    }
    return { label: t('details_watch_now'), enabled: true, isLoading: false, url: flatEpisodes[0].url };
  }

  if (mostRecent && (mostRecent.kind === 'InProgress' || mostRecent.kind === 'NextEpisode')) {
    const ep = flatEpisodes.find((e) => e.url === mostRecent.url);
    if (ep) {
      return {
        label: t('details_continue_episode', ep.seasonNumber, ep.episodeNumber),
        enabled: true,
        isLoading: false,
        url: ep.url,
      };
    }
  }

  return { label: t('details_watch_now'), enabled: true, isLoading: false, url: flatEpisodes[0].url };
}

function collectEpisodesUpTo(detailedMedia, targetSeasonNum, targetEpisodeNum) {
  const base = detailedMedia?.baseItem;
  if (!base?.seasons) return [];
  const flat = [];
  for (const s of base.seasons) {
    for (const ep of s.episodes || []) {
      flat.push(ep);
    }
  }
  const collected = [];
  for (const s of base.seasons) {
    if (s.seasonNumber < targetSeasonNum) {
      for (const ep of s.episodes || []) collected.push(ep);
    } else if (s.seasonNumber === targetSeasonNum) {
      for (const ep of s.episodes || []) {
        if (ep.episodeNumber <= targetEpisodeNum) collected.push(ep);
      }
      break;
    }
  }
  return collected.map((ep) => {
    const idx = flat.findIndex((f) => f.url === ep.url);
    const nextEp = idx !== -1 ? flat[idx + 1] : null;
    return {
      url: ep.url,
      seriesUrl: base.url,
      posterUrl: base.posterUrl,
      titlePl: `${base.titlePl} - S${ep.seasonNumber}E${ep.episodeNumber}`,
      seriesTitle: base.seriesTitle || base.titlePl,
      episodeTitle: ep.title,
      seasonNumber: ep.seasonNumber,
      episodeNumber: ep.episodeNumber,
      hasNextEpisode: Boolean(nextEp),
      nextEpisodeUrl: nextEp?.url || null,
      isTvShow: true,
    };
  });
}

export default function MovieDetailsScreen({
  request,
  autoPlay = false,
  episodeUrl = null,
  onNavigate,
  onOpenOverlay,
  onCloseOverlay,
  onToast,
}) {
  const [isLoading, setIsLoading] = useState(true);
  const [errorMessage, setErrorMessage] = useState(null);
  const [detailedMedia, setDetailedMedia] = useState(null);
  const [movieStreams, setMovieStreams] = useState([]);
  const [isExtractingLinks, setIsExtractingLinks] = useState(false);
  const [selectedTabId, setSelectedTabId] = useState(null);
  const [recommendations, setRecommendations] = useState([]);
  const [recommendationsPage, setRecommendationsPage] = useState(1);
  const [recommendationsHasMore, setRecommendationsHasMore] = useState(false);
  const [loadingMoreRecs, setLoadingMoreRecs] = useState(false);

  const progressItems = useProgressItems();
  useFavorites();
  useWatchlist();
  const progressMap = useMemo(() => buildProgressMap(progressItems), [progressItems]);

  const watchButtonRef = useRef(null);
  const hasAutoPlayedRef = useRef(false);

  const loadDetails = useCallback(async () => {
    setIsLoading(true);
    setErrorMessage(null);
    setMovieStreams([]);
    setIsExtractingLinks(false);
    hasAutoPlayedRef.current = false;

    try {
      const details = await getMediaDetails(request);
      if (!details) {
        setErrorMessage(t('error_media_not_found'));
        setIsLoading(false);
        return;
      }

      setDetailedMedia(details);
      setRecommendations(details.recommendations || []);
      setRecommendationsPage(1);
      setRecommendationsHasMore((details.recommendationsTotalPages || 1) > 1);

      const initialTabs = [];
      if (details.baseItem.seasons) initialTabs.push(TabId.EPISODES);
      if ((details.recommendations || []).length > 0) initialTabs.push(TabId.RECOMMENDED);
      if ((details.similarMovies || []).length > 0) initialTabs.push(TabId.SIMILAR);
      if ((details.actors || []).length > 0) initialTabs.push(TabId.DETAILS);
      setSelectedTabId(initialTabs[0] ?? TabId.SIMILAR);
    } catch (err) {
      setErrorMessage(err?.message || t('common_couldnt_load_movies'));
    } finally {
      setIsLoading(false);
    }
  }, [request]);

  useEffect(() => {
    loadDetails();
  }, [loadDetails]);

  // Prewarm movie streams live
  useEffect(() => {
    if (!detailedMedia || detailedMedia.baseItem.isTvShow) return undefined;
    setIsExtractingLinks(true);
    const unsubscribe = resolveStreamsWithUpdates({
      mediaUrl: detailedMedia.baseItem.url,
      onSourcesUpdate: (sources) => {
        setMovieStreams(sources);
        if (sources.length > 0) setIsExtractingLinks(false);
      },
      onSettled: () => {
        setIsExtractingLinks(false);
      },
    });
    return unsubscribe;
  }, [detailedMedia]);

  const watchButtonState = useMemo(
    () =>
      computeWatchButtonState({
        detailedMedia,
        movieStreams,
        isExtractingLinks,
        progressItems,
      }),
    [detailedMedia, movieStreams, isExtractingLinks, progressItems],
  );

  const { buildContextMenuProps, handleCastToDevice } = useDeviceCasting({
    onToast,
    onCloseOverlay,
    detailedMedia,
    watchButtonUrl: watchButtonState?.url,
  });

  // Auto-play support when coming from Continue Watching / New Episodes
  useEffect(() => {
    if (!autoPlay || hasAutoPlayedRef.current || isLoading || !detailedMedia) return;
    const targetUrl =
      episodeUrl ||
      watchButtonState.url ||
      (!detailedMedia.baseItem.isTvShow ? detailedMedia.baseItem.url : '');
    if (!targetUrl) return;
    hasAutoPlayedRef.current = true;
    onNavigate?.({ name: 'player', url: targetUrl });
  }, [autoPlay, episodeUrl, isLoading, detailedMedia, watchButtonState.url, onNavigate]);

  const tabs = useMemo(() => {
    if (!detailedMedia) return [];
    const list = [];
    if (detailedMedia.baseItem.seasons) {
      list.push({ id: TabId.EPISODES, label: t('details_episodes') });
    }
    if (recommendations.length > 0) {
      list.push({ id: TabId.RECOMMENDED, label: t('details_recommended') });
    }
    if ((detailedMedia.similarMovies || []).length > 0) {
      list.push({ id: TabId.SIMILAR, label: t('details_similar') });
    }
    if ((detailedMedia.actors || []).length > 0) {
      list.push({ id: TabId.DETAILS, label: t('details_about') });
    }
    return list;
  }, [detailedMedia, recommendations.length]);

  const handleLoadMoreRecommendations = useCallback(async () => {
    if (!detailedMedia || !recommendationsHasMore || loadingMoreRecs) return;
    const ref = parseMediaRef(detailedMedia.baseItem.url, detailedMedia.baseItem.isTvShow);
    if (!ref) return;
    setLoadingMoreRecs(true);
    try {
      const next = await fetchFeedPage(
        { type: TmdbFeed.RECOMMENDATIONS, ref },
        recommendationsPage + 1,
      );
      setRecommendations((prev) => {
        const seen = new Set(prev.map((m) => m.url));
        return [...prev, ...next.movies.filter((m) => !seen.has(m.url))];
      });
      setRecommendationsPage(next.page);
      setRecommendationsHasMore(next.hasMore);
    } finally {
      setLoadingMoreRecs(false);
    }
  }, [detailedMedia, recommendationsHasMore, loadingMoreRecs, recommendationsPage]);

  const openDetails = (movie, focusKey) => {
    if (focusKey) pushFocusKey(focusKey);
    onNavigate?.({
      name: 'details',
      request: movie.detailsRequest || { url: movie.url, isTvShow: movie.isTvShow },
      autoPlay: false,
      episodeUrl: null,
    });
  };

  const openStandardContextMenu = (movie, focusKey) => {
    if (focusKey) pushFocusKey(focusKey);
    onOpenOverlay?.(
      buildMediaContextMenu(
        buildContextMenuProps(
          movie,
          [
            ContextOption.PLAY_ON_TV,
            ContextOption.MARK_AS_WATCHED,
            ContextOption.MARK_AS_NOT_WATCHED,
            ContextOption.FAVORITES,
            ContextOption.WATCHLIST,
          ],
          { progressMap },
        ),
      ),
    );
  };

  const openEpisodeContextMenu = (ep) => {
    const base = detailedMedia.baseItem;
    const progress = progressMap[ep.url] ?? 0;
    const isWatched = progress >= MARK_AS_WATCHED_PROGRESS_THRESHOLD;
    const allEps = [];
    for (const s of base.seasons || []) {
      for (const e of s.episodes || []) allEps.push(e);
    }
    const idx = allEps.findIndex((e) => e.url === ep.url);
    const hasNext = idx !== -1 && idx + 1 < allEps.length;

    const epMovie = {
      url: ep.url,
      seriesUrl: base.url,
      posterUrl: base.posterUrl,
      titlePl: `${base.titlePl} - S${ep.seasonNumber}E${ep.episodeNumber}`,
      seriesTitle: base.seriesTitle || base.titlePl,
      episodeTitle: ep.title,
      seasonNumber: ep.seasonNumber,
      episodeNumber: ep.episodeNumber,
      hasNextEpisode: hasNext,
      isTvShow: true,
    };

    pushFocusKey(`${SectionFocusPrefix.EPISODES}${ep.url}`);
    onOpenOverlay?.(
      buildMediaContextMenu(
        buildContextMenuProps(
          epMovie,
          isWatched
            ? [ContextOption.PLAY_ON_TV, ContextOption.MARK_AS_NOT_WATCHED]
            : [ContextOption.PLAY_ON_TV, ContextOption.MARK_AS_WATCHED, ContextOption.MARK_PREVIOUS_AS_WATCHED],
          {
            progressMap,
            onMarkPreviousAsWatched: () => {
              const batch = collectEpisodesUpTo(detailedMedia, ep.seasonNumber, ep.episodeNumber);
              if (batch.length > 0) markAsWatched(batch);
            },
          },
        ),
      ),
    );
  };

  if (isLoading) return <TiwiFullscreenLoader />;
  if (errorMessage || !detailedMedia) {
    return (
      <div className="screen-content">
        <div className="screen-top-spacer" />
        <ErrorSection message={errorMessage} onRetry={loadDetails} />
      </div>
    );
  }

  return (
    <div className="screen-content">
      <PosterSection
        detailedMedia={detailedMedia}
        watchButtonState={watchButtonState}
        trailerUrl={detailedMedia.trailerUrl}
        watchButtonRef={watchButtonRef}
        onPlayClick={(targetUrl) => onNavigate?.({ name: 'player', url: targetUrl })}
        onWatchTrailerClick={(trailerUrl) =>
          onNavigate?.({
            name: 'player',
            url: trailerUrl,
            title: detailedMedia.baseItem?.titlePl || detailedMedia.baseItem?.titleEn || '',
            description: detailedMedia.baseItem?.description || '',
            isTrailer: true,
          })
        }
        onMoreOptionsClick={() =>
          openStandardContextMenu(
            detailedMedia.baseItem,
            `${SectionFocusPrefix.FEATURED}more`,
          )
        }
      />

      <TabRowSection
        tabs={tabs}
        selectedId={selectedTabId}
        onSelectTab={setSelectedTabId}
      />

      {selectedTabId === TabId.EPISODES && (
        <EpisodesRowSection
          seasons={detailedMedia.baseItem.seasons || []}
          showPosterUrl={detailedMedia.baseItem.posterUrl}
          progressMap={progressMap}
          onEpisodeClick={(ep) => onNavigate?.({ name: 'player', url: ep.url })}
          onEpisodeLongClick={openEpisodeContextMenu}
        />
      )}

      {selectedTabId === TabId.RECOMMENDED && (
        <MoviesGridSection
          items={recommendations}
          progressMap={progressMap}
          hasMore={recommendationsHasMore}
          isLoadingMore={loadingMoreRecs}
          showLoadMoreButton={false}
          focusPrefix="details_rec_"
          onItemClick={(m) => openDetails(m, `details_rec_${m.url}`)}
          onItemLongClick={(m) => openStandardContextMenu(m, `details_rec_${m.url}`)}
          onLoadNextPage={handleLoadMoreRecommendations}
        />
      )}

      {selectedTabId === TabId.SIMILAR && (
        <MoviesGridSection
          items={detailedMedia.similarMovies || []}
          progressMap={progressMap}
          hasMore={false}
          showLoadMoreButton={false}
          focusPrefix="details_sim_"
          onItemClick={(m) => openDetails(m, `details_sim_${m.url}`)}
          onItemLongClick={(m) => openStandardContextMenu(m, `details_sim_${m.url}`)}
        />
      )}

      {selectedTabId === TabId.DETAILS && (
        <MovieDetailsSection
          actors={detailedMedia.actors || []}
          onActorClick={(actorId) => {
            pushFocusKey(`${SectionFocusPrefix.CREW}${actorId}`);
            onNavigate?.({ name: 'actor', url: actorId });
          }}
        />
      )}
    </div>
  );
}
