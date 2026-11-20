import React from 'react';
import { t } from '../../i18n/index.js';
import { SectionFocusPrefix } from '../../focus/focusRestoration.js';
import SectionHeader from '../SectionHeader.jsx';
import MediaCard from '../MediaCard.jsx';

export default function EpisodesRowSection({
  seasons = [],
  showPosterUrl,
  progressMap = {},
  onEpisodeClick,
  onEpisodeLongClick,
}) {
  if (!seasons || seasons.length === 0) return null;

  return (
    <div>
      {seasons.map((season, seasonIdx) => (
        <section key={season.seasonNumber ?? seasonIdx}>
          <SectionHeader title={t('details_season_number', seasonIdx + 1)} />
          <div className="cards-row horizontal-bleed">
            {(season.episodes || []).map((ep) => (
              <MediaCard
                key={ep.url}
                rowVariant="landscape"
                imageUrl={showPosterUrl}
                title={ep.title}
                progress={progressMap[ep.url] ?? null}
                focusKey={`${SectionFocusPrefix.EPISODES}${ep.url}`}
                focusGroup={`episodes_s_${seasonIdx}`}
                focusWrap={true}
                onClick={() => onEpisodeClick?.(ep, seasonIdx)}
                onLongClick={() => onEpisodeLongClick?.(ep, seasonIdx)}
              />
            ))}
          </div>
        </section>
      ))}
    </div>
  );
}
