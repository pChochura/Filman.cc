import { createPersistedStore, useStore } from './createStore.js';

const MAX_HISTORY = 15;

function sanitizeHistory(raw) {
  if (!Array.isArray(raw)) return [];
  const seen = new Set();
  const out = [];
  for (const item of raw) {
    if (typeof item === 'string') {
      const trimmed = item.trim();
      if (trimmed && !seen.has(trimmed)) {
        seen.add(trimmed);
        out.push(trimmed);
      }
    }
  }
  return out.slice(0, MAX_HISTORY);
}

export const searchHistoryStore = createPersistedStore(
  'tiwi_search_history',
  [],
  sanitizeHistory,
  'filman_search_history',
);

export function useSearchHistory() {
  return useStore(searchHistoryStore);
}

export function addSearchQuery(query) {
  const trimmed = String(query || '').trim();
  if (!trimmed) return;
  searchHistoryStore.set((prev) => [trimmed, ...prev.filter((q) => q !== trimmed)].slice(0, MAX_HISTORY));
}

export function removeSearchQuery(query) {
  searchHistoryStore.set((prev) => prev.filter((q) => q !== query));
}

export function clearSearchHistory() {
  searchHistoryStore.set([]);
}
