import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { t } from '../i18n/index.js';
import { getActorDetails } from '../data/api/tmdbClient.js';
import { buildProgressMap, useProgressItems } from '../data/local/progressStore.js';
import { useFavorites } from '../data/local/favoritesStore.js';
import { useWatchlist } from '../data/local/watchlistStore.js';
import { pushFocusKey } from '../focus/focusRestoration.js';
import { buildMediaContextMenu } from '../components/contextMenuBuilder.js';
import TiwiFullscreenLoader from '../components/TiwiFullscreenLoader.jsx';
import ActorInfoSection from '../components/sections/ActorInfoSection.jsx';
import MoviesGridSection from '../components/sections/MoviesGridSection.jsx';
import ErrorSection from '../components/sections/ErrorSection.jsx';

export default function ActorScreen({ url, onNavigate, onOpenOverlay, onCloseOverlay }) {
  const [isLoading, setIsLoading] = useState(true);
  const [errorMessage, setErrorMessage] = useState(null);
  const [actor, setActor] = useState(null);

  const progressItems = useProgressItems();
  useFavorites();
  useWatchlist();
  const progressMap = useMemo(() => buildProgressMap(progressItems), [progressItems]);

  const loadActor = useCallback(async () => {
    setIsLoading(true);
    setErrorMessage(null);
    try {
      const details = await getActorDetails(url);
      if (!details) {
        setErrorMessage(t('error_media_not_found'));
      } else {
        setActor(details);
      }
    } catch (err) {
      setErrorMessage(err?.message || t('common_couldnt_load_movies'));
    } finally {
      setIsLoading(false);
    }
  }, [url]);

  useEffect(() => {
    loadActor();
  }, [loadActor]);

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

  if (isLoading) return <TiwiFullscreenLoader />;
  if (errorMessage || !actor) {
    return (
      <div className="screen-content">
        <div className="screen-top-spacer" />
        <ErrorSection message={errorMessage} onRetry={loadActor} />
      </div>
    );
  }

  return (
    <div className="screen-content">
      <div className="screen-top-spacer" />
      <ActorInfoSection actor={actor} />

      <MoviesGridSection
        title={t('actor_movies_director')}
        items={actor.moviesDirector}
        progressMap={progressMap}
        hasMore={false}
        showLoadMoreButton={false}
        focusPrefix="actor_dir_"
        onItemClick={(m) => openDetails(m, `actor_dir_${m.url}`)}
        onItemLongClick={(m) => openContextMenu(m, `actor_dir_${m.url}`)}
      />

      <MoviesGridSection
        title={t('actor_movies_writer')}
        items={actor.moviesWriter}
        progressMap={progressMap}
        hasMore={false}
        showLoadMoreButton={false}
        focusPrefix="actor_wri_"
        onItemClick={(m) => openDetails(m, `actor_wri_${m.url}`)}
        onItemLongClick={(m) => openContextMenu(m, `actor_wri_${m.url}`)}
      />

      <MoviesGridSection
        title={t('actor_movies_cast')}
        items={actor.moviesCast}
        progressMap={progressMap}
        hasMore={false}
        showLoadMoreButton={false}
        focusPrefix="actor_cast_"
        onItemClick={(m) => openDetails(m, `actor_cast_${m.url}`)}
        onItemLongClick={(m) => openContextMenu(m, `actor_cast_${m.url}`)}
      />
    </div>
  );
}
