export const LEKTOR_VOICES = [
  {
    id: 'edge_marek',
    displayName: 'Marek Neural (online)',
    edgeVoiceName: 'pl-PL-MarekNeural',
    isOnline: true,
    isSystem: false,
  },
  {
    id: 'edge_zofia',
    displayName: 'Zofia Neural (online)',
    edgeVoiceName: 'pl-PL-ZofiaNeural',
    isOnline: true,
    isSystem: false,
  },
  {
    id: 'edge_andrew',
    displayName: 'Andrew Multilingual (online)',
    edgeVoiceName: 'en-US-AndrewMultilingualNeural',
    isOnline: true,
    isSystem: false,
  },
  {
    id: 'edge_ava',
    displayName: 'Ava Multilingual (online)',
    edgeVoiceName: 'en-US-AvaMultilingualNeural',
    isOnline: true,
    isSystem: false,
  },
  {
    id: 'edge_brian',
    displayName: 'Brian Multilingual (online)',
    edgeVoiceName: 'en-US-BrianMultilingualNeural',
    isOnline: true,
    isSystem: false,
  },
  {
    id: 'edge_emma',
    displayName: 'Emma Multilingual (online)',
    edgeVoiceName: 'en-US-EmmaMultilingualNeural',
    isOnline: true,
    isSystem: false,
  },
  {
    id: 'edge_remy',
    displayName: 'Remy Multilingual (online)',
    edgeVoiceName: 'fr-FR-RemyMultilingualNeural',
    isOnline: true,
    isSystem: false,
  },
  {
    id: 'edge_vivienne',
    displayName: 'Vivienne Multilingual (online)',
    edgeVoiceName: 'fr-FR-VivienneMultilingualNeural',
    isOnline: true,
    isSystem: false,
  },
  {
    id: 'system',
    displayName: 'Głos systemowy (szybszy)',
    edgeVoiceName: null,
    isOnline: false,
    isSystem: true,
  },
];

export const DEFAULT_LEKTOR_VOICE_ID = 'edge_marek';
const SYSTEM_VOICE_PREFIX = 'system:';

export function systemVoiceNameFromId(id) {
  if (typeof id !== 'string' || !id.startsWith(SYSTEM_VOICE_PREFIX)) return null;
  const name = id.slice(SYSTEM_VOICE_PREFIX.length);
  return name.trim() ? name : null;
}

export function makeSystemVoiceId(name) {
  return `${SYSTEM_VOICE_PREFIX}${name}`;
}

export function getLektorVoiceById(id) {
  if (systemVoiceNameFromId(id) !== null) {
    return LEKTOR_VOICES.find((v) => v.id === 'system');
  }
  return (
    LEKTOR_VOICES.find((v) => v.id.toLowerCase() === String(id || '').toLowerCase()) ||
    LEKTOR_VOICES[0]
  );
}

export const LektorEdgeVoiceOptions = {
  DEFAULT_RATE: '+10%',
  DEFAULT_PITCH: '+0Hz',
  DEFAULT_VOLUME: '+0%',
  DEFAULT_CALM_MODE: true,
  DEFAULT_RATE_PERCENT: 10,
  MIN_RATE_PERCENT: -30,
  MAX_RATE_PERCENT: 45,
  rates: ['-20%', '-10%', '+0%', '+5%', '+10%', '+15%', '+25%', '+35%'],
  pitches: ['-20Hz', '-10Hz', '-6Hz', '-3Hz', '+0Hz', '+3Hz', '+6Hz', '+10Hz', '+20Hz'],
  volumes: ['-50%', '-35%', '-25%', '-15%', '+0%', '+15%', '+30%'],

  fromStoredRate(stored) {
    return this.rates.includes(stored) ? stored : this.DEFAULT_RATE;
  },
  fromStoredPitch(stored) {
    return this.pitches.includes(stored) ? stored : this.DEFAULT_PITCH;
  },
  fromStoredVolume(stored) {
    return this.volumes.includes(stored) ? stored : this.DEFAULT_VOLUME;
  },
  parseRatePercent(rate) {
    const parsed = Number.parseInt(String(rate || '').trim().replace(/%$/, ''), 10);
    return Number.isFinite(parsed) ? parsed : this.DEFAULT_RATE_PERCENT;
  },
  formatRatePercent(percent) {
    const clamped = Math.max(this.MIN_RATE_PERCENT, Math.min(this.MAX_RATE_PERCENT, Math.round(percent)));
    return clamped >= 0 ? `+${clamped}%` : `${clamped}%`;
  },
};

export const LektorDucking = {
  DEFAULT_VOLUME: 1.0,
  levels: [1.0, 0.8, 0.7, 0.6],
  fromStored(volume) {
    const v = Number(volume);
    if (!Number.isFinite(v)) return this.DEFAULT_VOLUME;
    if (Math.abs(v - 0.12) < 0.001) return 0.6;
    if (Math.abs(v - 0.22) < 0.001) return 0.7;
    if (Math.abs(v - 0.35) < 0.001 || Math.abs(v - 0.75) < 0.001) return 0.8;
    let best = this.levels[0];
    let bestDiff = Math.abs(best - v);
    for (const level of this.levels) {
      const diff = Math.abs(level - v);
      if (diff < bestDiff) {
        best = level;
        bestDiff = diff;
      }
    }
    return best;
  },
};

export const LektorTiming = {
  DEFAULT_OFFSET_MS: -500,
  offsetsMs: [-1000, -500, 0, 500, 1000],
  fromStored(offset) {
    const n = Number(offset);
    return this.offsetsMs.includes(n) ? n : this.DEFAULT_OFFSET_MS;
  },
};
