import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { t } from './i18n/index.js';
import {
  TMDB_API_KEY,
  getTvSummaryForEpisodeCheck,
} from './data/api/tmdbClient.js';
import { isStreamApiConfigured, wakeUpStreamApi } from './data/api/streamApiClient.js';
import { useSettings } from './data/local/settingsStore.js';
import { useSession } from './data/local/sessionStore.js';
import { checkForNewEpisodes } from './data/local/newEpisodesStore.js';
import { parseMediaRef } from './data/models/mediaRef.js';
import { triggerSync, useSyncStatus } from './data/sync/syncManager.js';
import { buildAppSettingsMenu } from './menus/appSettingsMenu.js';
import { getPolishSystemVoices } from './player/lektor/systemLektorSpeech.js';
import { restoreSavedFocus } from './focus/focusRestoration.js';
import { installTvFocusManager } from './focus/tvFocusManager.js';
import FilmanNavigationBar from './components/FilmanNavigationBar.jsx';
import FilmanOverlayMenu from './components/FilmanOverlayMenu.jsx';
import FilmanToast from './components/FilmanToast.jsx';
import HomeScreen from './screens/HomeScreen.jsx';
import { ForKidsScreen, MoviesScreen, TvShowsScreen } from './screens/CatalogScreen.jsx';
import SearchScreen from './screens/SearchScreen.jsx';
import MovieDetailsScreen from './screens/MovieDetailsScreen.jsx';
import ActorScreen from './screens/ActorScreen.jsx';
import FavoritesScreen from './screens/FavoritesScreen.jsx';
import WatchHistoryScreen from './screens/WatchHistoryScreen.jsx';
import LoginScreen from './screens/LoginScreen.jsx';
import PlayerScreen from './screens/PlayerScreen.jsx';
import ScreensaverOverlay from './screens/ScreensaverOverlay.jsx';

const TOP_LEVEL_TABS = new Set(['search', 'home', 'movies', 'tvshows', 'forkids']);
const BACK_BAR_ROUTES = new Set(['details', 'actor', 'favorites', 'watchhistory', 'login']);
const SYNC_INTERVAL_MS = 6 * 60 * 60 * 1000;

function routeToHash(route) {
  if (!route) return 'home';
  if (route.name === 'details') {
    const target = route.request?.url || route.episodeUrl || '';
    return target ? `details/${encodeURIComponent(target)}` : 'home';
  }
  if (route.name === 'actor' && route.url) {
    return `actor/${encodeURIComponent(route.url)}`;
  }
  if (route.name === 'player' && route.url) {
    return `player/${encodeURIComponent(route.url)}`;
  }
  return route.name || 'home';
}

