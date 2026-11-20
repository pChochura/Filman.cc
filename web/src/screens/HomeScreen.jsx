import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { t } from '../i18n/index.js';
import { TmdbFeed, fetchFeedPage } from '../data/api/tmdbClient.js';
import { getPersonalizedRecommendations } from '../data/api/recommendationManager.js';
import { useFavorites } from '../data/local/favoritesStore.js';
import { useWatchlist } from '../data/local/watchlistStore.js';
import { buildProgressMap, useProgressItems } from '../data/local/progressStore.js';
import { clearNewEpisode, useNewEpisodes } from '../data/local/newEpisodesStore.js';
import {
  distinctBySeries,
  filterUnwatchedWatchlist,
  toActiveContinueWatchingItems,
  toJumpBackInItems,
} from '../data/local/continueWatchingMapper.js';
import {
  SectionFocusPrefix,
  computeNeighborFallbackKey,
  focusElementByKey,
  pushFocusKey,
} from '../focus/focusRestoration.js';
import {
  ContextOption,
  DEFAULT_CONTEXT_OPTIONS,
  buildMediaContextMenu,
} from '../components/contextMenuBuilder.js';
import TiwiFullscreenLoader from '../components/TiwiFullscreenLoader.jsx';
import FeaturedSection from '../components/sections/FeaturedSection.jsx';
import ContinueWatchingSection from '../components/sections/ContinueWatchingSection.jsx';
import MoviesRowSection from '../components/sections/MoviesRowSection.jsx';
import MoviesGridSection from '../components/sections/MoviesGridSection.jsx';
import ErrorSection from '../components/sections/ErrorSection.jsx';

function appendUniqueMovies(existing, incoming) {
  const seen = new Set(existing.map((m) => m.url));
  const out = [...existing];
  for (const item of incoming) {
    if (!seen.has(item.url)) {
      seen.add(item.url);
      out.push(item);
    }
  }
  return out;
}

