import React, { useEffect, useRef, useState } from 'react';
import { t } from '../i18n/index.js';
import { TmdbFeed, fetchFeedPage } from '../data/api/tmdbClient.js';
import { toggleWatchlist, useWatchlist } from '../data/local/watchlistStore.js';
import { canonicalUrl } from '../data/models/mediaRef.js';
import TiwiButton from '../components/TiwiButton.jsx';
import { IconAdd, IconPlay, IconRemove } from '../components/Icons.jsx';

export default function ScreensaverOverlay({
  slideDurationMs = 10_000,
  onDismiss,
  onPlay,
  onDetails,
  onToast,
}) {
  const [slides, setSlides] = useState([]);
  const [index, setIndex] = useState(0);
  const watchlist = useWatchlist();
  const btnRef = useRef(null);

  useEffect(() => {
    let cancelled = false;

    async function loadFeeds() {
      try {
        const initialRes = await fetchFeedPage(TmdbFeed.TRENDING_ALL_DAY, 1);
        if (cancelled) return;
        const initial = (initialRes.movies || []).filter(
          (m) => m.backgroundUrl || m.posterUrl,
        );
        if (initial.length > 0) {
          setSlides(initial);
        }

        const [popularMovies, popularTv, topRated] = await Promise.all([
          fetchFeedPage(TmdbFeed.MOVIES_POPULAR, 1).catch(() => ({ movies: [] })),
          fetchFeedPage(TmdbFeed.TV_POPULAR, 1).catch(() => ({ movies: [] })),
          fetchFeedPage(TmdbFeed.MOVIES_TOP_RATED, 1).catch(() => ({ movies: [] })),
        ]);

        if (cancelled) return;

        const combinedPool = [
          ...initial,
          ...(popularMovies.movies || []),
          ...(popularTv.movies || []),
          ...(topRated.movies || []),
          ...watchlist,
        ].filter((m) => m && (m.backgroundUrl || m.posterUrl));

        const seen = new Set();
        const unique = [];
        for (const item of combinedPool) {
          const key = canonicalUrl(item.url);
          if (!seen.has(key)) {
            seen.add(key);
            unique.push(item);
          }
        }

        // Shuffle non-initial slides to ensure high variety
        for (let i = unique.length - 1; i > 0; i--) {
          const j = Math.floor(Math.random() * (i + 1));
          [unique[i], unique[j]] = [unique[j], unique[i]];
        }

        if (unique.length > 0) {
          setSlides(unique);
        }
      } catch (err) {
        console.error('Failed to load screensaver slides', err);
      }
    }

    loadFeeds();
    return () => {
      cancelled = true;
    };
  }, []);

  useEffect(() => {
    btnRef.current?.focus();
  }, [slides.length]);

  useEffect(() => {
    if (slides.length <= 1) return undefined;
    const timer = setInterval(() => {
      setIndex((prev) => (prev + 1) % slides.length);
    }, slideDurationMs);
    return () => clearInterval(timer);
  }, [slides.length, slideDurationMs, index]);

  useEffect(() => {
    const onKeyDown = (e) => {
      if (e.key === 'ArrowRight' && slides.length > 0) {
        e.preventDefault();
        e.stopPropagation();
        setIndex((prev) => (prev + 1) % slides.length);
      } else if (e.key === 'ArrowLeft' && slides.length > 0) {
        e.preventDefault();
        e.stopPropagation();
        setIndex((prev) => (prev - 1 + slides.length) % slides.length);
      } else if (e.key === 'Escape' || e.key === 'Backspace') {
        e.preventDefault();
        e.stopPropagation();
        onDismiss?.();
      }
    };
    window.addEventListener('keydown', onKeyDown, true);
    return () => window.removeEventListener('keydown', onKeyDown, true);
  }, [slides.length, onDismiss]);

  const current = slides[index] || null;
  const inWatchlist = current
    ? watchlist.some((w) => canonicalUrl(w.url) === canonicalUrl(current.url))
    : false;

  const handleToggleWatchlist = (e) => {
    e?.stopPropagation?.();
    if (!current) return;
    const added = toggleWatchlist(current);
    onToast?.(
      added
        ? t('screensaver_toast_added_to_watchlist')
        : t('screensaver_toast_removed_from_watchlist'),
    );
  };

  return (
    <div className="screensaver-overlay" onClick={onDismiss}>
      {!current ? (
        <div className="fullscreen-loader">
          <div className="spinner" />
        </div>
      ) : (
        <>
          <img
            key={current.url}
            src={current.backgroundUrl || current.posterUrl}
            alt={current.titlePl}
            className="screensaver-bg"
          />
          <div className="hero-scrim" />

          <div className="screensaver-info" onClick={(e) => e.stopPropagation()}>
            <h1 className="featured-title type-display-medium">{current.titlePl}</h1>
            <div className="meta-info-row type-body-medium">
              {(current.tiwiRating || current.filmanRating) && (
                <>
                  <span className="imdb-badge">{t('common_imdb')}</span>
                  <span style={{ color: 'var(--color-on-background)', fontWeight: 600 }}>
                    {Number(current.tiwiRating || current.filmanRating).toFixed(1)}
                  </span>
                </>
              )}
              {(current.tiwiRating || current.filmanRating) && current.year && <span>•</span>}
              {current.year && <span>{current.year}</span>}
            </div>
            {current.description && (
              <p className="featured-overview type-body-large">{current.description}</p>
            )}
            <div
              className="screensaver-actions"
              style={{
                display: 'flex',
                gap: 'var(--spacing-md)',
                marginTop: 'var(--spacing-md)',
              }}
            >
              {onPlay && (
                <TiwiButton
                  text={t('player_play')}
                  icon={<IconPlay size={20} />}
                  primary
                  onClick={(e) => {
                    e?.stopPropagation?.();
                    onDismiss?.();
                    onPlay(current);
                  }}
                />
              )}
              {onDetails && (
                <TiwiButton
                  text={t('common_open_details')}
                  onClick={(e) => {
                    e?.stopPropagation?.();
                    onDismiss?.();
                    onDetails(current);
                  }}
                />
              )}
              <TiwiButton
                buttonRef={btnRef}
                text={
                  inWatchlist
                    ? t('common_remove_from_watchlist')
                    : t('common_add_to_watchlist')
                }
                icon={
                  inWatchlist ? <IconRemove size={20} /> : <IconAdd size={20} />
                }
                onClick={handleToggleWatchlist}
              />
            </div>
          </div>
        </>
      )}
    </div>
  );
}
