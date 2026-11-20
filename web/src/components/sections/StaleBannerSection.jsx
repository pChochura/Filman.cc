import React from 'react';
import { t } from '../../i18n/index.js';
import { IconDelete } from '../Icons.jsx';
import TiwiIconButton from '../TiwiIconButton.jsx';

export default function StaleBannerSection({ visible, onDismiss }) {
  if (!visible) return null;
  return (
    <div className="stale-banner" role="status">
      <span className="type-body-medium">{t('common_stale_data_banner')}</span>
      {onDismiss && (
        <TiwiIconButton
          icon={<IconDelete size={18} />}
          tooltip={t('common_clear')}
          size="sm"
          variant="transparent"
          onClick={onDismiss}
        />
      )}
    </div>
  );
}
