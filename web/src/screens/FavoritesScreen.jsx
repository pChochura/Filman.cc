import React, { useMemo } from 'react';
import { t } from '../i18n/index.js';
import { useFavorites } from '../data/local/favoritesStore.js';
import { useWatchlist } from '../data/local/watchlistStore.js';
import { buildProgressMap, useProgressItems } from '../data/local/progressStore.js';
import { pushFocusKey } from '../focus/focusRestoration.js';
import { buildMediaContextMenu } from '../components/contextMenuBuilder.js';
import MoviesGridSection from '../components/sections/MoviesGridSection.jsx';
import EmptySection from '../components/sections/EmptySection.jsx';

export default function FavoritesScreen({ onNavigate, onOpenOverlay, onCloseOverlay }) {
  const favorites = useFavorites();
  const progressItems = useProgressItems();
  useWatchlist();
  const progressMap = useMemo(() => buildProgressMap(progressItems), [progressItems]);

  return (
    <div className="screen-content">
      <div className="screen-top-spacer" />

      {favorites.length === 0 ? (
        <EmptySection message={t('favorites_empty')} />
      ) : (
        <MoviesGridSection
          title={t('favorites_title')}
          items={favorites}
          progressMap={progressMap}
          hasMore={false}
          showLoadMoreButton={false}
          focusPrefix="fav_"
          onItemClick={(movie) => {
            pushFocusKey(`fav_${movie.url}`);
            onNavigate?.({
              name: 'details',
              request: movie.detailsRequest || { url: movie.url, isTvShow: movie.isTvShow },
              autoPlay: false,
              episodeUrl: null,
            });
          }}
          onItemLongClick={(movie) => {
            pushFocusKey(`fav_${movie.url}`);
            onOpenOverlay?.(
              buildMediaContextMenu({
                movie,
                progressMap,
                onClose: onCloseOverlay,
              }),
            );
          }}
        />
      )}
    </div>
  );
}
