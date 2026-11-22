import { createPersistedStore, useStore } from '../local/createStore.js';
import {
  clearAuthSession,
  saveAuthSession,
  sessionStore,
  updateLastSyncTimestamp,
} from '../local/sessionStore.js';
import {
  clearDeletedFavorites,
  deletedFavoritesStore,
  favoritesStore,
  mergeSyncedFavorites,
} from '../local/favoritesStore.js';
import {
  clearDeletedWatchlist,
  deletedWatchlistStore,
  mergeSyncedWatchlist,
  watchlistStore,
} from '../local/watchlistStore.js';
import { mergeSyncedProgress, progressStore } from '../local/progressStore.js';
import {
  applySettingsSnapshotMap,
  settingsStore,
  toSettingsSnapshotMap,
} from '../local/settingsStore.js';
import { makeEpisodeKey, makeMovieKey, makeShowKey, parseMediaRef } from '../models/mediaRef.js';

export const syncStatusStore = createPersistedStore(
  'tiwi_sync_ui_status_ephemeral',
  { state: 'idle', timestamp: 0, message: null },
  (v) => v,
  'filman_sync_ui_status_ephemeral',
);

// Always start in idle on fresh boot
syncStatusStore.set({ state: 'idle', timestamp: 0, message: null });

export function useSyncStatus() {
  return useStore(syncStatusStore);
}

let syncInFlight = null;
let isSyncing = false;
let debounceTimer = null;

export function triggerDebouncedSync(delayMs = 3000) {
  if (debounceTimer) clearTimeout(debounceTimer);
  debounceTimer = setTimeout(() => {
    debounceTimer = null;
    triggerSync();
  }, delayMs);
}

function handleAutoSyncTrigger() {
  if (isSyncing) return;
  const session = sessionStore.get();
  if (session?.authToken) {
    triggerDebouncedSync();
  }
}

progressStore.subscribe(handleAutoSyncTrigger);
watchlistStore.subscribe(handleAutoSyncTrigger);
favoritesStore.subscribe(handleAutoSyncTrigger);
deletedWatchlistStore.subscribe(handleAutoSyncTrigger);
deletedFavoritesStore.subscribe(handleAutoSyncTrigger);
settingsStore.subscribe(handleAutoSyncTrigger);

if (typeof window !== 'undefined') {
  window.addEventListener('visibilitychange', () => {
    if (document.visibilityState === 'hidden') {
      const session = sessionStore.get();
      if (session?.authToken) {
        triggerSync().catch(() => {});
      }
    }
  });
}

function resolveEntryMediaRef(entry) {
  if (!entry) return null;
  if (entry.tmdbId && entry.tmdbId > 0) {
    if (entry.type === 'TV') {
      const key =
        entry.season != null && entry.episode != null
          ? makeEpisodeKey(entry.tmdbId, entry.season, entry.episode)
          : makeShowKey(entry.tmdbId);
      return parseMediaRef(key, true);
    }
    return parseMediaRef(makeMovieKey(entry.tmdbId), false);
  }
  if (typeof entry.url === 'string' && entry.url.trim()) {
    const isTvHint =
      entry.isTvShow ??
      (entry.season != null ||
        Boolean(entry.parentUrl && entry.parentUrl !== entry.url && entry.parentUrl.startsWith('show_')));
    return parseMediaRef(entry.url, isTvHint);
  }
  return null;
}

async function callSyncEndpoint(action, { method = 'GET', since = 0, token = null, body = null } = {}) {
  const url =
    action === 'pull'
      ? `/api/sync?action=pull&since=${encodeURIComponent(String(since))}`
      : `/api/sync?action=${encodeURIComponent(action)}`;

  const headers = { Accept: 'application/json' };
  if (token) headers.Authorization = `Bearer ${token}`;
  if (body !== null) headers['Content-Type'] = 'application/json';

  const res = await fetch(url, {
    method,
    headers,
    body: body !== null ? JSON.stringify(body) : undefined,
  });

  const text = await res.text();
  let json = null;
  try {
    json = text ? JSON.parse(text) : {};
  } catch {
    json = { success: false, message: text || `HTTP ${res.status}` };
  }

  if (!res.ok) {
    throw new Error(json?.message || `HTTP ${res.status}`);
  }
  return json;
}

