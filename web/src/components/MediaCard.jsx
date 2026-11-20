import React, { useEffect, useRef } from 'react';
import { t } from '../i18n/index.js';
import { MARK_AS_WATCHED_PROGRESS_THRESHOLD } from '../data/local/progressStore.js';
import TiwiProgressBar from './TiwiProgressBar.jsx';
import { IconStar } from './Icons.jsx';

export default function MediaCard({
  imageUrl,
  title,
  badgeText = null,
  rating = null,
  progress = null,
  isFinished = false,
  aspect = 'portrait',
  rowVariant = null,
  sourceLabels = null,
  onClick,
  onLongClick,
  focusKey,
  focusGroup,
  focusWrap = false,
  focusZone = 'content',
  onFocusCard,
  cardRef,
  onMouseEnter,
  onMouseLeave,
}) {
  const internalRef = useRef(null);
  const longPressTimerRef = useRef(null);
  const pointerLongFiredRef = useRef(false);

  useEffect(() => {
    const el = internalRef.current;
    if (!el || !onLongClick) return undefined;
    const onTvLongPress = () => onLongClick();
    el.addEventListener('tv-long-press', onTvLongPress);
    return () => el.removeEventListener('tv-long-press', onTvLongPress);
  }, [onLongClick]);

  const showWatched = Boolean(isFinished || (progress !== null && progress >= MARK_AS_WATCHED_PROGRESS_THRESHOLD));
  const showProgressBar =
    !showWatched && progress !== null && progress > 0 && progress < MARK_AS_WATCHED_PROGRESS_THRESHOLD;

  const sizeClass =
    rowVariant === 'portrait'
      ? 'media-card--row-portrait'
      : rowVariant === 'landscape'
        ? 'media-card--row-landscape'
        : rowVariant === 'thumb'
          ? 'media-card--thumb'
          : aspect === 'landscape'
            ? 'media-card--landscape'
            : 'media-card--portrait';

  return (
    <button
      ref={(node) => {
        internalRef.current = node;
        if (typeof cardRef === 'function') cardRef(node);
        else if (cardRef) cardRef.current = node;
      }}
      type="button"
      className={`media-card ${sizeClass} selectable-pulse`}
      data-focusable="true"
      data-focus-key={focusKey}
      data-focus-group={focusGroup}
      data-focus-wrap={focusWrap ? 'true' : undefined}
      data-focus-zone={focusZone}
      data-has-context-menu={onLongClick ? 'true' : undefined}
      onFocus={onFocusCard}
      onMouseEnter={onMouseEnter}
      onMouseLeave={onMouseLeave}
      onClick={() => {
        if (pointerLongFiredRef.current) {
          pointerLongFiredRef.current = false;
          return;
        }
        onClick?.();
      }}
      onContextMenu={
        onLongClick
          ? (e) => {
              e.preventDefault();
              onLongClick();
            }
          : undefined
      }
      onPointerDown={
        onLongClick
          ? (e) => {
              if (e.pointerType === 'touch') {
                pointerLongFiredRef.current = false;
                longPressTimerRef.current = setTimeout(() => {
                  pointerLongFiredRef.current = true;
                  onLongClick();
                }, 520);
              }
            }
          : undefined
      }
      onPointerUp={() => clearTimeout(longPressTimerRef.current)}
      onPointerCancel={() => clearTimeout(longPressTimerRef.current)}
    >
      <div className="media-card__poster-wrap gradient-foreground">
        {imageUrl ? (
          <img src={imageUrl} alt={title || ''} className="media-card__img" loading="lazy" />
        ) : (
          <div className="media-card__placeholder type-body-small">{title}</div>
        )}
      </div>

      {sourceLabels && <div className="media-card__top-left">{sourceLabels}</div>}

      {rating !== null && rating !== undefined && (
        <div className="media-card__rating type-label-small">
          <IconStar size={14} />
          <span>{Number(rating).toFixed(1)}</span>
        </div>
      )}

      {(badgeText || title) && (
        <div className="media-card__bottom">
          {badgeText && (
            <div className="media-card__badge type-label-small">{badgeText}</div>
          )}
          {title && <div className="media-card__title type-body-large">{title}</div>}
        </div>
      )}

      {showProgressBar && (
        <div className="media-card__progress">
          <TiwiProgressBar progress={progress} />
        </div>
      )}

      {showWatched && (
        <div className="media-card__watched-veil type-body-large">
          {t('common_watched')}
        </div>
      )}
    </button>
  );
}
