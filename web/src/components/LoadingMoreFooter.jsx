import React from 'react';
import { t } from '../i18n/index.js';

export default function LoadingMoreFooter({ label = t('loading_more') }) {
  return (
    <div className="loading-more-footer">
      <div className="spinner spinner--sm" />
      <span className="type-body-large">{label}</span>
    </div>
  );
}
