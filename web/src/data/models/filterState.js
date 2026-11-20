export const MediaTypeFilter = {
  ALL: 'ALL',
  MOVIES: 'MOVIES',
  TV_SHOWS: 'TV_SHOWS',
};

export const SortOption = {
  POPULARITY_DESC: {
    id: 'POPULARITY_DESC',
    labelKey: 'sort_popularity_desc',
    movieApiValue: 'popularity.desc',
    tvApiValue: 'popularity.desc',
  },
  RATING_DESC: {
    id: 'RATING_DESC',
    labelKey: 'sort_rating_desc',
    movieApiValue: 'vote_average.desc',
    tvApiValue: 'vote_average.desc',
  },
  RELEASE_DATE_DESC: {
    id: 'RELEASE_DATE_DESC',
    labelKey: 'sort_release_date_desc',
    movieApiValue: 'primary_release_date.desc',
    tvApiValue: 'first_air_date.desc',
  },
  VOTE_COUNT_DESC: {
    id: 'VOTE_COUNT_DESC',
    labelKey: 'sort_vote_count_desc',
    movieApiValue: 'vote_count.desc',
    tvApiValue: 'vote_count.desc',
  },
};

export const SORT_OPTIONS = [
  SortOption.POPULARITY_DESC,
  SortOption.RATING_DESC,
  SortOption.RELEASE_DATE_DESC,
  SortOption.VOTE_COUNT_DESC,
];

export const DecadeOption = {
  ALL: { id: 'ALL', labelKey: 'filter_all_years', fromYear: null, toYear: null },
  YEAR_2024_PLUS: { id: 'YEAR_2024_PLUS', labelKey: 'filter_decade_2024_plus', fromYear: 2024, toYear: null },
  YEAR_2020_2023: { id: 'YEAR_2020_2023', labelKey: 'filter_decade_2020_2023', fromYear: 2020, toYear: 2023 },
  YEAR_2010S: { id: 'YEAR_2010S', labelKey: 'filter_decade_2010s', fromYear: 2010, toYear: 2019 },
  YEAR_2000S: { id: 'YEAR_2000S', labelKey: 'filter_decade_2000s', fromYear: 2000, toYear: 2009 },
  YEAR_1990S: { id: 'YEAR_1990S', labelKey: 'filter_decade_1990s', fromYear: 1990, toYear: 1999 },
  CLASSICS: { id: 'CLASSICS', labelKey: 'filter_decade_classics', fromYear: null, toYear: 1989 },
};

export const DECADE_OPTIONS = [
  DecadeOption.ALL,
  DecadeOption.YEAR_2024_PLUS,
  DecadeOption.YEAR_2020_2023,
  DecadeOption.YEAR_2010S,
  DecadeOption.YEAR_2000S,
  DecadeOption.YEAR_1990S,
  DecadeOption.CLASSICS,
];

export function matchesDecadeYear(decadeOption, year) {
  if (!decadeOption || decadeOption.id === 'ALL') return true;
  if (year === null || year === undefined) return false;
  if (decadeOption.fromYear !== null && year < decadeOption.fromYear) return false;
  if (decadeOption.toYear !== null && year > decadeOption.toYear) return false;
  return true;
}

export const RATING_OPTIONS = [null, 6, 7, 8, 9];

export const DEFAULT_FILTER_STATE = {
  sortBy: 'POPULARITY_DESC',
  selectedGenreIds: [],
  decade: 'ALL',
  minRating: null,
  mediaTypeFilter: MediaTypeFilter.ALL,
};

export function getSortOption(id) {
  return SortOption[id] || SortOption.POPULARITY_DESC;
}

export function getDecadeOption(id) {
  return DecadeOption[id] || DecadeOption.ALL;
}

export function isFilterActive(state) {
  const s = state || DEFAULT_FILTER_STATE;
  return (
    s.sortBy !== 'POPULARITY_DESC' ||
    (s.selectedGenreIds && s.selectedGenreIds.length > 0) ||
    s.decade !== 'ALL' ||
    s.minRating !== null ||
    s.mediaTypeFilter !== MediaTypeFilter.ALL
  );
}

export function activeFilterCount(state) {
  const s = state || DEFAULT_FILTER_STATE;
  let count = 0;
  if (s.sortBy !== 'POPULARITY_DESC') count++;
  count += s.selectedGenreIds?.length || 0;
  if (s.decade !== 'ALL') count++;
  if (s.minRating !== null) count++;
  if (s.mediaTypeFilter !== MediaTypeFilter.ALL) count++;
  return count;
}

export function isSingleGenreOnly(state) {
  const s = state || DEFAULT_FILTER_STATE;
  return (
    s.sortBy === 'POPULARITY_DESC' &&
    s.selectedGenreIds?.length === 1 &&
    s.decade === 'ALL' &&
    s.minRating === null &&
    s.mediaTypeFilter === MediaTypeFilter.ALL
  );
}
