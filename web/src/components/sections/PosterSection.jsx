import React from 'react';
import { t, tp } from '../../i18n/index.js';
import { SectionFocusPrefix } from '../../focus/focusRestoration.js';
import TiwiButton from '../TiwiButton.jsx';
import TiwiIconButton from '../TiwiIconButton.jsx';
import { IconMoreVert, IconPlay, IconStar, IconTrailer } from '../Icons.jsx';

export default function PosterSection({
  detailedMedia,
  watchButtonState,
  trailerUrl,
  onPlayClick,
  onWatchTrailerClick,
  onMoreOptionsClick,
  watchButtonRef,
}) {
  if (!detailedMedia?.baseItem) return null;
  const base = detailedMedia.baseItem;
  const meta = detailedMedia.metaInfo;
  const categories = detailedMedia.categories || [];
  const rating = base.imdbRating ?? base.tiwiRating ?? base.filmanRating;
  const isTv = Boolean(base.isTvShow);
  const seasonsCount = base.seasons?.length || 0;

  const metaParts = [];
  if (isTv && seasonsCount > 0) {
    metaParts.push(tp('common_n_seasons', seasonsCount));
  }
  if (meta?.duration) metaParts.push(meta.duration);
  if (meta?.year) metaParts.push(String(meta.year));
  if (Array.isArray(meta?.countries) && meta.countries.length > 0) {
    metaParts.push(meta.countries.join(', '));
  }

  const visibleCategories = categories.slice(0, 3);
  const extraCategoriesCount = Math.max(0, categories.length - 3);

  return (
    <section className="hero-bleed horizontal-bleed">
      {base.backgroundUrl ? (
        <img
          src={base.backgroundUrl}
          alt={base.titlePl}
          className="hero-backdrop hero-backdrop--dim"
        />
      ) : (
        <div className="hero-backdrop" style={{ background: 'var(--color-surface)' }} />
      )}
      <div className="hero-scrim" />

      <div className="poster-info">
        <h1 className="featured-title type-display-medium">{base.titlePl}</h1>

        {(rating || metaParts.length > 0) && (
          <div className="meta-info-row type-body-medium">
            {rating && (
              <>
                {base.imdbRating ? (
                  <span className="imdb-badge">{t('common_imdb')}</span>
                ) : (
                  <span style={{ color: 'var(--color-imdb)', display: 'inline-flex' }}>
                    <IconStar size={18} />
                  </span>
                )}
                <span style={{ color: 'var(--color-on-background)', fontWeight: 600 }}>
                  {Number(rating).toFixed(1)}
                </span>
                {metaParts.length > 0 && <span>•</span>}
              </>
            )}
            {metaParts.map((part, idx) => (
              <React.Fragment key={idx}>
                {idx > 0 && <span>•</span>}
                <span>{part}</span>
              </React.Fragment>
            ))}
          </div>
        )}

        {visibleCategories.length > 0 && (
          <div className="genre-pills-row">
            {visibleCategories.map((cat) => (
              <span key={cat.id || cat.name} className="genre-pill type-body-medium">
                {cat.name.charAt(0).toLocaleUpperCase() + cat.name.slice(1)}
              </span>
            ))}
            {extraCategoriesCount > 0 && (
              <span className="genre-pill type-body-medium">+{extraCategoriesCount}</span>
            )}
          </div>
        )}

        {base.description && (
          <p className="featured-overview type-body-large">{base.description}</p>
        )}

        <div className="poster-cta-row">
          <TiwiButton
            buttonRef={watchButtonRef}
            text={watchButtonState.label}
            icon={<IconPlay size={22} />}
            enabled={watchButtonState.enabled}
            isLoading={watchButtonState.isLoading}
            focusKey={`${SectionFocusPrefix.FEATURED}watch`}
            onClick={() => onPlayClick?.(watchButtonState.url)}
          />

          <TiwiButton
            text={t('common_watch_trailer')}
            icon={<IconTrailer size={20} />}
            enabled={Boolean(trailerUrl)}
            variant="secondary"
            focusKey={`${SectionFocusPrefix.FEATURED}trailer`}
            onClick={() => trailerUrl && onWatchTrailerClick?.(trailerUrl)}
          />

          <TiwiIconButton
            icon={<IconMoreVert size={24} />}
            tooltip={t('common_more_options')}
            variant="subtle"
            focusKey={`${SectionFocusPrefix.FEATURED}more`}
            onClick={onMoreOptionsClick}
          />
        </div>
      </div>
    </section>
  );
}
