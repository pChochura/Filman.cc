import { matchesGenre } from '../../i18n/locale.js';
import {
  DEFAULT_FILTER_STATE,
  MediaTypeFilter,
  getDecadeOption,
  isFilterActive,
  matchesDecadeYear,
} from '../models/filterState.js';

export function applyLocalFilter(items, filter = DEFAULT_FILTER_STATE) {
  if (!Array.isArray(items)) return [];
  if (!isFilterActive(filter)) return items;

  const decadeOption = getDecadeOption(filter.decade);
  const selectedGenres = filter.selectedGenreIds || [];

  const filtered = items.filter((item) => {
    let matchesType = true;
    if (filter.mediaTypeFilter === MediaTypeFilter.MOVIES) matchesType = !item.isTvShow;
    else if (filter.mediaTypeFilter === MediaTypeFilter.TV_SHOWS) matchesType = Boolean(item.isTvShow);

    const matchesGenres = selectedGenres.every((genreId) =>
      matchesGenre(item.genreIds || [], genreId, Boolean(item.isTvShow)),
    );
    const matchesDecade = matchesDecadeYear(decadeOption, item.year);
    const matchesRating =
      filter.minRating === null ||
      filter.minRating === undefined ||
      (item.filmanRating || 0) >= filter.minRating;

    return matchesType && matchesGenres && matchesDecade && matchesRating;
  });

  switch (filter.sortBy) {
    case 'RATING_DESC':
      return [...filtered].sort((a, b) => (b.filmanRating ?? -1) - (a.filmanRating ?? -1));
    case 'RELEASE_DATE_DESC':
      return [...filtered].sort((a, b) => (b.year ?? -Infinity) - (a.year ?? -Infinity));
    case 'VOTE_COUNT_DESC':
      return [...filtered].sort((a, b) => (b.voteCount ?? -1) - (a.voteCount ?? -1));
    case 'POPULARITY_DESC':
    default:
      return filtered;
  }
}
