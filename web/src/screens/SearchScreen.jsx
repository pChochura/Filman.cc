import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { t } from '../i18n/index.js';
import {
  TmdbFeed,
  fetchFeedPage,
  getMergedCategories,
  searchMulti,
} from '../data/api/tmdbClient.js';
import { applyLocalFilter } from '../data/api/mediaFilterMatcher.js';
import {
  DEFAULT_FILTER_STATE,
  MediaTypeFilter,
  activeFilterCount,
  isFilterActive,
  isSingleGenreOnly,
} from '../data/models/filterState.js';
import {
  addSearchQuery,
  clearSearchHistory,
  removeSearchQuery,
  useSearchHistory,
} from '../data/local/searchHistoryStore.js';
import { buildProgressMap, useProgressItems } from '../data/local/progressStore.js';
import { useFavorites } from '../data/local/favoritesStore.js';
import { useWatchlist } from '../data/local/watchlistStore.js';
import { pushFocusKey } from '../focus/focusRestoration.js';
import { buildMediaContextMenu } from '../components/contextMenuBuilder.js';
import { buildFilterMenu } from '../components/filterMenuBuilder.js';
import LoadingMoreFooter from '../components/LoadingMoreFooter.jsx';
import SearchBarSection from '../components/sections/SearchBarSection.jsx';
import SearchHistorySection from '../components/sections/SearchHistorySection.jsx';
import CategoriesGridSection from '../components/sections/CategoriesGridSection.jsx';
import MoviesGridSection from '../components/sections/MoviesGridSection.jsx';
import { EmptyFilterResultsSection } from '../components/sections/FilterBarSection.jsx';
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

