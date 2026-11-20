import React from 'react';
import { t } from '../../i18n/index.js';
import FilmanButton from '../FilmanButton.jsx';
import FilmanIconButton from '../FilmanIconButton.jsx';
import { IconDelete, IconFilter } from '../Icons.jsx';

export function FilterActionRow({
  activeFilterCount = 0,
  onOpenFilterMenu,
  onResetFilters,
}) {
  const hasActiveFilters = activeFilterCount > 0;
  const filterTooltip = hasActiveFilters
    ? t('filter_button_count', activeFilterCount)
    : t('filter_button_label');

  return (
    <div className="filter-action-row">
      {hasActiveFilters && (
        <FilmanIconButton
          icon={<IconDelete size={22} />}
          tooltip={t('filter_reset_all')}
          tooltipPosition="below"
          variant="surface"
          onClick={onResetFilters}
        />
      )}
      <FilmanIconButton
        icon={<IconFilter size={22} />}
        tooltip={filterTooltip}
        tooltipPosition="below"
        variant="surface"
        primaryTint={hasActiveFilters}
        onClick={onOpenFilterMenu}
      />
    </div>
  );
}

export function EmptyFilterResultsSection({ onReset }) {
  return (
    <div className="empty-section">
      <p className="type-title-medium">{t('filter_empty_results')}</p>
      {onReset && (
        <FilmanButton
          text={t('filter_reset_all')}
          variant="primaryFocus"
          onClick={onReset}
        />
      )}
    </div>
  );
}
