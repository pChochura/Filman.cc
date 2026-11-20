import React from 'react';
import { t } from '../../i18n/index.js';
import TiwiButton from '../TiwiButton.jsx';

export default function ErrorSection({ message, onRetry }) {
  return (
    <div className="error-section">
      <p className="error-section__message type-title-medium">
        {message || t('common_couldnt_load_movies')}
      </p>
      {onRetry && (
        <TiwiButton
          text={t('refresh')}
          variant="primaryFocus"
          onClick={onRetry}
        />
      )}
    </div>
  );
}
