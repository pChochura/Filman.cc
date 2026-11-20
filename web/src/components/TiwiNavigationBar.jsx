import React, { useEffect, useRef } from 'react';
import { t } from '../i18n/index.js';
import {
  IconBack,
  IconHome,
  IconKids,
  IconMovie,
  IconSearch,
  IconSettings,
  IconTv,
} from './Icons.jsx';

export const NAV_TABS = [
  { id: 'search', type: 'icon', icon: IconSearch, labelKey: 'common_search' },
  { id: 'home', type: 'text', icon: IconHome, labelKey: 'common_tab_home' },
  { id: 'movies', type: 'text', icon: IconMovie, labelKey: 'common_tab_movies' },
  { id: 'tvshows', type: 'text', icon: IconTv, labelKey: 'common_tab_series' },
  { id: 'forkids', type: 'text', icon: IconKids, labelKey: 'common_tab_kids' },
];

export const MOBILE_NAV_ITEMS = [
  { id: 'home', icon: IconHome, labelKey: 'common_tab_home' },
  { id: 'movies', icon: IconMovie, labelKey: 'common_tab_movies' },
  { id: 'tvshows', icon: IconTv, labelKey: 'common_tab_series' },
  { id: 'forkids', icon: IconKids, labelKey: 'common_tab_kids' },
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
      if (typeof window !== 'undefined') {
        window.scrollTo({ top: 0, behavior: 'smooth' });
      }
    } else {
      onSelectTab?.(tabId);
    }
  };

  const handleMobileItemClick = (item) => {
    handleTabClick(item.id);
  };

  return (
    <>
      <header className={`tiwi-navbar ${showBackButton ? 'tiwi-navbar--back-only' : ''}`}>
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
              const isSearchIcon = tab.type === 'icon';
              return (
                <button
                  key={tab.id}
                  type="button"
                  className={`tiwi-nav-item ${
                    isSearchIcon
                      ? 'tiwi-nav-item--icon tiwi-nav-item--search'
                      : 'type-title-medium tiwi-nav-item--desktop-tab'
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
                  {isSearchIcon ? <Icon size={16} /> : <span>{t(tab.labelKey)}</span>}
                </button>
              );
            })
          )}
        </div>

        {!showBackButton && (
          <div className="tiwi-nav-pill tiwi-nav-pill--settings">
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

      {showNavigationBar && (
        <nav className="tiwi-bottom-nav" role="navigation" aria-label="Mobile navigation">
          {MOBILE_NAV_ITEMS.map((item) => {
            const isSelected = item.id === currentRouteName;
            const Icon = item.icon;
            return (
              <button
                key={item.id}
                type="button"
                className={`tiwi-bottom-nav-item ${isSelected ? 'is-selected' : ''}`}
                onClick={() => handleMobileItemClick(item)}
                aria-current={isSelected ? 'page' : undefined}
                aria-label={t(item.labelKey)}
              >
                <div className="tiwi-bottom-nav-icon-wrap">
                  <Icon size={20} />
                </div>
                <span className="tiwi-bottom-nav-label">{t(item.labelKey)}</span>
              </button>
            );
          })}
        </nav>
      )}
    </>
  );
}
