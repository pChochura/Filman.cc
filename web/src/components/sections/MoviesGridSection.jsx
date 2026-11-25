import React, { useEffect, useRef, useState } from 'react';
import { t } from '../../i18n/index.js';
import SectionHeader from '../SectionHeader.jsx';
import MediaCard from '../MediaCard.jsx';
import LoadingMoreFooter from '../LoadingMoreFooter.jsx';
import QuickInfoPopup from '../QuickInfoPopup.jsx';
import { IconAdd } from '../Icons.jsx';

export const GRID_HOVER_DELAY_MS = 380;

export default function MoviesGridSection({
  title = null,
  headerAction = null,
  items = [],
  progressMap = {},
  hasMore = false,
  isLoadingMore = false,
  showLoadMoreButton = false,
  focusPrefix = 'grid_',
  hoverDelayMs = GRID_HOVER_DELAY_MS,
  onItemClick,
  onItemLongClick,
  onLoadNextPage,
}) {
  const sentinelRef = useRef(null);
  const itemRefs = useRef([]);
  const [hoverPopupData, setHoverPopupData] = useState(null);
  const hoverTimerRef = useRef(null);
  const leaveTimerRef = useRef(null);

  useEffect(() => {
    return () => {
      clearTimeout(hoverTimerRef.current);
      clearTimeout(leaveTimerRef.current);
    };
  }, []);

  const handleMouseEnter = (item, node) => {
    clearTimeout(leaveTimerRef.current);
    clearTimeout(hoverTimerRef.current);
    const delay = hoverPopupData ? 90 : hoverDelayMs;
    hoverTimerRef.current = setTimeout(() => {
      if (node) {
        setHoverPopupData({
          item,
          anchorRect: node.getBoundingClientRect(),
        });
      }
    }, delay);
  };

  const handleMouseLeave = () => {
    clearTimeout(hoverTimerRef.current);
    leaveTimerRef.current = setTimeout(() => {
      setHoverPopupData(null);
    }, 200);
  };

  const handlePopupMouseEnter = () => {
    clearTimeout(leaveTimerRef.current);
  };

  const handlePopupMouseLeave = () => {
    leaveTimerRef.current = setTimeout(() => {
      setHoverPopupData(null);
    }, 150);
  };

  useEffect(() => {
    if (showLoadMoreButton || !hasMore || isLoadingMore || !onLoadNextPage) return undefined;
    const node = sentinelRef.current;
    if (!node || typeof IntersectionObserver === 'undefined') return undefined;

    const observer = new IntersectionObserver(
      (entries) => {
        if (entries.some((e) => e.isIntersecting)) {
          onLoadNextPage();
        }
      },
      { rootMargin: '300px' },
    );
    observer.observe(node);
    return () => observer.disconnect();
  }, [showLoadMoreButton, hasMore, isLoadingMore, onLoadNextPage, items.length]);

  if (!items || items.length === 0) return null;

  const shouldDropLast =
    showLoadMoreButton && hasMore && items.length > 0 && items.length % 5 === 0;
  const displayedItems = shouldDropLast ? items.slice(0, -1) : items;

  const handleShowMoreClick = () => {
    const leftIdx = displayedItems.length - 1;
    if (leftIdx >= 0 && itemRefs.current[leftIdx]) {
      itemRefs.current[leftIdx].focus();
    }
    onLoadNextPage?.();
  };

  return (
    <section>
      {(title || headerAction) && <SectionHeader title={title} action={headerAction} />}
      <div className="movies-grid">
        {displayedItems.map((item, idx) => (
          <MediaCard
            key={item.url}
            cardRef={(node) => {
              itemRefs.current[idx] = node;
            }}
            aspect="portrait"
            imageUrl={item.posterUrl}
            title={item.titlePl}
            badgeText={item.isTvShow ? t('common_tab_series') : null}
            rating={item.tiwiRating ?? item.filmanRating}
            progress={progressMap[item.url] ?? null}
            focusKey={`${focusPrefix}${item.url}`}
            onClick={() => {
              clearTimeout(hoverTimerRef.current);
              setHoverPopupData(null);
              onItemClick?.(item);
            }}
            onLongClick={() => onItemLongClick?.(item)}
            onMouseEnter={(e) => handleMouseEnter(item, e.currentTarget)}
            onMouseLeave={handleMouseLeave}
          />
        ))}

        {showLoadMoreButton && hasMore && (
          <button
            type="button"
            className="show-more-card selectable-pulse"
            data-focusable="true"
            data-focus-zone="content"
            disabled={isLoadingMore}
            onClick={handleShowMoreClick}
          >
            {isLoadingMore ? (
              <span className="spinner spinner--sm" />
            ) : (
              <>
                <IconAdd size={28} />
                <span className="type-title-medium">{t('common_show_more')}</span>
              </>
            )}
          </button>
        )}
      </div>

      {hoverPopupData && (
        <QuickInfoPopup
          item={hoverPopupData.item}
          anchorRect={hoverPopupData.anchorRect}
          onClose={() => setHoverPopupData(null)}
          onClick={() => {
            const it = hoverPopupData.item;
            clearTimeout(hoverTimerRef.current);
            setHoverPopupData(null);
            onItemClick?.(it);
          }}
          onMouseEnter={handlePopupMouseEnter}
          onMouseLeave={handlePopupMouseLeave}
        />
      )}

      {!showLoadMoreButton && hasMore && (
        <div ref={sentinelRef}>
          {isLoadingMore && <LoadingMoreFooter />}
        </div>
      )}
    </section>
  );
}
