import React, { useEffect, useRef, useState } from 'react';
import { createPortal } from 'react-dom';
import { t, tp } from '../i18n/index.js';
import { getCachedMediaDetails, getMediaDetails } from '../data/api/tmdbClient.js';
import { IconInfo, IconMoreVert, IconStar } from './Icons.jsx';
import TiwiButton from './TiwiButton.jsx';

export default function QuickInfoPopup({
  item,
  anchorRect,
  onClose,
  onClick,
  onLongClick,
  onMouseEnter,
  onMouseLeave,
}) {
  const [details, setDetails] = useState(() => getCachedMediaDetails(item));
  const [isLoading, setIsLoading] = useState(() => !getCachedMediaDetails(item));
  const longPressTimerRef = useRef(null);
  const pointerLongFiredRef = useRef(false);

  useEffect(() => {
    let cancelled = false;
    const cached = getCachedMediaDetails(item);
    setDetails(cached);

    if (!cached && item) {
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
    } else {
      setIsLoading(false);
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
  }, [item?.url, onClose]);

  if (!item || !anchorRect) return null;

  // Viewport-aware positioning
  const popupWidth = Math.min(380, Math.max(290, window.innerWidth - 32));
  let left = anchorRect.left + (anchorRect.width - popupWidth) / 2;
  left = Math.max(16, Math.min(window.innerWidth - popupWidth - 16, left));

  const estimatedHeight = 440;
  let top = anchorRect.top - 20;
  if (top + estimatedHeight > window.innerHeight - 16) {
    top = window.innerHeight - estimatedHeight - 16;
  }
  if (top < 16) {
    top = 16;
  }

  // Calculate transform origin based on anchorRect relative to popup position
  const originX = Math.round((anchorRect.left + anchorRect.width / 2) - left);
  const originY = Math.round(anchorRect.top - top + (anchorRect.height * 0.25));
  const startScale = Math.min(0.85, Math.max(0.6, Number((anchorRect.width / popupWidth).toFixed(3))));

  const isTvShow = Boolean(details?.baseItem?.isTvShow ?? item.isTvShow);
  const title = details?.baseItem?.titlePl || item.titlePl || '';
  const titleEn = details?.baseItem?.titleEn || item.titleEn || null;
  const rating = details?.baseItem?.tiwiRating ?? item.tiwiRating ?? item.filmanRating;
  const year = details?.metaInfo?.year || item.year;
  const posterUrl = item.posterUrl || details?.baseItem?.posterUrl || '';
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
    if (pointerLongFiredRef.current) {
      pointerLongFiredRef.current = false;
      return;
    }
    onClick?.();
  };

  const handleContextMenu = (e) => {
    if (!onLongClick) return;
    e.preventDefault();
    e.stopPropagation();
    onClose?.();
    onLongClick();
  };

  return createPortal(
    <div
      className="quick-info-popup"
      style={{
        position: 'fixed',
        top: `${top}px`,
        left: `${left}px`,
        width: `${popupWidth}px`,
        transformOrigin: `${originX}px ${originY}px`,
        '--start-scale': startScale,
      }}
      role="tooltip"
      aria-live="polite"
      onClick={handleCardClick}
      onContextMenu={handleContextMenu}
      onPointerDown={(e) => {
        if (e.pointerType === 'touch' && onLongClick) {
          pointerLongFiredRef.current = false;
          longPressTimerRef.current = setTimeout(() => {
            pointerLongFiredRef.current = true;
            onClose?.();
            onLongClick();
          }, 520);
        }
      }}
      onPointerUp={() => clearTimeout(longPressTimerRef.current)}
      onPointerCancel={() => clearTimeout(longPressTimerRef.current)}
      onMouseEnter={onMouseEnter}
      onMouseLeave={onMouseLeave}
    >
      <div className="quick-info-popup__header">
        {posterUrl ? (
          <img
            src={posterUrl}
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
          <TiwiButton
            text={t('common_open_details')}
            icon={<IconInfo size={16} />}
            variant="default"
            className="quick-info-popup__action-main"
            fullWidth={!onLongClick}
            onClick={handleCardClick}
          />
          {onLongClick && (
            <TiwiButton
              icon={<IconMoreVert size={18} />}
              variant="secondary"
              aria-label={t('common_more_options')}
              className="quick-info-popup__action-more"
              onClick={(e) => {
                e.stopPropagation();
                onClose?.();
                onLongClick();
              }}
            />
          )}
        </div>
      </div>
    </div>,
    document.body,
  );
}
