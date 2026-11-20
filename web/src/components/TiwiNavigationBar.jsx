import React, { useEffect, useRef } from 'react';
import { t } from '../i18n/index.js';
import { IconBack, IconSearch, IconSettings } from './Icons.jsx';

export const NAV_TABS = [
  { id: 'search', type: 'icon', icon: IconSearch, labelKey: 'common_search' },
  { id: 'home', type: 'text', labelKey: 'common_tab_home' },
  { id: 'movies', type: 'text', labelKey: 'common_tab_movies' },
  { id: 'tvshows', type: 'text', labelKey: 'common_tab_series' },
  { id: 'forkids', type: 'text', labelKey: 'common_tab_kids' },
];

export default function TiwiNavigationBar({
  currentRouteName,
  showNavigationBar,
  showBackButton,
  onSelectTab,
  onBack,
  onOpenSettings,
  onFocusContent,
}) {
  const focusTimerRef = useRef(null);
  const pointerDownRef = useRef(false);

  useEffect(() => () => clearTimeout(focusTimerRef.current), []);

  if (!showNavigationBar && !showBackButton) return null;

  const handleTabFocus = (tabId) => {
    if (pointerDownRef.current) {
      pointerDownRef.current = false;
      return;
    }
    clearTimeout(focusTimerRef.current);
    focusTimerRef.current = setTimeout(() => {
      if (tabId !== currentRouteName) {
        onSelectTab?.(tabId);
      }
    }, 300);
  };

  const handleTabClick = (tabId) => {
    clearTimeout(focusTimerRef.current);
    if (tabId === currentRouteName) {
      onFocusContent?.();
    } else {
      onSelectTab?.(tabId);
    }
  };

  return (
    <header className="tiwi-navbar">
      <div className="tiwi-nav-pill" role="navigation" aria-label="Main navigation">
        {showBackButton ? (
          <button
            type="button"
            className="tiwi-nav-item tiwi-nav-item--icon selectable-pulse"
            onClick={onBack}
            aria-label={t('common_back')}
            data-focusable="true"
            data-focus-zone="navbar"
            data-selected="true"
          >
            <IconBack size={16} />
          </button>
        ) : (
          NAV_TABS.map((tab) => {
            const isSelected = currentRouteName === tab.id;
            const Icon = tab.icon;
            return (
              <button
                key={tab.id}
                type="button"
                className={`tiwi-nav-item ${
                  tab.type === 'icon' ? 'tiwi-nav-item--icon' : 'type-title-medium'
                } selectable-pulse ${isSelected ? 'is-selected' : ''}`}
                aria-label={t(tab.labelKey)}
                aria-current={isSelected ? 'page' : undefined}
                data-focusable="true"
                data-focus-zone="navbar"
                data-selected={isSelected ? 'true' : 'false'}
                onPointerDown={() => {
                  pointerDownRef.current = true;
                }}
                onFocus={() => handleTabFocus(tab.id)}
                onClick={() => handleTabClick(tab.id)}
              >
                {tab.type === 'icon' ? <Icon size={16} /> : <span>{t(tab.labelKey)}</span>}
              </button>
            );
          })
        )}
      </div>

      {!showBackButton && (
        <div className="tiwi-nav-pill">
          <button
            type="button"
            className="tiwi-nav-item tiwi-nav-item--icon selectable-pulse"
            onClick={onOpenSettings}
            aria-label={t('common_settings')}
            data-focusable="true"
            data-focus-zone="navbar"
          >
            <IconSettings size={16} />
          </button>
        </div>
      )}
    </header>
  );
}