export default function SearchScreen({ onNavigate, onOpenOverlay, onCloseOverlay }) {
  const [query, setQuery] = useState('');
  const [filterState, setFilterState] = useState(DEFAULT_FILTER_STATE);
  const [categories, setCategories] = useState([]);
  const [isSearching, setIsSearching] = useState(false);
  const [errorMessage, setErrorMessage] = useState(null);
  const [rawSearchResults, setRawSearchResults] = useState([]);
  const [discoverSections, setDiscoverSections] = useState([]);
  const [loadingMoreKey, setLoadingMoreKey] = useState(null);
  const [isFilterOverlayOpen, setIsFilterOverlayOpen] = useState(false);

  const searchHistory = useSearchHistory();
  const progressItems = useProgressItems();
  useFavorites();
  useWatchlist();
  const progressMap = useMemo(() => buildProgressMap(progressItems), [progressItems]);

  const inputRef = useRef(null);
  const searchTokenRef = useRef(0);
  const historyTimerRef = useRef(null);

  useEffect(() => {
    getMergedCategories().then(setCategories);
    return () => clearTimeout(historyTimerRef.current);
  }, []);

  const scheduleHistorySave = useCallback((q) => {
    clearTimeout(historyTimerRef.current);
    const trimmed = String(q || '').trim();
    if (!trimmed) return;
    historyTimerRef.current = setTimeout(() => {
      addSearchQuery(trimmed);
    }, 10_000);
  }, []);

  const executeTextSearch = useCallback(
    async (targetQuery) => {
      const trimmed = String(targetQuery || '').trim();
      if (!trimmed) return;
      const token = ++searchTokenRef.current;
      setIsSearching(true);
      setErrorMessage(null);
      setDiscoverSections([]);
      scheduleHistorySave(trimmed);

      const res = await searchMulti(trimmed, 1);
      if (token !== searchTokenRef.current) return;
      if (res.errorMessage && res.movies.length === 0 && res.tvShows.length === 0) {
        setErrorMessage(res.errorMessage);
        setRawSearchResults([]);
      } else {
        setRawSearchResults([...res.movies, ...res.tvShows]);
      }
      setIsSearching(false);
    },
    [scheduleHistorySave],
  );

  const executeDiscoverSearch = useCallback(async (filter) => {
    const token = ++searchTokenRef.current;
    setIsSearching(true);
    setErrorMessage(null);
    setRawSearchResults([]);

    const singleGenre = isSingleGenreOnly(filter);
    const tasks = [];

    if (singleGenre) {
      const genreId = filter.selectedGenreIds[0];
      tasks.push({
        titleKey: 'search_results_movies',
        feed: { type: TmdbFeed.DISCOVER_BY_GENRE, mediaType: 'MOVIE', genreId },
      });
      tasks.push({
        titleKey: 'common_results_tv_shows',
        feed: { type: TmdbFeed.DISCOVER_BY_GENRE, mediaType: 'TV', genreId },
      });
    } else {
      if (filter.mediaTypeFilter !== MediaTypeFilter.TV_SHOWS) {
        tasks.push({
          titleKey: 'search_results_movies',
          feed: { type: TmdbFeed.DISCOVER_FILTERED, mediaType: 'MOVIE', filter },
        });
      }
      if (filter.mediaTypeFilter !== MediaTypeFilter.MOVIES) {
        tasks.push({
          titleKey: 'common_results_tv_shows',
          feed: { type: TmdbFeed.DISCOVER_FILTERED, mediaType: 'TV', filter },
        });
      }
    }

    const results = await Promise.all(
      tasks.map(async (task) => ({
        ...task,
        res: await fetchFeedPage(task.feed, 1),
      })),
    );
    if (token !== searchTokenRef.current) return;

    const built = results
      .filter((r) => r.res.movies.length > 0)
      .map((r) => ({
        titleKey: r.titleKey,
        feed: r.feed,
        movies: r.res.movies,
        page: r.res.page,
        hasMore: r.res.hasMore,
      }));
    const firstErr = results.find((r) => r.res.errorMessage)?.res?.errorMessage;
    if (built.length === 0 && firstErr) {
      setErrorMessage(firstErr);
    }
    setDiscoverSections(built);
    setIsSearching(false);
  }, []);

  // 1000ms debounce on text typing
  useEffect(() => {
    const trimmed = query.trim();
    if (!trimmed) {
      clearTimeout(historyTimerRef.current);
      setRawSearchResults([]);
      if (isFilterActive(filterState)) {
        executeDiscoverSearch(filterState);
      } else {
        setDiscoverSections([]);
        setIsSearching(false);
        setErrorMessage(null);
      }
      return undefined;
    }

    const timer = setTimeout(() => {
      executeTextSearch(trimmed);
    }, 1000);
    return () => clearTimeout(timer);
  }, [query]); // eslint-disable-line react-hooks/exhaustive-deps

  // Re-run discover when filters change and query is blank
  useEffect(() => {
    if (query.trim()) return;
    if (isFilterActive(filterState)) {
      executeDiscoverSearch(filterState);
    } else {
      setDiscoverSections([]);
      setErrorMessage(null);
      setIsSearching(false);
    }
  }, [filterState, query, executeDiscoverSearch]);

  // Keep filter menu live while open
  useEffect(() => {
    if (!isFilterOverlayOpen) return;
    onOpenOverlay?.(
      buildFilterMenu({
        filterState,
        availableGenres: categories,
        showMediaTypeFilter: true,
        onSetMediaTypeFilter: (mediaTypeFilter) =>
          setFilterState((prev) => ({ ...prev, mediaTypeFilter })),
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
  }, [isFilterOverlayOpen, filterState, categories, onOpenOverlay]);

  const filteredTextResults = useMemo(
    () => applyLocalFilter(rawSearchResults, filterState),
    [rawSearchResults, filterState],
  );

  const handleLoadMoreDiscover = async (titleKey) => {
    if (loadingMoreKey) return;
    const sec = discoverSections.find((s) => s.titleKey === titleKey);
    if (!sec || !sec.hasMore) return;
    setLoadingMoreKey(titleKey);
    try {
      const next = await fetchFeedPage(sec.feed, sec.page + 1);
      setDiscoverSections((prev) =>
        prev.map((s) =>
          s.titleKey === titleKey
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

  const openDetails = (movie, focusKey) => {
    if (focusKey) pushFocusKey(focusKey);
    onNavigate?.({
      name: 'details',
      request: movie.detailsRequest || { url: movie.url, isTvShow: movie.isTvShow },
      autoPlay: false,
      episodeUrl: null,
    });
  };

  const openContextMenu = (movie, focusKey) => {
    if (focusKey) pushFocusKey(focusKey);
    onOpenOverlay?.(
      buildMediaContextMenu({
        movie,
        progressMap,
        onClose: onCloseOverlay,
      }),
    );
  };

  const hasQuery = query.trim().length > 0;
  const filtersActive = isFilterActive(filterState);
  const showDiscoveryIdle =
    !errorMessage && !isSearching && !hasQuery && !filtersActive && discoverSections.length === 0;
  const hasVisibleResults = hasQuery
    ? filteredTextResults.length > 0
    : discoverSections.length > 0;

  return (
    <div className="screen-content">
      <SearchBarSection
        inputRef={inputRef}
        query={query}
        onQueryChange={setQuery}
        onSubmit={(val) => executeTextSearch(val)}
        onClear={() => {
          clearTimeout(historyTimerRef.current);
          setQuery('');
          setFilterState(DEFAULT_FILTER_STATE);
          setRawSearchResults([]);
          setDiscoverSections([]);
          setErrorMessage(null);
        }}
        onOpenFilters={() => setIsFilterOverlayOpen(true)}
        activeFilterCount={activeFilterCount(filterState)}
      />

      {showDiscoveryIdle && (
        <>
          <SearchHistorySection
            history={searchHistory}
            onSelectQuery={(item) => {
              setQuery(item);
              inputRef.current?.focus();
              executeTextSearch(item);
            }}
            onLongClickQuery={(item) => {
              onOpenOverlay?.({
                title: item,
                items: [
                  {
                    id: 'remove_history_item',
                    type: 'button',
                    label: t('search_remove_from_history'),
                    onClick: () => {
                      removeSearchQuery(item);
                      onCloseOverlay?.();
                    },
                  },
                ],
              });
            }}
            onClearAll={() => clearSearchHistory()}
          />
          <CategoriesGridSection
            categories={categories}
            hasSearchHistory={Boolean(searchHistory && searchHistory.length > 0)}
            onCategoryClick={(cat) => {
              setQuery('');
              setFilterState({ ...DEFAULT_FILTER_STATE, selectedGenreIds: [cat.id] });
            }}
          />
        </>
      )}

      {errorMessage && (
        <ErrorSection
          message={errorMessage}
          onRetry={() => {
            if (hasQuery) executeTextSearch(query);
            else if (filtersActive) executeDiscoverSearch(filterState);
          }}
        />
      )}

      {isSearching && <LoadingMoreFooter label={t('common_loading')} />}

      {!errorMessage && !isSearching && (hasQuery || filtersActive) && !hasVisibleResults && (
        <EmptyFilterResultsSection
          onReset={() => {
            if (filtersActive) {
              setFilterState(DEFAULT_FILTER_STATE);
            } else {
              setQuery('');
              setRawSearchResults([]);
              inputRef.current?.focus();
            }
          }}
        />
      )}

      {!errorMessage && !isSearching && hasQuery && filteredTextResults.length > 0 && (
        <MoviesGridSection
          title={t('common_results')}
          items={filteredTextResults}
          progressMap={progressMap}
          hasMore={false}
          showLoadMoreButton={false}
          focusPrefix="search_res_"
          onItemClick={(m) => openDetails(m, `search_res_${m.url}`)}
          onItemLongClick={(m) => openContextMenu(m, `search_res_${m.url}`)}
        />
      )}

      {!errorMessage &&
        !isSearching &&
        !hasQuery &&
        discoverSections.map((sec) => (
          <MoviesGridSection
            key={sec.titleKey}
            title={t(sec.titleKey)}
            items={sec.movies}
            progressMap={progressMap}
            hasMore={sec.hasMore}
            isLoadingMore={loadingMoreKey === sec.titleKey}
            showLoadMoreButton={true}
            focusPrefix={`search_disc_${sec.titleKey}_`}
            onItemClick={(m) => openDetails(m, `search_disc_${sec.titleKey}_${m.url}`)}
            onItemLongClick={(m) => openContextMenu(m, `search_disc_${sec.titleKey}_${m.url}`)}
            onLoadNextPage={() => handleLoadMoreDiscover(sec.titleKey)}
          />
        ))}
    </div>
  );
}
