import React from 'react';

export function CardSkeleton({ aspect = 'portrait', width = null }) {
  const aspectClass =
    aspect === 'landscape'
      ? 'skeleton-card--landscape'
      : 'skeleton-card--portrait';
  const style = width ? { width: `${width}px` } : {};
  return <div className={`skeleton-shimmer skeleton-card ${aspectClass}`} style={style} />;
}

export function HeroSkeleton() {
  return (
    <div className="skeleton-shimmer skeleton-hero">
      <div className="skeleton-hero__scrim" />
      <div className="skeleton-hero__content">
        <div
          className="skeleton-shimmer skeleton-bar"
          style={{ width: '380px', height: '40px' }}
        />
        <div style={{ display: 'flex', gap: 'var(--spacing-sm)' }}>
          <div
            className="skeleton-shimmer skeleton-bar"
            style={{ width: '60px', height: '22px' }}
          />
          <div
            className="skeleton-shimmer skeleton-bar"
            style={{ width: '50px', height: '22px' }}
          />
          <div
            className="skeleton-shimmer skeleton-bar"
            style={{ width: '80px', height: '22px' }}
          />
        </div>
        <div
          className="skeleton-shimmer skeleton-bar"
          style={{ width: '460px', height: '18px', opacity: 0.7 }}
        />
        <div
          className="skeleton-shimmer skeleton-bar"
          style={{ width: '340px', height: '18px', opacity: 0.5 }}
        />
        <div style={{ display: 'flex', gap: 'var(--spacing-md)', marginTop: 'var(--spacing-sm)' }}>
          <div
            className="skeleton-shimmer skeleton-bar"
            style={{ width: '130px', height: '42px', background: 'var(--color-primary)' }}
          />
          <div
            className="skeleton-shimmer skeleton-bar"
            style={{ width: '120px', height: '42px' }}
          />
        </div>
      </div>
    </div>
  );
}

export function CardsRowSkeleton({ aspect = 'portrait', count = 6 }) {
  return (
    <div className="skeleton-row">
      <div className="skeleton-shimmer skeleton-row__title" />
      <div className="skeleton-row__cards">
        {Array.from({ length: count }).map((_, i) => (
          <CardSkeleton key={i} aspect={aspect} />
        ))}
      </div>
    </div>
  );
}

export function CatalogSkeleton() {
  return (
    <div className="screen-content" style={{ padding: 0 }}>
      <HeroSkeleton />
      <CardsRowSkeleton aspect="landscape" count={5} />
      <CardsRowSkeleton aspect="portrait" count={7} />
    </div>
  );
}

export function DetailsSkeleton() {
  return (
    <div className="screen-content" style={{ padding: 0 }}>
      <HeroSkeleton />
      <CardsRowSkeleton aspect="landscape" count={5} />
    </div>
  );
}

export default CatalogSkeleton;
