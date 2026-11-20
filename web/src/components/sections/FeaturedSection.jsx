import React, { useEffect, useRef, useState } from 'react';
import { t } from '../../i18n/index.js';
import { SectionFocusPrefix } from '../../focus/focusRestoration.js';
import MediaCard from '../MediaCard.jsx';

export default function FeaturedSection({
  items = [],
  progressMap = {},
  onItemClick,
  onItemLongClick,
}) {
  const [focusedIndex, setFocusedIndex] = useState(0);
  const stripRef = useRef(null);
  const thumbRefs = useRef([]);

  const safeIndex = items.length > 0 ? Math.min(focusedIndex, items.length - 1) : 0;
  const currentItem = items[safeIndex] || null;

  useEffect(() => {
    if (items.length <= 1) return undefined;
    const timer = setInterval(() => {
      setFocusedIndex((prev) => (prev + 1) % items.length);
    }, 10_000);
    return () => clearInterval(timer);
  }, [items.length, safeIndex]);

  useEffect(() => {
    const el = thumbRefs.current[safeIndex];
    if (el && stripRef.current) {
      el.scrollIntoView({ behavior: 'smooth', block: 'nearest', inline: 'nearest' });
    }
  }, [safeIndex]);

  if (!items || items.length === 0 || !currentItem) {
    return <div className="screen-top-spacer" />;
  }

  return (
    <section className="hero-bleed horizontal-bleed">
      {currentItem.backgroundUrl ? (
        <img
          key={currentItem.url}
          src={currentItem.backgroundUrl}
          alt={currentItem.titlePl}
          className="hero-backdrop"
        />
      ) : (
        <div className="hero-backdrop" style={{ background: 'var(--color-surface)' }} />
      )}
      <div className="hero-scrim" />

      <div className="featured-info">
        <h1 className="featured-title type-display-medium">{currentItem.titlePl}</h1>
        {currentItem.description && (
          <p className="featured-overview type-body-large">{currentItem.description}</p>
        )}
      </div>

      <div ref={stripRef} className="featured-strip">
        {items.map((item, idx) => (
          <div key={item.url} className="featured-thumb-item">
            <MediaCard
              cardRef={(node) => {
                thumbRefs.current[idx] = node;
              }}
              rowVariant="thumb"
              imageUrl={item.posterUrl}
              title={null}
              badgeText={item.isTvShow ? t('common_tab_series') : null}
              rating={item.tiwiRating ?? item.filmanRating}
              progress={progressMap[item.url] ?? null}
              focusKey={`${SectionFocusPrefix.FEATURED}${item.url}`}
              focusGroup="featured_strip"
              focusWrap={true}
              onFocusCard={() => setFocusedIndex(idx)}
              onClick={() => onItemClick?.(item)}
              onLongClick={() => onItemLongClick?.(item)}
            />
            <span className="featured-thumb-item__label type-body-medium">{item.titlePl}</span>
          </div>
        ))}
      </div>
    </section>
  );
}
