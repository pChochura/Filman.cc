import { createPersistedStore, useStore } from './createStore.js';
import { canonicalUrl } from '../models/mediaRef.js';

export const DEFAULT_TV_SHOW_SOURCE_SETTINGS = {
  serverName: null,
  version: null,
  quality: null,
  sourceWebsite: null,
  subtitlesEnabled: false,
  subtitleLanguage: null,
  subtitleLabel: null,
  preferredSubtitleGroup: null,
  preferredRipSource: null,
  subtitleOffsetMs: 0,
  playbackSpeed: null,
  aspectRatioMode: null,
  lastKnownEpisodeCount: null,
};

function sanitizeMap(raw) {
  if (!raw || typeof raw !== 'object' || Array.isArray(raw)) return {};
  const out = {};
  for (const [k, v] of Object.entries(raw)) {
    if (v && typeof v === 'object') {
      out[canonicalUrl(k, true)] = { ...DEFAULT_TV_SHOW_SOURCE_SETTINGS, ...v };
    }
  }
  return out;
}

export const tvShowSettingsStore = createPersistedStore(
  'tiwi_tv_show_settings',
  {},
  sanitizeMap,
  'filman_tv_show_settings',
);

export function useTvShowSettings() {
  return useStore(tvShowSettingsStore);
}

export function getMediaSourceSettings(mediaKey) {
  if (!mediaKey) return null;
  const norm = canonicalUrl(mediaKey);
  return tvShowSettingsStore.get()[norm] || null;
}

export function saveMediaSourceSettings(mediaKey, patch) {
  if (!mediaKey) return;
  const norm = canonicalUrl(mediaKey);
  tvShowSettingsStore.set((prev) => ({
    ...prev,
    [norm]: {
      ...DEFAULT_TV_SHOW_SOURCE_SETTINGS,
      ...(prev[norm] || {}),
      ...patch,
    },
  }));
}

export function getTvShowSourceSettings(showKey) {
  return getMediaSourceSettings(showKey);
}

export function saveTvShowSourceSettings(showKey, patch) {
  saveMediaSourceSettings(showKey, patch);
}
