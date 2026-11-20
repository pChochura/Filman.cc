import React from 'react';
import { t } from '../../i18n/index.js';
import TiwiButton from '../TiwiButton.jsx';

export default function SearchHistorySection({
  history = [],
  onSelectQuery,
  onLongClickQuery,
  onClearAll,
}) {
  if (!history || history.length === 0) return null;

  return (
    <section style={{ marginTop: 'var(--spacing-md)' }}>
      <div className="type-title-medium" style={{ color: 'var(--color-on-surface-variant)', marginBottom: '8px' }}>
        {t('common_recently_searched')}
      </div>
      <div className="search-history-row horizontal-bleed">
        {history.map((item) => (
          <TiwiButton
            key={item}
            text={item}
            focusGroup="search_history"
            onClick={() => onSelectQuery?.(item)}
            onLongClick={() => onLongClickQuery?.(item)}
          />
        ))}
        <TiwiButton
          text={t('common_clear_all_history')}
          variant="danger"
          focusGroup="search_history"
          onClick={onClearAll}
        />
      </div>
    </section>
  );
}
