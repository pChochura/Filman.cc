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
import TiwiNavigationBar from './components/TiwiNavigationBar.jsx';
import TiwiOverlayMenu from './components/TiwiOverlayMenu.jsx';
import TiwiToast from './components/TiwiToast.jsx';
import { handleOverlayPopstate, resetOverlayHistory } from './components/overlayHistoryManager.js';
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
  const isInternalBackRef = useRef(false);
  const historyDepthRef = useRef(stack.length || 1);

  const current = stack[stack.length - 1] || { name: 'home' };
  const currentRef = useRef(current);
  currentRef.current = current;
  const overlayStateRef = useRef(overlayState);
  overlayStateRef.current = overlayState;
  const screensaverActiveRef = useRef(screensaverActive);
  screensaverActiveRef.current = screensaverActive;

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

  // Seed initial history state if not yet tagged with depth
  useEffect(() => {
    if (typeof window === 'undefined') return;
    const initialHash = `#${routeToHash(current)}`;
    if (!window.history.state || typeof window.history.state.depth !== 'number') {
      window.history.replaceState({ depth: stack.length || 1 }, '', initialHash);
    }
  }, []);

  // Keep hash synchronized with current route without triggering hashchange loops
  useEffect(() => {
    if (typeof window === 'undefined') return;
    const targetHash = `#${routeToHash(current)}`;
    if (window.location.hash !== targetHash) {
      window.history.replaceState({ depth: historyDepthRef.current }, '', targetHash);
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
    resetOverlayHistory();
    setOverlayState(null);
    overlayDismissRef.current = null;

    if (typeof window !== 'undefined') {
      const targetHash = `#${routeToHash(route)}`;
      if (route.replace) {
        window.history.replaceState({ depth: historyDepthRef.current }, '', targetHash);
      } else {
        historyDepthRef.current += 1;
        window.history.pushState({ depth: historyDepthRef.current }, '', targetHash);
      }
    }

    setStack((prev) => {
      let cleaned = prev;
      // If navigating to player from a details route that had autoPlay: true,
      // clear autoPlay so returning from player later doesn't loop back!
      if (route.name === 'player' && prev.length > 0) {
        const top = prev[prev.length - 1];
        if (top?.name === 'details' && top.autoPlay) {
          cleaned = [...prev.slice(0, -1), { ...top, autoPlay: false }];
        }
      }
      if (route.replace && cleaned.length > 0) {
        return [...cleaned.slice(0, -1), route];
      }
      if (TOP_LEVEL_TABS.has(route.name)) {
        return [{ name: route.name }];
      }
      return [...cleaned, route];
    });
    if (typeof window !== 'undefined') {
      window.scrollTo({ top: 0, behavior: 'instant' });
    }
  }, []);

  const selectTopTab = useCallback((tabId) => {
    setScreensaverActive(false);
    setOverlayState(null);
    overlayDismissRef.current = null;

    if (typeof window !== 'undefined') {
      const targetHash = `#${tabId}`;
      if (window.location.hash !== targetHash) {
        historyDepthRef.current += 1;
        window.history.pushState({ depth: historyDepthRef.current }, '', targetHash);
      }
    }

    setStack([{ name: tabId }]);
    if (typeof window !== 'undefined') {
      window.scrollTo({ top: 0, behavior: 'instant' });
    }
  }, []);

  const goBackInternal = useCallback(() => {
    const cur = currentRef.current;
    // When on the player screen, unconditionally close the player (never get blocked by stale overlays)
    if (cur.name === 'player') {
      setOverlayState(null);
      setScreensaverActive(false);
      setStack((prev) => {
        if (prev.length > 1) {
          const next = prev.slice(0, -1);
          const top = next[next.length - 1];
          if (top?.name === 'details' && top.autoPlay) {
            return [...next.slice(0, -1), { ...top, autoPlay: false }];
          }
          return next;
        }
        if (prev[0]?.name !== 'home') return [{ name: 'home' }];
        return prev;
      });
      setTimeout(() => restoreSavedFocus(), 60);
      return;
    }

    if (overlayStateRef.current) {
      closeOverlay();
      return;
    }
    if (screensaverActiveRef.current) {
      setScreensaverActive(false);
      return;
    }
    setStack((prev) => {
      if (prev.length > 1) {
        const next = prev.slice(0, -1);
        const top = next[next.length - 1];
        if (top?.name === 'details' && top.autoPlay) {
          return [...next.slice(0, -1), { ...top, autoPlay: false }];
        }
        return next;
      }
      if (prev[0]?.name !== 'home') return [{ name: 'home' }];
      return prev;
    });
    setTimeout(() => restoreSavedFocus(), 60);
  }, [closeOverlay]);

  // In-app Back trigger (Esc key, player top-bar back button, navbar back button)
  const goBack = useCallback(() => {
    if (typeof window !== 'undefined' && historyDepthRef.current > 1) {
      isInternalBackRef.current = true;
      historyDepthRef.current -= 1;
      window.history.back();
    }
    goBackInternal();
  }, [goBackInternal]);

  // Browser's back & forward buttons (popstate)
  useEffect(() => {
    if (typeof window === 'undefined') return undefined;
    const onPopState = (e) => {
      if (isInternalBackRef.current) {
        isInternalBackRef.current = false;
        return;
      }
      if (handleOverlayPopstate()) {
        return;
      }
      const targetDepth = e.state?.depth ?? Math.max(1, historyDepthRef.current - 1);
      historyDepthRef.current = Math.max(1, targetDepth);
      goBackInternal();
    };
    window.addEventListener('popstate', onPopState);
    return () => window.removeEventListener('popstate', onPopState);
  }, [goBackInternal]);

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
    const mainEl = document.querySelector('.tiwi-scaffold main, .filman-scaffold main');
    const first = mainEl?.querySelector('[data-focusable="true"]');
    first?.focus();
  }, []);

  const effectiveTmdbApiKey =
    (typeof window !== 'undefined' && window.__ENV__ && window.__ENV__.VITE_TMDB_API_KEY) ||
    TMDB_API_KEY;
  const effectiveStreamConfigured =
    (typeof window !== 'undefined' && window.__ENV__ && window.__ENV__.VITE_STREAM_API_CONFIGURED) ||
    isStreamApiConfigured;

  const missingEnvItems = [];
  if (!effectiveTmdbApiKey) missingEnvItems.push('TMDB_API_KEY');
  if (!effectiveStreamConfigured) missingEnvItems.push('STREAM_API_BASE_URL');

  const isOverlayOpen = Boolean(activeMenuData);

  return (
    <div className={`tiwi-scaffold ${isOverlayOpen ? 'tiwi-scaffold--overlay-active' : ''}`}>
      {current.name !== 'player' && (
        <div
          className="tiwi-nav-container"
          inert={isOverlayOpen ? '' : undefined}
          aria-hidden={isOverlayOpen ? 'true' : undefined}
        >
          <TiwiNavigationBar
            currentRouteName={current.name}
            showNavigationBar={showNavigationBar}
            showBackButton={showBackButton}
            onSelectTab={selectTopTab}
            onBack={goBack}
            onOpenSettings={openAppSettings}
            onFocusContent={focusMainContent}
          />
        </div>
      )}

      <main
        inert={isOverlayOpen ? '' : undefined}
        aria-hidden={isOverlayOpen ? 'true' : undefined}
      >
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

      <TiwiOverlayMenu
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

      <TiwiToast message={toastMessage} onDismiss={() => setToastMessage(null)} />
    </div>
  );
}
