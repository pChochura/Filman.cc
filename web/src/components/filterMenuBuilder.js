import { t, tp } from '../i18n/index.js';
import {
  DECADE_OPTIONS,
  MediaTypeFilter,
  RATING_OPTIONS,
  SORT_OPTIONS,
  getDecadeOption,
  getSortOption,
  isFilterActive,
} from '../data/models/filterState.js';

export function buildFilterMenu({
  filterState,
  availableGenres = [],
  showMediaTypeFilter = false,
  onSetMediaTypeFilter,
  onSetSortOption,
  onToggleGenre,
  onClearGenres,
  onSetDecade,
  onSetMinRating,
  onResetFilters,
}) {
  const items = [];

  if (showMediaTypeFilter) {
    const currentTypeLabel =
      filterState.mediaTypeFilter === MediaTypeFilter.MOVIES
        ? t('filter_media_type_movies')
        : filterState.mediaTypeFilter === MediaTypeFilter.TV_SHOWS
          ? t('filter_media_type_tv_shows')
          : t('filter_media_type_all');

    items.push({
      id: 'menu_filter_media_type',
      type: 'nested',
      label: t('filter_media_type'),
      value: currentTypeLabel,
      items: [
        {
          id: 'mt_all',
          type: 'option',
          label: t('filter_media_type_all'),
          isSelected: filterState.mediaTypeFilter === MediaTypeFilter.ALL,
          onClick: ({ popBack }) => {
            onSetMediaTypeFilter?.(MediaTypeFilter.ALL);
            popBack();
          },
        },
        {
          id: 'mt_movies',
          type: 'option',
          label: t('filter_media_type_movies'),
          isSelected: filterState.mediaTypeFilter === MediaTypeFilter.MOVIES,
          onClick: ({ popBack }) => {
            onSetMediaTypeFilter?.(MediaTypeFilter.MOVIES);
            popBack();
          },
        },
        {
          id: 'mt_tv',
          type: 'option',
          label: t('filter_media_type_tv_shows'),
          isSelected: filterState.mediaTypeFilter === MediaTypeFilter.TV_SHOWS,
          onClick: ({ popBack }) => {
            onSetMediaTypeFilter?.(MediaTypeFilter.TV_SHOWS);
            popBack();
          },
        },
      ],
    });
  }

  const currentSort = getSortOption(filterState.sortBy);
  items.push({
    id: 'menu_filter_sort',
    type: 'nested',
    label: t('filter_sort_by'),
    value: t(currentSort.labelKey),
    items: SORT_OPTIONS.map((opt) => ({
      id: `sort_${opt.id}`,
      type: 'option',
      label: t(opt.labelKey),
      isSelected: filterState.sortBy === opt.id,
      onClick: ({ popBack }) => {
        onSetSortOption?.(opt.id);
        popBack();
      },
    })),
  });

  if (availableGenres.length > 0) {
    const selectedIds = filterState.selectedGenreIds || [];
    let genreValue = t('filter_all_genres');
    if (selectedIds.length === 1) {
      genreValue =
        availableGenres.find((g) => g.id === selectedIds[0])?.name ||
        tp('filter_selected_count', 1);
    } else if (selectedIds.length > 1) {
      genreValue = tp('filter_selected_count', selectedIds.length);
    }

    const genreSubitems = [];
    if (selectedIds.length > 0) {
      genreSubitems.push({
        id: 'btn_clear_genres',
        type: 'button',
        label: t('filter_clear_genres'),
        onClick: () => onClearGenres?.(),
      });
    }
    for (const genre of availableGenres) {
      genreSubitems.push({
        id: `genre_${genre.id}`,
        type: 'option',
        label: genre.name,
        isSelected: selectedIds.includes(genre.id),
        onClick: () => onToggleGenre?.(genre.id),
      });
    }

    items.push({
      id: 'menu_filter_genres',
      type: 'nested',
      label: t('filter_genres'),
      value: genreValue,
      items: genreSubitems,
    });
  }

  const currentDecade = getDecadeOption(filterState.decade);
  items.push({
    id: 'menu_filter_decade',
    type: 'nested',
    label: t('filter_release_period'),
    value: t(currentDecade.labelKey),
    items: DECADE_OPTIONS.map((dec) => ({
      id: `decade_${dec.id}`,
      type: 'option',
      label: t(dec.labelKey),
      isSelected: filterState.decade === dec.id,
      onClick: ({ popBack }) => {
        onSetDecade?.(dec.id);
        popBack();
      },
    })),
  });

  const currentRatingLabel =
    filterState.minRating != null
      ? t('filter_rating_value', Math.round(filterState.minRating))
      : t('filter_any_rating');
  items.push({
    id: 'menu_filter_rating',
    type: 'nested',
    label: t('filter_rating'),
    value: currentRatingLabel,
    items: RATING_OPTIONS.map((r) => ({
      id: `rating_${r ?? 'any'}`,
      type: 'option',
      label: r != null ? t('filter_rating_value', r) : t('filter_any_rating'),
      isSelected: filterState.minRating === r,
      onClick: ({ popBack }) => {
        onSetMinRating?.(r);
        popBack();
      },
    })),
  });

  if (isFilterActive(filterState)) {
    items.push({
      id: 'filter_actions_header',
      type: 'header',
      label: t('filter_header_actions'),
    });
    items.push({
      id: 'btn_reset_filters',
      type: 'button',
      label: t('filter_reset_all'),
      onClick: ({ popBack }) => {
        onResetFilters?.();
        popBack();
      },
    });
  }

  return {
    title: t('filter_menu_title'),
    items,
  };
}
