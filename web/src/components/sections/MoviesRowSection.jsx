import React from 'react';
import { t } from '../../i18n/index.js';
import { SectionFocusPrefix } from '../../focus/focusRestoration.js';
import SectionHeader from '../SectionHeader.jsx';
import MediaCard from '../MediaCard.jsx';

export default function MoviesRowSection({
  title,
  items = [],
  progressMap = {},
  onItemClick,
  onItemLongClick,
}) {
  if (!items || items.length === 0) return null;
  const prefix = SectionFocusPrefix.moviesRowPrefix(title);

  return (
    <section>
      <SectionHeader title={title} />
      <div className="cards-row horizontal-bleed">
        {items.map((item) => (
          <MediaCard
            key={item.url}
            rowVariant="portrait"
            imageUrl={item.posterUrl}
            title={item.titlePl}
            badgeText={item.isTvShow ? t('common_tab_series') : null}
            rating={item.tiwiRating ?? item.filmanRating}
            progress={progressMap[item.url] ?? null}
            focusKey={`${prefix}${item.url}`}
            focusGroup={`row_${title}`}
            focusWrap={true}
            onClick={() => onItemClick?.(item)}
            onLongClick={() => onItemLongClick?.(item)}
          />
        ))}
      </div>
    </section>
  );
}
