import React, { useEffect, useRef, useState } from 'react';
import { t } from '../i18n/index.js';
import { TmdbFeed, fetchFeedPage } from '../data/api/tmdbClient.js';
import { toggleWatchlist, useWatchlist } from '../data/local/watchlistStore.js';
import { canonicalUrl } from '../data/models/mediaRef.js';
import TiwiButton from '../components/TiwiButton.jsx';
import { IconAdd, IconRemove } from '../components/Icons.jsx';

export default function ScreensaverOverlay({
  slideDurationMs = 10_000,
  onDismiss,
  onToast,
}) {
  const [slides, setSlides] = useState([]);
  const [index, setIndex] = useState(0);
  const watchlist = useWatchlist();
  const btnRef = useRef(null);

  useEffect(() => {
    fetchFeedPage(TmdbFeed.TRENDING_ALL_DAY, 1).then((res) => {
      if (res.movies.length > 0) {
        setSlides(res.movies);
      }
    });
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
            <div style={{ marginTop: 'var(--spacing-sm)' }}>
              <TiwiButton
                buttonRef={btnRef}
                text={inWatchlist ? t('common_remove_from_watchlist') : t('common_add_to_watchlist')}
                icon={inWatchlist ? <IconRemove size={20} /> : <IconAdd size={20} />}
                onClick={handleToggleWatchlist}
              />
            </div>
          </div>
        </>
      )}
    </div>
  );
}
