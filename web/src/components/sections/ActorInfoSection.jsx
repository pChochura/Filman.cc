import React, { useState } from 'react';
import { t } from '../../i18n/index.js';
import { IconCalendar, IconLocation, IconPerson } from '../Icons.jsx';

export default function ActorInfoSection({ actor }) {
  const [expanded, setExpanded] = useState(false);
  if (!actor) return null;

  const metaItems = [];
  if (actor.birthDate) {
    metaItems.push({ icon: <IconCalendar size={16} />, text: actor.birthDate });
  }
  if (actor.birthPlace) {
    metaItems.push({ icon: <IconLocation size={16} />, text: actor.birthPlace });
  }

  return (
    <section className="actor-info-section">
      {actor.avatarUrl ? (
        <img src={actor.avatarUrl} alt={actor.name} className="actor-avatar" />
      ) : (
        <div className="actor-avatar">
          <IconPerson size={48} />
        </div>
      )}

      <div style={{ flex: 1, display: 'flex', flexDirection: 'column', gap: 'var(--spacing-md)' }}>
        <h1 className="type-display-medium">{actor.name}</h1>

        {metaItems.length > 0 && (
          <div className="meta-info-row type-body-medium">
            {metaItems.map((m, idx) => (
              <React.Fragment key={idx}>
                {idx > 0 && <span>•</span>}
                <span style={{ display: 'inline-flex', alignItems: 'center', gap: '6px' }}>
                  {m.icon}
                  <span>{m.text}</span>
                </span>
              </React.Fragment>
            ))}
          </div>
        )}

        {actor.description && (
          <button
            type="button"
            className="actor-bio-card selectable-pulse"
            data-focusable="true"
            data-focus-zone="content"
            onClick={() => setExpanded((prev) => !prev)}
          >
            <p
              className={`type-body-large ${expanded ? '' : 'actor-bio-card__text--clamped'}`}
              style={{ color: 'var(--color-on-surface-variant)' }}
            >
              {actor.description}
            </p>
            <span className="type-label-large" style={{ color: 'var(--color-on-surface)' }}>
              {expanded ? t('common_show_less') : t('common_read_more')}
            </span>
          </button>
        )}
      </div>
    </section>
  );
}
