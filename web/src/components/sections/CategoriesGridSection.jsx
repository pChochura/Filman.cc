import React from 'react';

export default function CategoriesGridSection({
  categories = [],
  hasSearchHistory = false,
  onCategoryClick,
}) {
  const containerClass = `categories-grid ${!hasSearchHistory ? 'categories-grid--no-history' : ''}`;

  if (!categories || categories.length === 0) {
    return (
      <div className={containerClass}>
        {Array.from({ length: 15 }).map((_, i) => {
          const row = Math.floor(i / 5);
          const opacity = row === 0 ? 1 : row === 1 ? 0.65 : 0.35;
          return (
            <div
              key={i}
              className="category-card skeleton-shimmer"
              style={{ opacity }}
              aria-hidden="true"
            />
          );
        })}
      </div>
    );
  }

  return (
    <div className={containerClass}>
      {categories.map((cat, idx) => {
        const hue1 = (idx * 13) % 360;
        const hue2 = (hue1 + 60) % 360;
        const background = `linear-gradient(135deg, hsl(${hue1}, 50%, 40%) 0%, hsl(${hue2}, 50%, 30%) 100%)`;
        return (
          <button
            key={cat.id}
            type="button"
            className="category-card type-title-medium selectable-pulse"
            style={{ background }}
            data-focusable="true"
            data-focus-zone="content"
            onClick={() => onCategoryClick?.(cat)}
          >
            {cat.name}
          </button>
        );
      })}
    </div>
  );
}
