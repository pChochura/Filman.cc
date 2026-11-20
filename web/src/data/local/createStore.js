import { useSyncExternalStore } from 'react';

const memoryStorage = new Map();

export function readStorage(key) {
  try {
    if (typeof window !== 'undefined' && window.localStorage) {
      return window.localStorage.getItem(key);
    }
  } catch {
    // ignore storage quota/security errors
  }
  return memoryStorage.has(key) ? memoryStorage.get(key) : null;
}

export function writeStorage(key, value) {
  try {
    if (typeof window !== 'undefined' && window.localStorage) {
      if (value === null || value === undefined) {
        window.localStorage.removeItem(key);
      } else {
        window.localStorage.setItem(key, value);
      }
      return;
    }
  } catch {
    // ignore
  }
  if (value === null || value === undefined) {
    memoryStorage.delete(key);
  } else {
    memoryStorage.set(key, value);
  }
}

export function createPersistedStore(storageKey, initialValue, sanitize = (v) => v) {
  let state = initialValue;
  const raw = readStorage(storageKey);
  if (raw !== null) {
    try {
      state = sanitize(JSON.parse(raw));
    } catch {
      state = initialValue;
    }
  }

  const listeners = new Set();

  function get() {
    return state;
  }

  function set(updater) {
    const next = typeof updater === 'function' ? updater(state) : updater;
    const sanitized = sanitize(next);
    state = sanitized;
    try {
      writeStorage(storageKey, JSON.stringify(sanitized));
    } catch {
      // ignore
    }
    for (const listener of listeners) {
      listener();
    }
  }

  function subscribe(listener) {
    listeners.add(listener);
    return () => listeners.delete(listener);
  }

  return { get, set, subscribe };
}

export function useStore(store) {
  return useSyncExternalStore(store.subscribe, store.get, store.get);
}
