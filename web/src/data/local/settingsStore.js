import { createPersistedStore, useStore } from './createStore.js';
import {
  DEFAULT_LEKTOR_VOICE_ID,
  LektorDucking,
  LektorEdgeVoiceOptions,
  LektorTiming,
} from '../models/lektorModels.js';
import {
  DEFAULT_SUBTITLE_STYLE,
  NextEpisodeAppearance,
  QUALITY_OPTIONS,
  argbIntToCss,
  cssToArgbInt,
} from '../models/playerConstants.js';
import {
  DEFAULT_CONTENT_RULES,
  sanitizeContentRules,
} from '../models/contentClassification.js';

export const DEFAULT_SETTINGS = {
  preferredQuality: 'auto',
  autoPlayNext: true,
  initialAppearanceType: NextEpisodeAppearance.SHOW_IN_OVERLAY,
  initialAppearanceOffset: 105,
  initialAppearancePercentage: 5,
  secondaryAppearanceType: NextEpisodeAppearance.SHOW_WITH_TIMER,
  secondaryAppearanceOffset: 30,
  secondaryAppearancePercentage: 2,
  secondaryTimerAmount: 10,
  subtitleStyle: { ...DEFAULT_SUBTITLE_STYLE },
  screensaverEnabled: true,
  screensaverInactivityTime: 120_000,
  screensaverSlideDuration: 10_000,
  lektorEnabled: false,
  lektorHideSubtitles: false,
  lektorVoice: DEFAULT_LEKTOR_VOICE_ID,
  lektorDuckingVolume: LektorDucking.DEFAULT_VOLUME,
  lektorTimingOffsetMs: LektorTiming.DEFAULT_OFFSET_MS,
  lektorEdgeRate: LektorEdgeVoiceOptions.DEFAULT_RATE,
  lektorEdgePitch: LektorEdgeVoiceOptions.DEFAULT_PITCH,
  lektorEdgeVolume: LektorEdgeVoiceOptions.DEFAULT_VOLUME,
  lektorEdgeCalmMode: LektorEdgeVoiceOptions.DEFAULT_CALM_MODE,
  contentRules: { ...DEFAULT_CONTENT_RULES },
  notificationsEnabled: true,
};

function sanitizeSettings(raw) {
  if (!raw || typeof raw !== 'object') return { ...DEFAULT_SETTINGS };
  const s = { ...DEFAULT_SETTINGS, ...raw };
  if (!QUALITY_OPTIONS.includes(s.preferredQuality)) s.preferredQuality = 'auto';
  s.autoPlayNext = Boolean(s.autoPlayNext);
  s.screensaverEnabled = Boolean(s.screensaverEnabled);
  s.lektorEnabled = Boolean(s.lektorEnabled);
  s.lektorHideSubtitles = Boolean(s.lektorHideSubtitles);
  s.lektorDuckingVolume = LektorDucking.fromStored(s.lektorDuckingVolume);
  s.lektorTimingOffsetMs = LektorTiming.fromStored(s.lektorTimingOffsetMs);
  s.lektorEdgeRate = LektorEdgeVoiceOptions.fromStoredRate(s.lektorEdgeRate);
  s.lektorEdgePitch = LektorEdgeVoiceOptions.fromStoredPitch(s.lektorEdgePitch);
  s.lektorEdgeVolume = LektorEdgeVoiceOptions.fromStoredVolume(s.lektorEdgeVolume);
  s.lektorEdgeCalmMode =
    typeof s.lektorEdgeCalmMode === 'boolean'
      ? s.lektorEdgeCalmMode
      : LektorEdgeVoiceOptions.DEFAULT_CALM_MODE;
  s.subtitleStyle = { ...DEFAULT_SUBTITLE_STYLE, ...(s.subtitleStyle || {}) };
  s.contentRules = sanitizeContentRules(s.contentRules);
  return s;
}

export const settingsStore = createPersistedStore(
  'tiwi_settings',
  DEFAULT_SETTINGS,
  sanitizeSettings,
  'filman_settings',
);

export function useSettings() {
  return useStore(settingsStore);
}

export function updateSetting(key, value) {
  settingsStore.set((prev) => ({ ...prev, [key]: value }));
}

export function updateSubtitleStyle(patch) {
  settingsStore.set((prev) => ({
    ...prev,
    subtitleStyle: { ...prev.subtitleStyle, ...patch },
  }));
}

export function getEffectiveSecondaryAppearanceType(settings = settingsStore.get()) {
  if (!settings.autoPlayNext && settings.secondaryAppearanceType === NextEpisodeAppearance.SHOW_WITH_TIMER) {
    return NextEpisodeAppearance.SHOW;
  }
  return settings.secondaryAppearanceType;
}

