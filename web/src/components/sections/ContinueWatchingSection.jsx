import React from 'react';
import { t } from '../../i18n/index.js';
import {
  formatContinueWatchingTitle,
  formatSeasonEpisodeTag,
} from '../../data/local/continueWatchingMapper.js';
import SectionHeader from '../SectionHeader.jsx';
import MediaCard from '../MediaCard.jsx';

export default function ContinueWatchingSection({
  title,
  items = [],
  sectionPrefix,
  onItemClick,
  onItemLongClick,
}) {
  if (!items || items.length === 0) return null;

  return (
    <section>
      <SectionHeader title={title} />
      <div className="cards-row horizontal-bleed">
        {items.map((item) => {
          const displayTitle = formatContinueWatchingTitle(item, (base) =>
            t('common_next_episode_format', base),
          );
          const badgeText = formatSeasonEpisodeTag(item);
          return (
            <MediaCard
              key={item.url}
              rowVariant="landscape"
              imageUrl={item.posterUrl}
              title={displayTitle}
              badgeText={badgeText}
              progress={item.kind === 'NextEpisode' ? 0 : item.progressPercentage}
              focusKey={`${sectionPrefix}${item.url}`}
              focusGroup={`row_${sectionPrefix}`}
              focusWrap={true}
              onClick={() => onItemClick?.(item)}
              onLongClick={() => onItemLongClick?.(item)}
            />
          );
        })}
      </div>
    </section>
  );
}
