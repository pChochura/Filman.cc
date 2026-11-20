import React, { useMemo } from 'react';
import { formatWatchTime, t } from '../i18n/index.js';
import {
  computeWatchHistorySummary,
  groupWatchHistoryByDate,
} from '../data/local/continueWatchingMapper.js';
import {
  buildProgressMap,
  clearAllProgress,
  useProgressItems,
} from '../data/local/progressStore.js';
import { useFavorites } from '../data/local/favoritesStore.js';
import { useWatchlist } from '../data/local/watchlistStore.js';
import { pushFocusKey } from '../focus/focusRestoration.js';
import { buildMediaContextMenu } from '../components/contextMenuBuilder.js';
import TiwiButton from '../components/TiwiButton.jsx';
import MoviesGridSection from '../components/sections/MoviesGridSection.jsx';
import EmptySection from '../components/sections/EmptySection.jsx';
import { IconDelete } from '../components/Icons.jsx';

export default function WatchHistoryScreen({
  onNavigate,
  onOpenOverlay,
  onCloseOverlay,
  onToast,
}) {
  const progressItems = useProgressItems();
  useFavorites();
  useWatchlist();

  const progressMap = useMemo(() => buildProgressMap(progressItems), [progressItems]);
  const summary = useMemo(() => computeWatchHistorySummary(progressItems), [progressItems]);
  const grouped = useMemo(() => groupWatchHistoryByDate(progressItems), [progressItems]);

  const handleRequestClear = () => {
    onOpenOverlay?.({
      title: t('overlay_menu_clear_watch_history'),
      items: [
        {
          id: 'clear_hist_hdr',
          type: 'header',
          label: t('overlay_menu_are_you_sure'),
        },
        {
          id: 'clear_hist_yes',
          type: 'button',
          label: t('overlay_menu_yes'),
          onClick: () => {
            clearAllProgress();
            onCloseOverlay?.();
            onToast?.(t('overlay_menu_history_cleared'));
          },
        },
        {
          id: 'clear_hist_no',
          type: 'button',
          label: t('overlay_menu_no'),
          onClick: () => onCloseOverlay?.(),
        },
      ],
    });
  };

  const formattedTime = summary ? formatWatchTime(summary.totalWatchTimeMs) : null;

  return (
    <div className="screen-content">
      <div className="screen-top-spacer" />

      {!summary && grouped.length === 0 ? (
        <EmptySection message={t('watch_history_empty')} />
      ) : (
        <>
          {summary && summary.totalWatchTimeMs > 0 && (
            <div className="watch-history-summary">
              {formattedTime && (
                <div className="watch-history-summary__stat" style={{ flex: 1 }}>
                  <span className="type-body-small" style={{ color: 'var(--color-on-surface-variant)' }}>
                    {t('watch_history_total_time_label')}
                  </span>
                  <span className="type-title-large">{formattedTime}</span>
                </div>
              )}
              <div className="watch-history-summary__stat">
                <span className="type-body-small" style={{ color: 'var(--color-on-surface-variant)' }}>
                  {t('watch_history_movies_label')}
                </span>
                <span className="type-title-large">{summary.moviesCount}</span>
              </div>
              <div className="watch-history-summary__stat">
                <span className="type-body-small" style={{ color: 'var(--color-on-surface-variant)' }}>
                  {t('watch_history_episodes_label')}
                </span>
                <span className="type-title-large">{summary.episodesCount}</span>
              </div>
              <TiwiButton
                text={t('overlay_menu_clear_watch_history')}
                icon={<IconDelete size={18} />}
                variant="danger"
                onClick={handleRequestClear}
              />
            </div>
          )}

          {grouped.map((group) => (
            <MoviesGridSection
              key={group.date}
              title={group.date}
              items={group.items}
              progressMap={progressMap}
              hasMore={false}
              showLoadMoreButton={false}
              focusPrefix={`hist_${group.date}_`}
              onItemClick={(movie) => {
                pushFocusKey(`hist_${group.date}_${movie.url}`);
                onNavigate?.({
                  name: 'details',
                  request: movie.detailsRequest || { url: movie.url, isTvShow: movie.isTvShow },
                  autoPlay: false,
                  episodeUrl: null,
                });
              }}
              onItemLongClick={(movie) => {
                pushFocusKey(`hist_${group.date}_${movie.url}`);
                onOpenOverlay?.(
                  buildMediaContextMenu({
                    movie,
                    progressMap,
                    onClose: onCloseOverlay,
                  }),
                );
              }}
            />
          ))}
        </>
      )}
    </div>
  );
}
