import React, { useCallback, useEffect, useRef, useState } from 'react';
import { t } from '../i18n/index.js';
import {
  TmdbFeed,
  fetchFeedPage,
  getMovieCategories,
  getTvCategories,
  loadFeedCatalog,
} from '../data/api/tmdbClient.js';
import {
  DEFAULT_FILTER_STATE,
  activeFilterCount,
  isFilterActive,
} from '../data/models/filterState.js';
import { buildProgressMap, useProgressItems } from '../data/local/progressStore.js';
import { useFavorites } from '../data/local/favoritesStore.js';
import { useWatchlist } from '../data/local/watchlistStore.js';
import { pushFocusKey } from '../focus/focusRestoration.js';
import { buildMediaContextMenu } from '../components/contextMenuBuilder.js';
import { buildFilterMenu } from '../components/filterMenuBuilder.js';
import TiwiFullscreenLoader from '../components/TiwiFullscreenLoader.jsx';
import LoadingMoreFooter from '../components/LoadingMoreFooter.jsx';
import FeaturedSection from '../components/sections/FeaturedSection.jsx';
import MoviesGridSection from '../components/sections/MoviesGridSection.jsx';
import {
  EmptyFilterResultsSection,
  FilterActionRow,
} from '../components/sections/FilterBarSection.jsx';
import ErrorSection from '../components/sections/ErrorSection.jsx';

const MOVIES_SPECS = [
  { feed: TmdbFeed.MOVIES_NOW_PLAYING, titleKey: 'movies_now_playing', includeInFeatured: true },
  { feed: TmdbFeed.MOVIES_POPULAR, titleKey: 'common_most_viewed', includeInFeatured: true },
  { feed: TmdbFeed.MOVIES_TOP_RATED, titleKey: 'common_highest_rating', includeInFeatured: true },
  { feed: TmdbFeed.MOVIES_NEW_RELEASES, titleKey: 'common_recently_added', includeInFeatured: false },
];

const TV_SPECS = [
  { feed: TmdbFeed.TV_ON_THE_AIR, titleKey: 'tvshows_new_episodes', includeInFeatured: true },
  { feed: TmdbFeed.TV_POPULAR, titleKey: 'common_most_viewed', includeInFeatured: true },
  { feed: TmdbFeed.TV_TOP_RATED, titleKey: 'common_highest_rating', includeInFeatured: true },
  { feed: TmdbFeed.TV_NEW_RELEASES, titleKey: 'common_recently_added', includeInFeatured: false },
];

const KIDS_SPECS = [
  { feed: TmdbFeed.KIDS_POPULAR_MOVIES, titleKey: 'kids_popular_movies', includeInFeatured: true },
  { feed: TmdbFeed.KIDS_TV_SHOWS, titleKey: 'kids_tv_shows', includeInFeatured: true },
  { feed: TmdbFeed.KIDS_TOP_RATED_FAMILY, titleKey: 'kids_top_rated_family', includeInFeatured: false },
  { feed: TmdbFeed.KIDS_DISNEY_PIXAR, titleKey: 'kids_disney_pixar', includeInFeatured: false },
  { feed: TmdbFeed.KIDS_DREAMWORKS_GHIBLI, titleKey: 'kids_dreamworks_ghibli', includeInFeatured: false },
];

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

