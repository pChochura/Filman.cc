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
  const [isInView, setIsInView] = useState(true);
  const [isHovered, setIsHovered] = useState(false);
  const [isFocusWithin, setIsFocusWithin] = useState(false);

  const heroRef = useRef(null);
  const stripRef = useRef(null);
  const thumbRefs = useRef([]);

  const safeIndex = items.length > 0 ? Math.min(focusedIndex, items.length - 1) : 0;
  const currentItem = items[safeIndex] || null;

  // Pause rotation and avoid DOM work when hero is off-screen
  useEffect(() => {
    const heroEl = heroRef.current;
    if (!heroEl || typeof IntersectionObserver === 'undefined') return undefined;

    const observer = new IntersectionObserver(
      ([entry]) => {
        setIsInView(entry.isIntersecting);
      },
      { threshold: 0 },
    );

    observer.observe(heroEl);
    return () => observer.disconnect();
  }, [items.length]);

  const isPaused = !isInView || isHovered || isFocusWithin;

  // Auto-advance timer (only runs when visible and not paused by user interaction)
  useEffect(() => {
    if (items.length <= 1 || isPaused) return undefined;
    const timer = setInterval(() => {
      setFocusedIndex((prev) => (prev + 1) % items.length);
    }, 10_000);
    return () => clearInterval(timer);
  }, [items.length, isPaused]);

  // Keep active thumbnail visible inside the strip by scrolling stripRef horizontally only.
  // Using container scrollTo/scrollBy guarantees window vertical scroll is never affected.
  useEffect(() => {
    const strip = stripRef.current;
    const el = thumbRefs.current[safeIndex];
    if (!strip || !el) return;

    if (safeIndex === 0) {
      strip.scrollTo({ left: 0, behavior: 'smooth' });
      return;
    }

    const stripRect = strip.getBoundingClientRect();
    const elRect = el.getBoundingClientRect();

    if (elRect.left < stripRect.left) {
      strip.scrollBy({ left: elRect.left - stripRect.left, behavior: 'smooth' });
    } else if (elRect.right > stripRect.right) {
      strip.scrollBy({ left: elRect.right - stripRect.right, behavior: 'smooth' });
    }
  }, [safeIndex]);

  if (!items || items.length === 0 || !currentItem) {
    return <div className="screen-top-spacer" />;
  }

  return (
    <section
      ref={heroRef}
      className="hero-bleed horizontal-bleed"
      onMouseEnter={() => setIsHovered(true)}
      onMouseLeave={() => setIsHovered(false)}
      onFocus={() => setIsFocusWithin(true)}
      onBlur={(e) => {
        if (!e.currentTarget.contains(e.relatedTarget)) {
          setIsFocusWithin(false);
        }
      }}
    >
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
