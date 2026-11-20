import { t } from '../i18n/index.js';
import { isFavorite, toggleFavorite } from '../data/local/favoritesStore.js';
import { isInWatchlist, toggleWatchlist } from '../data/local/watchlistStore.js';
import {
  MARK_AS_WATCHED_PROGRESS_THRESHOLD,
  markAsNotWatched,
  markAsWatched,
  removeProgress,
} from '../data/local/progressStore.js';

export const ContextOption = {
  REMOVE_FROM_CONTINUE_WATCHING: 'REMOVE_FROM_CONTINUE_WATCHING',
  OPEN_DETAILS: 'OPEN_DETAILS',
  MARK_AS_NOT_WATCHED: 'MARK_AS_NOT_WATCHED',
  MARK_AS_WATCHED: 'MARK_AS_WATCHED',
  MARK_PREVIOUS_AS_WATCHED: 'MARK_PREVIOUS_AS_WATCHED',
  FAVORITES: 'FAVORITES',
  WATCHLIST: 'WATCHLIST',
};

export const DEFAULT_CONTEXT_OPTIONS = [
  ContextOption.FAVORITES,
  ContextOption.WATCHLIST,
  ContextOption.MARK_AS_WATCHED,
  ContextOption.MARK_AS_NOT_WATCHED,
];

export function buildMediaContextMenu({
  movie,
  progressMap = {},
  options = DEFAULT_CONTEXT_OPTIONS,
  onOpenDetails,
  onMarkPreviousAsWatched,
  onAfterMutate,
  onClose,
}) {
  if (!movie) return null;
  const progress = progressMap[movie.url] ?? 0;
  const isWatched = progress >= MARK_AS_WATCHED_PROGRESS_THRESHOLD;
  const fav = isFavorite(movie.url);
  const inWatchlist = isInWatchlist(movie.url);

  const items = [];

  if (options.includes(ContextOption.REMOVE_FROM_CONTINUE_WATCHING)) {
    items.push({
      id: 'ctx_remove_continue',
      type: 'button',
      label: t('common_remove_from_continue_watching'),
      onClick: () => {
        removeProgress(movie.url);
        onAfterMutate?.('remove_continue', movie);
        onClose?.();
      },
    });
  }

  if (options.includes(ContextOption.OPEN_DETAILS)) {
    items.push({
      id: 'ctx_open_details',
      type: 'button',
      label: t('common_open_details'),
      onClick: () => {
        onClose?.();
        onOpenDetails?.(movie);
      },
    });
  }

  if (options.includes(ContextOption.MARK_AS_NOT_WATCHED) && isWatched) {
    items.push({
      id: 'ctx_mark_not_watched',
      type: 'button',
      label: t('common_mark_as_not_watched'),
      onClick: () => {
        markAsNotWatched(movie.url);
        onAfterMutate?.('mark_not_watched', movie);
        onClose?.();
      },
    });
  }

  if (options.includes(ContextOption.MARK_AS_WATCHED) && !isWatched) {
    items.push({
      id: 'ctx_mark_watched',
      type: 'button',
      label: t('common_mark_as_watched'),
      onClick: () => {
        markAsWatched(movie);
        onAfterMutate?.('mark_watched', movie);
        onClose?.();
      },
    });
  }

  if (options.includes(ContextOption.MARK_PREVIOUS_AS_WATCHED) && !isWatched) {
    items.push({
      id: 'ctx_mark_prev_watched',
      type: 'button',
      label: t('common_mark_previous_as_watched'),
      onClick: () => {
        onMarkPreviousAsWatched?.(movie);
        onClose?.();
      },
    });
  }

  if (options.includes(ContextOption.FAVORITES)) {
    items.push({
      id: 'ctx_favorites',
      type: 'button',
      label: fav ? t('common_remove_from_favorites') : t('common_add_to_favorites'),
      onClick: () => {
        toggleFavorite(movie);
        onAfterMutate?.('toggle_favorite', movie);
        onClose?.();
      },
    });
  }

  if (options.includes(ContextOption.WATCHLIST)) {
    items.push({
      id: 'ctx_watchlist',
      type: 'button',
      label: inWatchlist ? t('common_remove_from_watchlist') : t('common_add_to_watchlist'),
      onClick: () => {
        toggleWatchlist(movie);
        onAfterMutate?.('toggle_watchlist', movie);
        onClose?.();
      },
    });
  }

  return {
    title: movie.titlePl || '',
    items,
  };
}