export function toSettingsSnapshotMap(settings = settingsStore.get()) {
  const stylePayload = {
    fontSizeFraction: settings.subtitleStyle.fontSizeFraction,
    textColorArgb: cssToArgbInt(settings.subtitleStyle.textColorHex, 0xffffffff | 0),
    backgroundColorArgb: cssToArgbInt(settings.subtitleStyle.backgroundColorHex, 0x00000000),
    edgeType: settings.subtitleStyle.edgeType,
    edgeColorArgb: cssToArgbInt(settings.subtitleStyle.edgeColorHex, 0xff000000 | 0),
    verticalPaddingFraction: settings.subtitleStyle.verticalPaddingFraction,
  };
  return {
    preferred_quality: settings.preferredQuality,
    autoplay_next: String(settings.autoPlayNext),
    subtitle_style: JSON.stringify(stylePayload),
    screensaver_enabled: String(settings.screensaverEnabled),
    screensaver_inactivity_time: String(settings.screensaverInactivityTime),
    screensaver_slide_duration: String(settings.screensaverSlideDuration),
    lektor_enabled: String(settings.lektorEnabled),
    lektor_hide_subtitles: String(settings.lektorHideSubtitles),
    lektor_voice: String(settings.lektorVoice),
    lektor_ducking_volume: String(settings.lektorDuckingVolume),
    lektor_timing_offset_ms: String(settings.lektorTimingOffsetMs),
    lektor_edge_rate: String(settings.lektorEdgeRate),
    lektor_edge_pitch: String(settings.lektorEdgePitch),
    lektor_edge_volume: String(settings.lektorEdgeVolume),
    lektor_edge_calm_mode: String(settings.lektorEdgeCalmMode),
    content_rules: JSON.stringify(settings.contentRules),
  };
}

function parseBoolStrict(val) {
  if (val === 'true') return true;
  if (val === 'false') return false;
  return null;
}

export function applySettingsSnapshotMap(map) {
  if (!map || typeof map !== 'object') return;
  settingsStore.set((prev) => {
    const next = { ...prev };
    if (typeof map.preferred_quality === 'string' && QUALITY_OPTIONS.includes(map.preferred_quality)) {
      next.preferredQuality = map.preferred_quality;
    }
    const autoPlay = parseBoolStrict(map.autoplay_next);
    if (autoPlay !== null) next.autoPlayNext = autoPlay;

    if (typeof map.subtitle_style === 'string') {
      try {
        const parsed = JSON.parse(map.subtitle_style);
        next.subtitleStyle = {
          fontSizeFraction: parsed.fontSizeFraction ?? next.subtitleStyle.fontSizeFraction,
          textColorHex:
            typeof parsed.textColorArgb === 'number'
              ? argbIntToCss(parsed.textColorArgb, '#FFFFFF')
              : next.subtitleStyle.textColorHex,
          backgroundColorHex:
            typeof parsed.backgroundColorArgb === 'number'
              ? argbIntToCss(parsed.backgroundColorArgb, 'transparent')
              : next.subtitleStyle.backgroundColorHex,
          edgeType: parsed.edgeType ?? next.subtitleStyle.edgeType,
          edgeColorHex:
            typeof parsed.edgeColorArgb === 'number'
              ? argbIntToCss(parsed.edgeColorArgb, '#000000')
              : next.subtitleStyle.edgeColorHex,
          verticalPaddingFraction:
            parsed.verticalPaddingFraction ?? next.subtitleStyle.verticalPaddingFraction,
        };
      } catch {
        // ignore invalid subtitle style json
      }
    }

    const ssEnabled = parseBoolStrict(map.screensaver_enabled);
    if (ssEnabled !== null) next.screensaverEnabled = ssEnabled;

    const ssInactivity = Number.parseInt(map.screensaver_inactivity_time, 10);
    if (Number.isFinite(ssInactivity) && ssInactivity > 0) next.screensaverInactivityTime = ssInactivity;

    const ssSlide = Number.parseInt(map.screensaver_slide_duration, 10);
    if (Number.isFinite(ssSlide) && ssSlide > 0) next.screensaverSlideDuration = ssSlide;

    const lekEnabled = parseBoolStrict(map.lektor_enabled);
    if (lekEnabled !== null) next.lektorEnabled = lekEnabled;

    const lekHide = parseBoolStrict(map.lektor_hide_subtitles);
    if (lekHide !== null) next.lektorHideSubtitles = lekHide;

    if (typeof map.lektor_voice === 'string' && map.lektor_voice) {
      next.lektorVoice = map.lektor_voice;
    }

    const duck = Number.parseFloat(map.lektor_ducking_volume);
    if (Number.isFinite(duck) && LektorDucking.levels.includes(duck)) {
      next.lektorDuckingVolume = duck;
    }

    const timing = Number.parseInt(map.lektor_timing_offset_ms, 10);
    if (Number.isFinite(timing) && LektorTiming.offsetsMs.includes(timing)) {
      next.lektorTimingOffsetMs = timing;
    }

    if (LektorEdgeVoiceOptions.rates.includes(map.lektor_edge_rate)) {
      next.lektorEdgeRate = map.lektor_edge_rate;
    }
    if (LektorEdgeVoiceOptions.pitches.includes(map.lektor_edge_pitch)) {
      next.lektorEdgePitch = map.lektor_edge_pitch;
    }
    if (LektorEdgeVoiceOptions.volumes.includes(map.lektor_edge_volume)) {
      next.lektorEdgeVolume = map.lektor_edge_volume;
    }
    const calm = parseBoolStrict(map.lektor_edge_calm_mode);
    if (calm !== null) next.lektorEdgeCalmMode = calm;

    if (typeof map.content_rules === 'string') {
      try {
        const parsed = JSON.parse(map.content_rules);
        if (parsed && typeof parsed === 'object') {
          next.contentRules = sanitizeContentRules(parsed);
        }
      } catch {
        // ignore invalid content rules json
      }
    }

    return next;
  });
}

export function updateContentRule(categoryKey, patch) {
  settingsStore.set((prev) => ({
    ...prev,
    contentRules: {
      ...prev.contentRules,
      [categoryKey]: {
        ...(prev.contentRules[categoryKey] || DEFAULT_CONTENT_RULES[categoryKey] || {}),
        ...patch,
      },
    },
  }));
}