export async function loginAccount(username, password) {
  if (typeof sessionStorage !== 'undefined') {
    sessionStorage.removeItem('tiwi_manual_logout');
  }
  const resp = await callSyncEndpoint('login', {
    method: 'POST',
    body: { username, password },
  });
  if (resp.success === false) {
    throw new Error(resp.message || 'Invalid credentials');
  }
  const token = resp.token || `session_${Date.now()}`;
  saveAuthSession({
    token,
    userId: resp.user?.id || null,
    username: resp.user?.username || username,
  });
  triggerSync();
  return resp;
}

export async function registerAccount(username, password) {
  if (typeof sessionStorage !== 'undefined') {
    sessionStorage.removeItem('tiwi_manual_logout');
  }
  const resp = await callSyncEndpoint('register', {
    method: 'POST',
    body: { username, password },
  });
  if (resp.success === false) {
    throw new Error(resp.message || 'Registration failed');
  }
  const token = resp.token || `session_${Date.now()}`;
  saveAuthSession({
    token,
    userId: resp.user?.id || null,
    username: resp.user?.username || username,
  });
  triggerSync();
  return resp;
}

export function logoutAccount() {
  if (typeof sessionStorage !== 'undefined') {
    sessionStorage.setItem('tiwi_manual_logout', 'true');
  }
  clearAuthSession();
  syncStatusStore.set({ state: 'idle', timestamp: 0, message: null });
}

export function triggerSync() {
  if (debounceTimer) {
    clearTimeout(debounceTimer);
    debounceTimer = null;
  }
  return syncNow().catch(() => null);
}

