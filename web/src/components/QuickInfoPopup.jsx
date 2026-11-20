import React, { useEffect, useState } from 'react';
import { createPortal } from 'react-dom';
import { t, tp } from '../i18n/index.js';
import { getCachedMediaDetails, getMediaDetails } from '../data/api/tmdbClient.js';
import { IconInfo, IconStar } from './Icons.jsx';

export default function QuickInfoPopup({
  item,
  anchorRect,
  onClose,
  onClick,
  onMouseEnter,
  onMouseLeave,
}) {
  const [details, setDetails] = useState(() => getCachedMediaDetails(item));
  const [isLoading, setIsLoading] = useState(!details);

  useEffect(() => {
    let cancelled = false;

    if (!details && item) {
      setIsLoading(true);
      getMediaDetails(item)
        .then((data) => {
          if (!cancelled && data) {
            setDetails(data);
          }
        })
        .catch(() => {})
        .finally(() => {
          if (!cancelled) {
            setIsLoading(false);
          }
        });
    }

    const onScroll = () => {
      onClose?.();
    };

    const onKeyDown = (e) => {
      if (e.key === 'Escape') {
        onClose?.();
      }
    };

    window.addEventListener('scroll', onScroll, { passive: true });
    window.addEventListener('keydown', onKeyDown);

    return () => {
      cancelled = true;
      window.removeEventListener('scroll', onScroll);
      window.removeEventListener('keydown', onKeyDown);
    };
  }, [item, details, onClose]);

  if (!item || !anchorRect) return null;

  // Viewport-aware positioning
  const popupWidth = Math.min(340, Math.max(280, window.innerWidth - 32));
  let left = anchorRect.left + (anchorRect.width - popupWidth) / 2;
  left = Math.max(16, Math.min(window.innerWidth - popupWidth - 16, left));

  const estimatedHeight = 360;
  let top = anchorRect.top - 20;
  if (top + estimatedHeight > window.innerHeight - 16) {
    top = window.innerHeight - estimatedHeight - 16;
  }
  if (top < 16) {
    top = 16;
  }

  const isTvShow = Boolean(details?.baseItem?.isTvShow ?? item.isTvShow);
  const title = details?.baseItem?.titlePl || item.titlePl || '';
  const titleEn = details?.baseItem?.titleEn || item.titleEn || null;
  const rating = details?.baseItem?.tiwiRating ?? item.tiwiRating ?? item.filmanRating;
  const year = details?.metaInfo?.year || item.year;
  const backdropUrl =
    details?.baseItem?.backgroundUrl ||
    details?.baseItem?.posterUrl ||
    item.posterUrl ||
    '';
  const description =
    details?.baseItem?.description || item.description || '';
  const categories = details?.categories || [];
  const actors = details?.actors || [];

  const seasonsCount = details?.baseItem?.seasons?.length || 0;
  const durationLabel = isTvShow
    ? seasonsCount > 0
      ? tp('common_n_seasons', seasonsCount, seasonsCount)
      : t('common_tab_series')
    : details?.metaInfo?.duration || null;

  const handleCardClick = (e) => {
    e.stopPropagation();
    onClick?.();
  };

  return createPortal(
    <div
      className="quick-info-popup"
      style={{
        position: 'fixed',
        top: `${top}px`,
        left: `${left}px`,
        width: `${popupWidth}px`,
      }}
      role="tooltip"
      aria-live="polite"
      onClick={handleCardClick}
      onMouseEnter={onMouseEnter}
      onMouseLeave={onMouseLeave}
    >
      <div className="quick-info-popup__header">
        {backdropUrl ? (
          <img
            src={backdropUrl}
            alt={title}
            className="quick-info-popup__backdrop"
            loading="eager"
          />
        ) : (
          <div className="quick-info-popup__backdrop-placeholder" />
        )}
        <div className="quick-info-popup__scrim" />
        <span className="quick-info-popup__badge type-label-small">
          {isTvShow ? t('common_tab_series') : t('common_tab_movies')}
        </span>
      </div>

      <div className="quick-info-popup__body">
        <div className="quick-info-popup__title type-title-medium">{title}</div>
        {titleEn && titleEn !== title && (
          <div className="quick-info-popup__subtitle type-body-small">{titleEn}</div>
        )}

        <div className="quick-info-popup__meta type-label-small">
          {rating !== null && rating !== undefined && (
            <span className="quick-info-popup__rating">
              <IconStar size={13} />
              <span>{Number(rating).toFixed(1)}</span>
            </span>
          )}
          {year && <span className="quick-info-popup__year">{year}</span>}
          {durationLabel && (
            <span className="quick-info-popup__duration">{durationLabel}</span>
          )}
          {isLoading && <span className="quick-info-popup__loader spinner spinner--sm" />}
        </div>

        {categories.length > 0 && (
          <div className="quick-info-popup__tags">
            {categories.slice(0, 3).map((cat) => (
              <span key={cat.id || cat.name} className="quick-info-popup__tag type-label-small">
                {cat.name}
              </span>
            ))}
          </div>
        )}

        {description && (
          <div className="quick-info-popup__description type-body-small">
            {description}
          </div>
        )}

        {actors.length > 0 && (
          <div className="quick-info-popup__cast type-body-small">
            <span className="quick-info-popup__cast-label">{t('details_about')}: </span>
            <span>{actors.slice(0, 3).map((a) => a.name).join(', ')}</span>
          </div>
        )}

        <div className="quick-info-popup__actions">
          <button
            type="button"
            className="quick-info-popup__btn type-label-small"
            onClick={handleCardClick}
          >
            <IconInfo size={15} />
            <span>{t('common_open_details')}</span>
          </button>
        </div>
      </div>
    </div>,
    document.body,
  );
}
