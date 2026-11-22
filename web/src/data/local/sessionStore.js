import { createPersistedStore, useStore } from './createStore.js';

const DEFAULT_SESSION = {
  username: null,
  authToken: null,
  userId: null,
  lastSyncTimestamp: 0,
};

function sanitizeSession(raw) {
  if (!raw || typeof raw !== 'object') return { ...DEFAULT_SESSION };
  return {
    username: typeof raw.username === 'string' && raw.username ? raw.username : null,
    authToken: typeof raw.authToken === 'string' && raw.authToken ? raw.authToken : null,
    userId: typeof raw.userId === 'string' && raw.userId ? raw.userId : null,
    lastSyncTimestamp: Number(raw.lastSyncTimestamp) || 0,
  };
}

export const sessionStore = createPersistedStore(
  'tiwi_session',
  DEFAULT_SESSION,
  sanitizeSession,
  'filman_session',
);

// Auto-sync session if injected by HTTP Auth router
if (
  typeof window !== 'undefined' &&
  window.__INITIAL_SESSION__ &&
  window.__INITIAL_SESSION__.token
) {
  const current = sessionStore.get();
  const isManualLogout =
    typeof sessionStorage !== 'undefined' &&
    sessionStorage.getItem('tiwi_manual_logout') === 'true';

  if (
    !isManualLogout &&
    (!current.authToken ||
      current.username !== window.__INITIAL_SESSION__.username ||
      current.authToken !== window.__INITIAL_SESSION__.token)
  ) {
    saveAuthSession(window.__INITIAL_SESSION__);
  }
}

export function useSession() {
  const session = useStore(sessionStore);
  return {
    ...session,
    isLoggedIn: Boolean(session.authToken),
  };
}

export function isLoggedIn() {
  return Boolean(sessionStore.get().authToken);
}

export function saveAuthSession({ token, userId, username }) {
  sessionStore.set({
    authToken: token,
    userId: userId || null,
    username: username || null,
    lastSyncTimestamp: 0,
  });
}

export function updateLastSyncTimestamp(ts) {
  sessionStore.set((prev) => ({
    ...prev,
    lastSyncTimestamp: Number(ts) || Date.now(),
  }));
}

export function clearAuthSession() {
  sessionStore.set({ ...DEFAULT_SESSION });
}