export default function HomeScreen({ onNavigate, onOpenOverlay, onCloseOverlay }) {
  const [isLoading, setIsLoading] = useState(true);
  const [errorMessage, setErrorMessage] = useState(null);
  const [featuredItems, setFeaturedItems] = useState([]);
  const [sections, setSections] = useState([]);
  const [loadingMoreKey, setLoadingMoreKey] = useState(null);

  const favorites = useFavorites();
  const watchlist = useWatchlist();
  const progressItems = useProgressItems();
  const newEpisodes = useNewEpisodes();

  const loadTokenRef = useRef(0);
  const firstFavEffectRef = useRef(true);

  const progressMap = useMemo(() => buildProgressMap(progressItems), [progressItems]);
  const distinctProgress = useMemo(() => distinctBySeries(progressItems), [progressItems]);
  const continueWatching = useMemo(
    () => toActiveContinueWatchingItems(distinctProgress, Date.now(), progressItems),
    [distinctProgress, progressItems],
  );
  const jumpBackIn = useMemo(
    () => toJumpBackInItems(distinctProgress, Date.now(), progressItems),
    [distinctProgress, progressItems],
  );
  const filteredWatchlist = useMemo(
    () => filterUnwatchedWatchlist(watchlist, progressItems),
    [watchlist, progressItems],
  );

  const loadHomeData = useCallback(async () => {
    const token = ++loadTokenRef.current;
    setIsLoading(true);
    setErrorMessage(null);

    try {
      const [trendingToday, popularMovies, trendingTv, forYou] = await Promise.all([
        fetchFeedPage(TmdbFeed.TRENDING_ALL_DAY, 1),
        fetchFeedPage(TmdbFeed.MOVIES_POPULAR, 1),
        fetchFeedPage(TmdbFeed.TRENDING_TV_WEEK, 1),
        getPersonalizedRecommendations(1),
      ]);

      if (token !== loadTokenRef.current) return;

      const tmdbFeeds = [trendingToday, popularMovies, trendingTv];
      const allEmpty = tmdbFeeds.every((f) => f.movies.length === 0);
      const firstErr = tmdbFeeds.find((f) => f.errorMessage)?.errorMessage;
      if (allEmpty && firstErr) {
        setErrorMessage(firstErr);
        return;
      }

      const hero =
        trendingToday.featuredItems.length > 0
          ? trendingToday.featuredItems
          : popularMovies.featuredItems;
      setFeaturedItems(hero);

      const built = [];
      if (forYou.movies.length > 0) {
        built.push({
          titleKey: 'home_for_you',
          feed: TmdbFeed.PERSONALIZED_FOR_YOU,
          movies: forYou.movies,
          page: 1,
          hasMore: forYou.hasMore,
        });
      }
      if (trendingToday.movies.length > 0) {
        built.push({
          titleKey: 'home_trending_today',
          feed: TmdbFeed.TRENDING_ALL_DAY,
          movies: trendingToday.movies,
          page: trendingToday.page,
          hasMore: trendingToday.hasMore,
        });
      }
      if (popularMovies.movies.length > 0) {
        built.push({
          titleKey: 'home_recommended',
          feed: TmdbFeed.MOVIES_POPULAR,
          movies: popularMovies.movies,
          page: popularMovies.page,
          hasMore: popularMovies.hasMore,
        });
      }
      if (trendingTv.movies.length > 0) {
        built.push({
          titleKey: 'home_trending_series',
          feed: TmdbFeed.TRENDING_TV_WEEK,
          movies: trendingTv.movies,
          page: trendingTv.page,
          hasMore: trendingTv.hasMore,
        });
      }
      setSections(built);
    } catch (err) {
      if (token === loadTokenRef.current) {
        setErrorMessage(err?.message || t('common_couldnt_load_movies'));
      }
    } finally {
      if (token === loadTokenRef.current) {
        setIsLoading(false);
      }
    }
  }, []);

  useEffect(() => {
    loadHomeData();
  }, [loadHomeData]);

  // Live-update "For You" 500ms after favorites change
  const favKeySignature = useMemo(() => favorites.map((f) => f.url).join(','), [favorites]);
  useEffect(() => {
    if (firstFavEffectRef.current) {
      firstFavEffectRef.current = false;
      return undefined;
    }
    const timer = setTimeout(async () => {
      const res = await getPersonalizedRecommendations(1);
      setSections((prev) => {
        const withoutForYou = prev.filter((s) => s.titleKey !== 'home_for_you');
        if (res.movies.length === 0) return withoutForYou;
        return [
          {
            titleKey: 'home_for_you',
            feed: TmdbFeed.PERSONALIZED_FOR_YOU,
            movies: res.movies,
            page: 1,
            hasMore: res.hasMore,
          },
          ...withoutForYou,
        ];
      });
    }, 500);
    return () => clearTimeout(timer);
  }, [favKeySignature]);

  const handleLoadMoreSection = async (titleKey) => {
    if (loadingMoreKey) return;
    const sec = sections.find((s) => s.titleKey === titleKey);
    if (!sec || !sec.hasMore) return;

    setLoadingMoreKey(titleKey);
    try {
      const next =
        sec.feed === TmdbFeed.PERSONALIZED_FOR_YOU
          ? await getPersonalizedRecommendations(sec.page + 1)
          : await fetchFeedPage(sec.feed, sec.page + 1);
      setSections((prev) =>
        prev.map((s) =>
          s.titleKey === titleKey
            ? {
                ...s,
                movies: appendUniqueMovies(s.movies, next.movies),
                page: s.page + 1,
                hasMore: next.hasMore,
              }
            : s,
        ),
      );
    } finally {
      setLoadingMoreKey(null);
    }
  };

  const openDetails = (request, autoPlay = false, episodeUrl = null, focusKey = null) => {
    if (focusKey) pushFocusKey(focusKey);
    onNavigate?.({
      name: 'details',
      request,
      autoPlay,
      episodeUrl,
    });
  };

  const handleContinueWatchingClick = (item, prefix) => {
    const isEp = item.parentUrl && item.parentUrl !== item.url;
    const targetUrl = isEp ? item.parentUrl : item.url;
    const epUrl = isEp && item.kind !== 'NextEpisode' ? item.url : null;
    openDetails(
      { url: targetUrl, isTvShow: Boolean(isEp) },
      true,
      epUrl,
      `${prefix}${item.url}`,
    );
  };

  const openStandardContextMenu = (movie, focusKey) => {
    if (focusKey) pushFocusKey(focusKey);
    onOpenOverlay?.(
      buildMediaContextMenu({
        movie,
        progressMap,
        options: DEFAULT_CONTEXT_OPTIONS,
        onClose: onCloseOverlay,
      }),
    );
  };

  const openContinueContextMenu = (item, prefix, itemsList) => {
    const isMovie = !item.parentUrl || item.parentUrl === item.url;
    const movie = {
      url: item.url,
      seriesUrl: item.parentUrl,
      titlePl: item.titlePl,
      posterUrl: item.posterUrl,
      seasonNumber: item.season,
      episodeNumber: item.episode,
      seriesTitle: item.seriesTitle,
      episodeTitle: item.episodeTitle,
      hasNextEpisode: item.hasNextEpisode,
      isTvShow: !isMovie,
    };
    const options = isMovie
      ? [
          ContextOption.OPEN_DETAILS,
          ContextOption.REMOVE_FROM_CONTINUE_WATCHING,
          ContextOption.MARK_AS_WATCHED,
          ContextOption.MARK_AS_NOT_WATCHED,
          ContextOption.FAVORITES,
          ContextOption.WATCHLIST,
        ]
      : [
          ContextOption.OPEN_DETAILS,
          ContextOption.REMOVE_FROM_CONTINUE_WATCHING,
          ContextOption.MARK_AS_WATCHED,
          ContextOption.MARK_AS_NOT_WATCHED,
        ];

    const focusKey = `${prefix}${item.url}`;
    pushFocusKey(focusKey);

    const lastFeaturedKey =
      featuredItems.length > 0
        ? `${SectionFocusPrefix.FEATURED}${featuredItems[featuredItems.length - 1].url}`
        : null;

    onOpenOverlay?.(
      buildMediaContextMenu({
        movie,
        progressMap,
        options,
        onOpenDetails: (m) =>
          openDetails({ url: m.seriesUrl || m.url, isTvShow: m.isTvShow }, false, null),
        onAfterMutate: (action) => {
          if (action === 'remove_continue' || action === 'mark_watched') {
            const fallback = computeNeighborFallbackKey(itemsList, item.url, prefix, lastFeaturedKey);
            if (fallback) setTimeout(() => focusElementByKey(fallback), 80);
          }
        },
        onClose: onCloseOverlay,
      }),
    );
  };

  if (isLoading) return <TiwiFullscreenLoader />;
  if (errorMessage) {
    return (
      <div className="screen-content">
        <div className="screen-top-spacer" />
        <ErrorSection message={errorMessage} onRetry={loadHomeData} />
      </div>
    );
  }

  return (
    <div className="screen-content">
      {featuredItems.length > 0 ? (
        <FeaturedSection
          items={featuredItems}
          progressMap={progressMap}
          onItemClick={(item) =>
            openDetails(
              item.detailsRequest || { url: item.url, isTvShow: item.isTvShow },
              false,
              null,
              `${SectionFocusPrefix.FEATURED}${item.url}`,
            )
          }
          onItemLongClick={(item) =>
            openStandardContextMenu(item, `${SectionFocusPrefix.FEATURED}${item.url}`)
          }
        />
      ) : (
        <div className="screen-top-spacer" />
      )}

      <ContinueWatchingSection
        title={t('common_continue_watching')}
        items={continueWatching}
        sectionPrefix={SectionFocusPrefix.CONTINUE_WATCHING}
        onItemClick={(item) =>
          handleContinueWatchingClick(item, SectionFocusPrefix.CONTINUE_WATCHING)
        }
        onItemLongClick={(item) =>
          openContinueContextMenu(item, SectionFocusPrefix.CONTINUE_WATCHING, continueWatching)
        }
      />

      <MoviesRowSection
        title={t('common_new_episodes')}
        items={newEpisodes}
        progressMap={progressMap}
        onItemClick={(item) => {
          clearNewEpisode(item.url);
          openDetails(
            item.detailsRequest || { url: item.seriesUrl || item.url, isTvShow: true },
            true,
            item.url,
          );
        }}
        onItemLongClick={(item) =>
          openStandardContextMenu(
            item,
            `${SectionFocusPrefix.moviesRowPrefix(t('common_new_episodes'))}${item.url}`,
          )
        }
      />

      <MoviesRowSection
        title={t('common_watchlist')}
        items={filteredWatchlist}
        progressMap={progressMap}
        onItemClick={(item) =>
          openDetails(
            item.detailsRequest || { url: item.url, isTvShow: item.isTvShow },
            false,
            null,
            `${SectionFocusPrefix.moviesRowPrefix(t('common_watchlist'))}${item.url}`,
          )
        }
        onItemLongClick={(item) =>
          openStandardContextMenu(
            item,
            `${SectionFocusPrefix.moviesRowPrefix(t('common_watchlist'))}${item.url}`,
          )
        }
      />

      <ContinueWatchingSection
        title={t('common_jump_back_in')}
        items={jumpBackIn}
        sectionPrefix={SectionFocusPrefix.JUMP_BACK_IN}
        onItemClick={(item) =>
          handleContinueWatchingClick(item, SectionFocusPrefix.JUMP_BACK_IN)
        }
        onItemLongClick={(item) =>
          openContinueContextMenu(item, SectionFocusPrefix.JUMP_BACK_IN, jumpBackIn)
        }
      />

      {sections.map((sec) => (
        <MoviesGridSection
          key={sec.titleKey}
          title={t(sec.titleKey)}
          items={sec.movies}
          progressMap={progressMap}
          hasMore={sec.hasMore}
          isLoadingMore={loadingMoreKey === sec.titleKey}
          showLoadMoreButton={true}
          focusPrefix={`home_${sec.titleKey}_`}
          onItemClick={(m) =>
            openDetails(
              m.detailsRequest || { url: m.url, isTvShow: m.isTvShow },
              false,
              null,
              `home_${sec.titleKey}_${m.url}`,
            )
          }
          onItemLongClick={(m) => openStandardContextMenu(m, `home_${sec.titleKey}_${m.url}`)}
          onLoadNextPage={() => handleLoadMoreSection(sec.titleKey)}
        />
      ))}
    </div>
  );
}