export async function syncNow() {
  if (syncInFlight) return syncInFlight;

  const session = sessionStore.get();
  if (!session.authToken) {
    return null;
  }

  syncInFlight = (async () => {
    isSyncing = true;
    syncStatusStore.set({ state: 'syncing', timestamp: 0, message: null });
    try {
      const pullResponse = await callSyncEndpoint('pull', {
        method: 'GET',
        since: session.lastSyncTimestamp || 0,
        token: session.authToken,
      });

      if (Array.isArray(pullResponse.progress) && pullResponse.progress.length > 0) {
        const pulledProgress = [];
        for (const p of pullResponse.progress) {
          const ref = resolveEntryMediaRef(p);
          if (!ref) continue;
          const canonicalParent = ref.isTvShow ? ref.parentKey : ref.canonicalKey;
          pulledProgress.push({
            kind: p.isWatched ? 'Watched' : 'InProgress',
            url: ref.canonicalKey,
            parentUrl: canonicalParent,
            posterUrl: p.posterUrl || '',
            titlePl: p.titlePl || '',
            season: ref.season ?? p.season ?? null,
            episode: ref.episode ?? p.episode ?? null,
            seriesTitle: p.seriesTitle ?? null,
            episodeTitle: p.episodeTitle ?? null,
            hasNextEpisode: Boolean(p.hasNextEpisode),
            progressPercentage: p.isWatched ? 1.0 : Number(p.progressPercentage) || 0,
            progressMs: Number(p.progressMs) || 0,
            timestamp: Number(p.updatedAt) || Date.now(),
          });
        }
        mergeSyncedProgress(pulledProgress);
      }

      if (Array.isArray(pullResponse.watchlist) && pullResponse.watchlist.length > 0) {
        const activeItems = [];
        const deletedUrls = new Set();
        for (const entry of pullResponse.watchlist) {
          const ref = resolveEntryMediaRef(entry);
          if (!ref) continue;
          if (entry.isDeleted) {
            deletedUrls.add(ref.canonicalKey);
          } else {
            activeItems.push({
              url: ref.canonicalKey,
              titlePl: entry.titlePl || '',
              posterUrl: entry.posterUrl || '',
              backgroundUrl: entry.backgroundUrl || null,
              isTvShow: ref.isTvShow,
              year: entry.year ?? null,
              detailsRequest: { url: ref.canonicalKey, isTvShow: ref.isTvShow },
            });
          }
        }
        mergeSyncedWatchlist(activeItems, deletedUrls);
      }

      if (Array.isArray(pullResponse.favorites) && pullResponse.favorites.length > 0) {
        const activeItems = [];
        const deletedUrls = new Set();
        for (const entry of pullResponse.favorites) {
          const ref = resolveEntryMediaRef(entry);
          if (!ref) continue;
          if (entry.isDeleted) {
            deletedUrls.add(ref.canonicalKey);
          } else {
            activeItems.push({
              url: ref.canonicalKey,
              titlePl: entry.titlePl || '',
              posterUrl: entry.posterUrl || '',
              backgroundUrl: entry.backgroundUrl || null,
              isTvShow: ref.isTvShow,
              year: entry.year ?? null,
              detailsRequest: { url: ref.canonicalKey, isTvShow: ref.isTvShow },
            });
          }
        }
        mergeSyncedFavorites(activeItems, deletedUrls);
      }

      if (pullResponse.preferences && typeof pullResponse.preferences === 'object') {
        applySettingsSnapshotMap(pullResponse.preferences);
      }

      const localProgressEntries = [];
      for (const item of progressStore.get()) {
        const ref = parseMediaRef(item.url, item.season != null);
        if (!ref) continue;
        const canonicalParent = ref.isTvShow ? ref.parentKey : ref.canonicalKey;
        localProgressEntries.push({
          url: ref.canonicalKey,
          parentUrl: canonicalParent,
          type: ref.type,
          tmdbId: ref.tmdbId,
          season: ref.season ?? item.season ?? null,
          episode: ref.episode ?? item.episode ?? null,
          hasNextEpisode: Boolean(item.hasNextEpisode),
          progressPercentage: item.progressPercentage ?? 0,
          progressMs: item.kind === 'InProgress' || item.kind === 'Watched' ? item.progressMs || 0 : 0,
          posterUrl: item.posterUrl || '',
          titlePl: item.titlePl || '',
          seriesTitle: item.seriesTitle ?? null,
          episodeTitle: item.episodeTitle ?? null,
          isWatched: item.kind === 'Watched',
          updatedAt: item.timestamp || Date.now(),
        });
      }

      const mapMediaItemsToSync = (items) => {
        const out = [];
        for (const movie of items) {
          const ref = parseMediaRef(movie.url, movie.isTvShow);
          if (!ref) continue;
          out.push({
            url: ref.canonicalKey,
            isTvShow: ref.isTvShow,
            type: ref.type,
            tmdbId: ref.tmdbId,
            titlePl: movie.titlePl || '',
            posterUrl: movie.posterUrl || '',
            backgroundUrl: movie.backgroundUrl ?? null,
            year: movie.year ?? null,
            isDeleted: false,
            updatedAt: Date.now(),
          });
        }
        return out;
      };

      const tombstoneWatchlistUrls = deletedWatchlistStore.get() || [];
      const tombstoneWatchlist = tombstoneWatchlistUrls.map((url) => {
        const ref = parseMediaRef(url);
        return {
          url: ref?.canonicalKey || url,
          isTvShow: ref ? ref.isTvShow : false,
          type: ref?.type || 'MOVIE',
          tmdbId: ref?.tmdbId || 0,
          titlePl: '',
          isDeleted: true,
          updatedAt: Date.now(),
        };
      });

      const tombstoneFavoritesUrls = deletedFavoritesStore.get() || [];
      const tombstoneFavorites = tombstoneFavoritesUrls.map((url) => {
        const ref = parseMediaRef(url);
        return {
          url: ref?.canonicalKey || url,
          isTvShow: ref ? ref.isTvShow : false,
          type: ref?.type || 'MOVIE',
          tmdbId: ref?.tmdbId || 0,
          titlePl: '',
          isDeleted: true,
          updatedAt: Date.now(),
        };
      });

      const pushPayload = {
        clientTimestamp: Date.now(),
        progress: localProgressEntries,
        watchlist: [...mapMediaItemsToSync(watchlistStore.get()), ...tombstoneWatchlist],
        favorites: [...mapMediaItemsToSync(favoritesStore.get()), ...tombstoneFavorites],
        preferences: toSettingsSnapshotMap(),
      };

      const pushResponse = await callSyncEndpoint('push', {
        method: 'POST',
        token: session.authToken,
        body: pushPayload,
      });

      if (tombstoneWatchlistUrls.length > 0) {
        clearDeletedWatchlist(tombstoneWatchlistUrls);
      }
      if (tombstoneFavoritesUrls.length > 0) {
        clearDeletedFavorites(tombstoneFavoritesUrls);
      }

      const serverTime =
        pushResponse?.serverTimestamp && pushResponse.serverTimestamp > 0
          ? pushResponse.serverTimestamp
          : Date.now();
      updateLastSyncTimestamp(serverTime);
      syncStatusStore.set({ state: 'success', timestamp: serverTime, message: null });
      return true;
    } catch (err) {
      syncStatusStore.set({
        state: 'error',
        timestamp: 0,
        message: err?.message || 'Sync error',
      });
      throw err;
    } finally {
      isSyncing = false;
      syncInFlight = null;
    }
  })();

  return syncInFlight;
}