export function CatalogScreen({
  specs,
  mediaType = null,
  hasFilters = false,
  showLoadMoreButton = true,
  onNavigate,
  onOpenOverlay,
  onCloseOverlay,
}) {
  const [isLoading, setIsLoading] = useState(true);
  const [errorMessage, setErrorMessage] = useState(null);
  const [featuredItems, setFeaturedItems] = useState([]);
  const [sections, setSections] = useState([]);
  const [availableGenres, setAvailableGenres] = useState([]);
  const [filterState, setFilterState] = useState(DEFAULT_FILTER_STATE);
  const [filteredSection, setFilteredSection] = useState(null);
  const [isFiltering, setIsFiltering] = useState(false);
  const [filterError, setFilterError] = useState(null);
  const [loadingMoreKey, setLoadingMoreKey] = useState(null);
  const [isFilterOverlayOpen, setIsFilterOverlayOpen] = useState(false);

  const progressItems = useProgressItems();
  useFavorites();
  useWatchlist();
  const progressMap = buildProgressMap(progressItems);

  const loadTokenRef = useRef(0);

  const loadInitial = useCallback(async () => {
    const token = ++loadTokenRef.current;
    setIsLoading(true);
    setErrorMessage(null);

    try {
      const [catalog, genres] = await Promise.all([
        loadFeedCatalog(specs),
        hasFilters
          ? mediaType === 'TV'
            ? getTvCategories()
            : getMovieCategories()
          : Promise.resolve([]),
      ]);
      if (token !== loadTokenRef.current) return;

      const builtSections = catalog.entries.map(({ spec, result }) => ({
        titleKey: spec.titleKey,
        feed: spec.feed,
        movies: result.movies,
        page: result.page,
        hasMore: result.hasMore,
        errorMessage: result.errorMessage,
      }));

      const allEmpty = builtSections.every((s) => s.movies.length === 0);
      const firstErr = builtSections.find((s) => s.errorMessage)?.errorMessage;
      if (allEmpty && firstErr) {
        setErrorMessage(firstErr);
      } else {
        setFeaturedItems(catalog.featuredItems);
        setSections(builtSections);
        setAvailableGenres(genres);
      }
    } catch (err) {
      if (token === loadTokenRef.current) {
        setErrorMessage(err?.message || t('common_couldnt_load_movies'));
      }
    } finally {
      if (token === loadTokenRef.current) {
        setIsLoading(false);
      }
    }
  }, [specs, hasFilters, mediaType]);

  useEffect(() => {
    loadInitial();
  }, [loadInitial]);

  // Run filtered discover when filterState becomes active
  useEffect(() => {
    if (!hasFilters) return;
    if (!isFilterActive(filterState)) {
      setFilteredSection(null);
      setFilterError(null);
      setIsFiltering(false);
      return;
    }

    let cancelled = false;
    setIsFiltering(true);
    setFilterError(null);

    const feed = { type: TmdbFeed.DISCOVER_FILTERED, mediaType, filter: filterState };
    fetchFeedPage(feed, 1).then((res) => {
      if (cancelled) return;
      if (res.errorMessage && res.movies.length === 0) {
        setFilterError(res.errorMessage);
        setFilteredSection(null);
      } else {
        setFilteredSection({
          titleKey: 'filter_results_title',
          feed,
          movies: res.movies,
          page: res.page,
          hasMore: res.hasMore,
        });
      }
      setIsFiltering(false);
    });

    return () => {
      cancelled = true;
    };
  }, [hasFilters, mediaType, filterState]);

  const openContextMenu = useCallback(
    (movie, focusKey) => {
      if (focusKey) pushFocusKey(focusKey);
      onOpenOverlay?.(
        buildMediaContextMenu({
          movie,
          progressMap,
          onClose: onCloseOverlay,
        }),
      );
    },
    [progressMap, onOpenOverlay, onCloseOverlay],
  );

  // Rebuild live filter menu when filterState updates while overlay is open
  useEffect(() => {
    if (!isFilterOverlayOpen || !hasFilters) return;
    onOpenOverlay?.(
      buildFilterMenu({
        filterState,
        availableGenres,
        showMediaTypeFilter: false,
        onSetSortOption: (sortBy) => setFilterState((prev) => ({ ...prev, sortBy })),
        onToggleGenre: (genreId) =>
          setFilterState((prev) => {
            const exists = prev.selectedGenreIds.includes(genreId);
            return {
              ...prev,
              selectedGenreIds: exists
                ? prev.selectedGenreIds.filter((id) => id !== genreId)
                : [...prev.selectedGenreIds, genreId],
            };
          }),
        onClearGenres: () => setFilterState((prev) => ({ ...prev, selectedGenreIds: [] })),
        onSetDecade: (decade) => setFilterState((prev) => ({ ...prev, decade })),
        onSetMinRating: (minRating) => setFilterState((prev) => ({ ...prev, minRating })),
        onResetFilters: () => setFilterState(DEFAULT_FILTER_STATE),
      }),
      () => setIsFilterOverlayOpen(false),
    );
  }, [isFilterOverlayOpen, filterState, availableGenres, hasFilters, onOpenOverlay]);

  const handleLoadMoreSection = async (sectionTitleKey) => {
    if (loadingMoreKey) return;
    const sec = sections.find((s) => s.titleKey === sectionTitleKey);
    if (!sec || !sec.hasMore) return;

    setLoadingMoreKey(sectionTitleKey);
    try {
      const next = await fetchFeedPage(sec.feed, sec.page + 1);
      setSections((prev) =>
        prev.map((s) =>
          s.titleKey === sectionTitleKey
            ? {
                ...s,
                movies: appendUniqueMovies(s.movies, next.movies),
                page: next.page,
                hasMore: next.hasMore,
              }
            : s,
        ),
      );
    } finally {
      setLoadingMoreKey(null);
    }
  };

  const handleLoadMoreFiltered = async () => {
    if (!filteredSection || !filteredSection.hasMore || loadingMoreKey) return;
    setLoadingMoreKey('filtered');
    try {
      const next = await fetchFeedPage(filteredSection.feed, filteredSection.page + 1);
      setFilteredSection((prev) =>
        prev
          ? {
              ...prev,
              movies: appendUniqueMovies(prev.movies, next.movies),
              page: next.page,
              hasMore: next.hasMore,
            }
          : null,
      );
    } finally {
      setLoadingMoreKey(null);
    }
  };

  const handleItemClick = (movie, focusKey) => {
    if (focusKey) pushFocusKey(focusKey);
    onNavigate?.({
      name: 'details',
      request: movie.detailsRequest || { url: movie.url, isTvShow: movie.isTvShow },
      autoPlay: false,
      episodeUrl: null,
    });
  };

  if (isLoading) return <TiwiFullscreenLoader />;
  if (errorMessage) {
    return (
      <div className="screen-content">
        <div className="screen-top-spacer" />
        <ErrorSection message={errorMessage} onRetry={loadInitial} />
      </div>
    );
  }

  const filtersActive = hasFilters && isFilterActive(filterState);

  return (
    <div className="screen-content">
      {!filtersActive && featuredItems.length > 0 ? (
        <FeaturedSection
          items={featuredItems}
          progressMap={progressMap}
          onItemClick={(item) => handleItemClick(item, `featured_${item.url}`)}
          onItemLongClick={(item) => openContextMenu(item, `featured_${item.url}`)}
        />
      ) : (
        <div className="screen-top-spacer" />
      )}

      {filtersActive ? (
        <>
          {isFiltering && <LoadingMoreFooter />}
          {!isFiltering && filterError && (
            <ErrorSection
              message={filterError}
              onRetry={() => setFilterState((prev) => ({ ...prev }))}
            />
          )}
          {!isFiltering &&
            !filterError &&
            (!filteredSection || filteredSection.movies.length === 0) && (
              <EmptyFilterResultsSection onReset={() => setFilterState(DEFAULT_FILTER_STATE)} />
            )}
          {!isFiltering && filteredSection && filteredSection.movies.length > 0 && (
            <MoviesGridSection
              title={t('filter_results_title')}
              headerAction={
                <FilterActionRow
                  activeFilterCount={activeFilterCount(filterState)}
                  onOpenFilterMenu={() => setIsFilterOverlayOpen(true)}
                  onResetFilters={() => setFilterState(DEFAULT_FILTER_STATE)}
                />
              }
              items={filteredSection.movies}
              progressMap={progressMap}
              hasMore={filteredSection.hasMore}
              isLoadingMore={loadingMoreKey === 'filtered'}
              showLoadMoreButton={showLoadMoreButton}
              focusPrefix="filtered_"
              onItemClick={(m) => handleItemClick(m, `filtered_${m.url}`)}
              onItemLongClick={(m) => openContextMenu(m, `filtered_${m.url}`)}
              onLoadNextPage={handleLoadMoreFiltered}
            />
          )}
        </>
      ) : (
        sections.map((sec, idx) => (
          <MoviesGridSection
            key={sec.titleKey}
            title={t(sec.titleKey)}
            headerAction={
              idx === 0 && hasFilters ? (
                <FilterActionRow
                  activeFilterCount={activeFilterCount(filterState)}
                  onOpenFilterMenu={() => setIsFilterOverlayOpen(true)}
                  onResetFilters={() => setFilterState(DEFAULT_FILTER_STATE)}
                />
              ) : null
            }
            items={sec.movies}
            progressMap={progressMap}
            hasMore={sec.hasMore}
            isLoadingMore={loadingMoreKey === sec.titleKey}
            showLoadMoreButton={showLoadMoreButton}
            focusPrefix={`grid_${sec.titleKey}_`}
            onItemClick={(m) => handleItemClick(m, `grid_${sec.titleKey}_${m.url}`)}
            onItemLongClick={(m) => openContextMenu(m, `grid_${sec.titleKey}_${m.url}`)}
            onLoadNextPage={() => handleLoadMoreSection(sec.titleKey)}
          />
        ))
      )}
    </div>
  );
}

export function MoviesScreen(props) {
  return (
    <CatalogScreen
      {...props}
      specs={MOVIES_SPECS}
      mediaType="MOVIE"
      hasFilters={true}
      showLoadMoreButton={true}
    />
  );
}

export function TvShowsScreen(props) {
  return (
    <CatalogScreen
      {...props}
      specs={TV_SPECS}
      mediaType="TV"
      hasFilters={true}
      showLoadMoreButton={true}
    />
  );
}

export function ForKidsScreen(props) {
  return (
    <CatalogScreen
      {...props}
      specs={KIDS_SPECS}
      mediaType={null}
      hasFilters={false}
      showLoadMoreButton={false}
    />
  );
}