function parseInitialStack() {
  if (typeof window === 'undefined') return [{ name: 'home' }];
  const raw = window.location.hash.replace(/^#\/?/, '').trim();
  if (!raw || raw === 'home') return [{ name: 'home' }];
  if (TOP_LEVEL_TABS.has(raw)) return [{ name: raw }];
  if (raw === 'favorites' || raw === 'watchhistory' || raw === 'login') {
    return [{ name: 'home' }, { name: raw }];
  }
  if (raw.startsWith('details/')) {
    const id = decodeURIComponent(raw.slice('details/'.length));
    const ref = parseMediaRef(id);
    if (ref) {
      return [
        { name: 'home' },
        {
          name: 'details',
          request: { url: id, isTvShow: ref.isTvShow },
          autoPlay: false,
          episodeUrl: null,
        },
      ];
    }
  }
  if (raw.startsWith('actor/')) {
    const id = decodeURIComponent(raw.slice('actor/'.length));
    if (id) return [{ name: 'home' }, { name: 'actor', url: id }];
  }
  if (raw.startsWith('player/')) {
    const id = decodeURIComponent(raw.slice('player/'.length));
    if (id) return [{ name: 'home' }, { name: 'player', url: id }];
  }
  return [{ name: 'home' }];
}

export default function App() {
  const [stack, setStack] = useState(parseInitialStack);
  const [overlayState, setOverlayState] = useState(null);
  const [toastMessage, setToastMessage] = useState(null);
  const [screensaverActive, setScreensaverActive] = useState(false);
  const [isVideoPlaying, setIsVideoPlaying] = useState(false);
  const [systemVoices, setSystemVoices] = useState(() => getPolishSystemVoices());
  const [envBannerDismissed, setEnvBannerDismissed] = useState(false);

  const settings = useSettings();
  const session = useSession();
  const syncStatus = useSyncStatus();

  const overlayDismissRef = useRef(null);
  const inactivityTimerRef = useRef(null);

  const current = stack[stack.length - 1] || { name: 'home' };

  const showToast = useCallback((msg) => {
    if (!msg) return;
    setToastMessage(String(msg));
  }, []);

  const refreshSystemVoices = useCallback(() => {
    setSystemVoices(getPolishSystemVoices());
  }, []);

  useEffect(() => {
    refreshSystemVoices();
    if (typeof window === 'undefined' || !window.speechSynthesis) return undefined;
    window.speechSynthesis.addEventListener?.('voiceschanged', refreshSystemVoices);
    return () => {
      window.speechSynthesis.removeEventListener?.('voiceschanged', refreshSystemVoices);
    };
  }, [refreshSystemVoices]);

  // Keep hash synchronized with current route without triggering hashchange loops
  useEffect(() => {
    if (typeof window === 'undefined') return;
    const targetHash = `#${routeToHash(current)}`;
    if (window.location.hash !== targetHash) {
      window.history.replaceState(null, '', targetHash);
    }
  }, [current]);

  const closeOverlay = useCallback(() => {
    setOverlayState(null);
    const cb = overlayDismissRef.current;
    overlayDismissRef.current = null;
    if (cb) cb();
    setTimeout(() => restoreSavedFocus(), 40);
  }, []);

  const openCustomOverlay = useCallback((menuData, onClosedCallback = null) => {
    overlayDismissRef.current = typeof onClosedCallback === 'function' ? onClosedCallback : null;
    setOverlayState({ kind: 'custom', menuData });
  }, []);

  const openAppSettings = useCallback(() => {
    overlayDismissRef.current = null;
    setOverlayState({ kind: 'app_settings' });
  }, []);

  const navigate = useCallback((route) => {
    if (!route?.name) return;
    setScreensaverActive(false);
    setStack((prev) => {
      if (route.replace && prev.length > 0) {
        return [...prev.slice(0, -1), route];
      }
      if (TOP_LEVEL_TABS.has(route.name)) {
        return [{ name: route.name }];
      }
      return [...prev, route];
    });
    if (typeof window !== 'undefined') {
      window.scrollTo({ top: 0, behavior: 'instant' });
    }
  }, []);

  const selectTopTab = useCallback((tabId) => {
    setScreensaverActive(false);
    setStack([{ name: tabId }]);
    if (typeof window !== 'undefined') {
      window.scrollTo({ top: 0, behavior: 'instant' });
    }
  }, []);

  const goBack = useCallback(() => {
    if (overlayState) {
      closeOverlay();
      return;
    }
    if (screensaverActive) {
      setScreensaverActive(false);
      return;
    }
    setStack((prev) => {
      if (prev.length > 1) return prev.slice(0, -1);
      if (prev[0]?.name !== 'home') return [{ name: 'home' }];
      return prev;
    });
    setTimeout(() => restoreSavedFocus(), 60);
  }, [overlayState, screensaverActive, closeOverlay]);

  const activeMenuData = useMemo(() => {
    if (!overlayState) return null;
    if (overlayState.kind === 'app_settings') {
      return buildAppSettingsMenu({
        settings,
        session,
        syncStatus,
        systemVoices,
        onRefreshSystemVoices: refreshSystemVoices,
        onNavigate: navigate,
        onClose: closeOverlay,
        onToast: showToast,
      });
    }
    return overlayState.menuData;
  }, [
    overlayState,
    settings,
    session,
    syncStatus,
    systemVoices,
    refreshSystemVoices,
    navigate,
    closeOverlay,
    showToast,
  ]);

  // Install D-pad spatial focus & startup background services
  useEffect(() => {
    const uninstallFocus = installTvFocusManager();
    wakeUpStreamApi();
    triggerSync();
    checkForNewEpisodes({ getTvSummaryForEpisodeCheck });

    const syncInterval = setInterval(() => {
      triggerSync();
    }, SYNC_INTERVAL_MS);

    return () => {
      uninstallFocus();
      clearInterval(syncInterval);
    };
  }, []);

  // Global Back (Escape / Backspace) outside Player / Overlay / Screensaver
  useEffect(() => {
    const onKeyDown = (e) => {
      if (current.name === 'player' || activeMenuData || screensaverActive) return;
      const tag = document.activeElement?.tagName;
      const isTextInput = tag === 'INPUT' || tag === 'TEXTAREA';
      if (e.key === 'Escape' || (e.key === 'Backspace' && !isTextInput)) {
        if (stack.length > 1 || current.name !== 'home') {
          e.preventDefault();
          e.stopPropagation();
          goBack();
        }
      }
    };
    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  }, [current.name, activeMenuData, screensaverActive, stack.length, goBack]);

  // Screensaver inactivity timer
  const screensaverSuppressed =
    !settings.screensaverEnabled ||
    isVideoPlaying ||
    current.name === 'player' ||
    current.name === 'login' ||
    Boolean(activeMenuData);

  const resetInactivity = useCallback(() => {
    clearTimeout(inactivityTimerRef.current);
    if (screensaverSuppressed || screensaverActive) return;
    const delay = Math.max(30_000, settings.screensaverInactivityTime || 120_000);
    inactivityTimerRef.current = setTimeout(() => {
      setScreensaverActive(true);
    }, delay);
  }, [screensaverSuppressed, screensaverActive, settings.screensaverInactivityTime]);

  useEffect(() => {
    resetInactivity();
    if (screensaverSuppressed) return undefined;

    const onActivity = () => {
      if (screensaverActive) {
        setScreensaverActive(false);
      }
      resetInactivity();
    };
    const events = ['keydown', 'pointerdown', 'pointermove', 'wheel'];
    for (const evt of events) {
      window.addEventListener(evt, onActivity, { passive: true });
    }
    return () => {
      clearTimeout(inactivityTimerRef.current);
      for (const evt of events) {
        window.removeEventListener(evt, onActivity);
      }
    };
  }, [screensaverSuppressed, screensaverActive, resetInactivity]);

  const showNavigationBar = TOP_LEVEL_TABS.has(current.name);
  const showBackButton = BACK_BAR_ROUTES.has(current.name);

  const focusMainContent = useCallback(() => {
    const mainEl = document.querySelector('.filman-scaffold main');
    const first = mainEl?.querySelector('[data-focusable="true"]');
    first?.focus();
  }, []);

  const missingEnvItems = [];
  if (!TMDB_API_KEY) missingEnvItems.push('TMDB_API_KEY');
  if (!isStreamApiConfigured) missingEnvItems.push('STREAM_API_BASE_URL');

  return (
    <div className="filman-scaffold">
      {current.name !== 'player' && (
        <FilmanNavigationBar
          currentRouteName={current.name}
          showNavigationBar={showNavigationBar}
          showBackButton={showBackButton}
          onSelectTab={selectTopTab}
          onBack={goBack}
          onOpenSettings={openAppSettings}
          onFocusContent={focusMainContent}
        />
      )}

      <main>
        {current.name === 'home' && (
          <HomeScreen
            onNavigate={navigate}
            onOpenOverlay={openCustomOverlay}
            onCloseOverlay={closeOverlay}
          />
        )}
        {current.name === 'movies' && (
          <MoviesScreen
            onNavigate={navigate}
            onOpenOverlay={openCustomOverlay}
            onCloseOverlay={closeOverlay}
          />
        )}
        {current.name === 'tvshows' && (
          <TvShowsScreen
            onNavigate={navigate}
            onOpenOverlay={openCustomOverlay}
            onCloseOverlay={closeOverlay}
          />
        )}
        {current.name === 'forkids' && (
          <ForKidsScreen
            onNavigate={navigate}
            onOpenOverlay={openCustomOverlay}
            onCloseOverlay={closeOverlay}
          />
        )}
        {current.name === 'search' && (
          <SearchScreen
            onNavigate={navigate}
            onOpenOverlay={openCustomOverlay}
            onCloseOverlay={closeOverlay}
          />
        )}
        {current.name === 'details' && (
          <MovieDetailsScreen
            key={`${current.request?.url || current.episodeUrl || ''}_${current.request?.isTvShow ? 'tv' : 'mv'}`}
            request={current.request}
            autoPlay={Boolean(current.autoPlay)}
            episodeUrl={current.episodeUrl || null}
            onNavigate={navigate}
            onOpenOverlay={openCustomOverlay}
            onCloseOverlay={closeOverlay}
          />
        )}
        {current.name === 'actor' && (
          <ActorScreen
            key={current.url}
            url={current.url}
            onNavigate={navigate}
            onOpenOverlay={openCustomOverlay}
            onCloseOverlay={closeOverlay}
          />
        )}
        {current.name === 'favorites' && (
          <FavoritesScreen
            onNavigate={navigate}
            onOpenOverlay={openCustomOverlay}
            onCloseOverlay={closeOverlay}
          />
        )}
        {current.name === 'watchhistory' && (
          <WatchHistoryScreen
            onNavigate={navigate}
            onOpenOverlay={openCustomOverlay}
            onCloseOverlay={closeOverlay}
            onToast={showToast}
          />
        )}
        {current.name === 'login' && (
          <LoginScreen
            onSuccess={() => {
              showToast(t('overlay_menu_synced_just_now'));
              goBack();
            }}
            onBack={goBack}
          />
        )}
        {current.name === 'player' && (
          <PlayerScreen
            key={current.url}
            url={current.url}
            onBack={goBack}
            onNavigate={navigate}
            onToast={showToast}
            onPlaybackChange={setIsVideoPlaying}
          />
        )}
      </main>

      {missingEnvItems.length > 0 && !envBannerDismissed && current.name !== 'player' && (
        <div
          style={{
            position: 'fixed',
            bottom: 'var(--spacing-lg)',
            right: 'var(--spacing-lg)',
            zIndex: 150,
            maxWidth: '420px',
            padding: '12px 16px',
            borderRadius: 'var(--shape-medium)',
            background: 'rgba(147, 0, 10, 0.92)',
            color: 'var(--color-on-error-container)',
            border: '1px solid rgba(255, 218, 214, 0.35)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            gap: '12px',
          }}
        >
          <div className="type-body-small">
            <strong>{t('env_config_warning_title')}:</strong>{' '}
            {t('env_config_warning_desc')} {missingEnvItems.join(', ')}
          </div>
          <button
            type="button"
            onClick={() => setEnvBannerDismissed(true)}
            style={{
              background: 'rgba(255,255,255,0.18)',
              color: '#fff',
              borderRadius: 'var(--shape-small)',
              padding: '4px 10px',
              fontSize: '12px',
              fontWeight: 700,
              flexShrink: 0,
            }}
          >
            ✕
          </button>
        </div>
      )}

      <FilmanOverlayMenu
        menuData={activeMenuData}
        onClose={closeOverlay}
        onResetInactivity={resetInactivity}
      />

      {screensaverActive && (
        <ScreensaverOverlay
          slideDurationMs={settings.screensaverSlideDuration}
          onDismiss={() => {
            setScreensaverActive(false);
            resetInactivity();
          }}
          onToast={showToast}
        />
      )}

      <FilmanToast message={toastMessage} onDismiss={() => setToastMessage(null)} />
    </div>
  );
}
