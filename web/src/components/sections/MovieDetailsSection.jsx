import React from 'react';
import { t } from '../../i18n/index.js';
import { SectionFocusPrefix } from '../../focus/focusRestoration.js';
import SectionHeader from '../SectionHeader.jsx';
import { IconPerson } from '../Icons.jsx';

function ActorRow({ title, actors, groupName, onActorClick }) {
  if (!actors || actors.length === 0) return null;

  return (
    <section>
      <SectionHeader title={title} />
      <div className="cards-row cards-row--compact-gap horizontal-bleed">
        {actors.map((actor, idx) => (
          <button
            key={`${actor.role}_${actor.url}_${idx}`}
            type="button"
            className="cast-card selectable-pulse"
            data-focusable="true"
            data-focus-zone="content"
            data-focus-key={`${SectionFocusPrefix.CREW}${actor.role}_${actor.url}`}
            data-focus-group={groupName}
            data-focus-wrap="true"
            onClick={() => onActorClick?.(actor.url)}
          >
            {actor.avatarUrl ? (
              <img
                src={actor.avatarUrl}
                alt={actor.name}
                className="cast-card__avatar"
                loading="lazy"
              />
            ) : (
              <div className="cast-card__avatar cast-card__avatar--placeholder">
                <IconPerson size={28} />
              </div>
            )}
            <span className="cast-card__name type-body-medium">{actor.name}</span>
          </button>
        ))}
      </div>
    </section>
  );
}

export default function MovieDetailsSection({ actors = [], onActorClick }) {
  if (!actors || actors.length === 0) return null;
  const crew = actors.filter((a) => a.role === 'DIRECTOR' || a.role === 'WRITER');
  const cast = actors.filter((a) => a.role !== 'DIRECTOR' && a.role !== 'WRITER');

  return (
    <div>
      <ActorRow
        title={t('common_director_and_writers')}
        actors={crew}
        groupName="details_crew"
        onActorClick={onActorClick}
      />
      <ActorRow
        title={t('common_cast')}
        actors={cast}
        groupName="details_cast"
        onActorClick={onActorClick}
      />
    </div>
  );
}
