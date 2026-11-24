export const ContentCategory = {
  KIDS_FAMILY: 'kids',
  ANIME: 'anime',
  NATIVE_DOMESTIC: 'native_pl',
  FOREIGN_MAINSTREAM: 'foreign_en',
  FOREIGN_WORLD: 'foreign_world',
  DOCUMENTARY: 'documentary',
};

export const AudioPreferenceMode = {
  DUBBING_PREFERRED: 'dubbing',
  LEKTOR_PREFERRED: 'lektor',
  ORIGINAL_SUBTITLES: 'original_subtitles',
  ORIGINAL_ONLY: 'original_only',
};

export const DEFAULT_CONTENT_RULES = {
  [ContentCategory.KIDS_FAMILY]: {
    audioMode: AudioPreferenceMode.DUBBING_PREFERRED,
    subtitlesEnabled: false,
    subtitleLanguage: 'pl',
  },
  [ContentCategory.ANIME]: {
    audioMode: AudioPreferenceMode.ORIGINAL_SUBTITLES,
    subtitlesEnabled: true,
    subtitleLanguage: 'pl',
  },
  [ContentCategory.NATIVE_DOMESTIC]: {
    audioMode: AudioPreferenceMode.ORIGINAL_ONLY,
    subtitlesEnabled: false,
    subtitleLanguage: 'pl',
  },
  [ContentCategory.FOREIGN_MAINSTREAM]: {
    audioMode: AudioPreferenceMode.LEKTOR_PREFERRED,
    subtitlesEnabled: false,
    subtitleLanguage: 'pl',
  },
  [ContentCategory.FOREIGN_WORLD]: {
    audioMode: AudioPreferenceMode.ORIGINAL_SUBTITLES,
    subtitlesEnabled: true,
    subtitleLanguage: 'pl',
  },
  [ContentCategory.DOCUMENTARY]: {
    audioMode: AudioPreferenceMode.LEKTOR_PREFERRED,
    subtitlesEnabled: false,
    subtitleLanguage: 'pl',
  },
};

export function classifyContent(
  { genreIds = [], originalLanguage = '', countries = [] } = {},
  isFromKidsSection = false,
) {
  const lang = String(originalLanguage || '').toLowerCase().trim();
  const cList = (countries || []).map((c) => String(c).toLowerCase().trim());
  const isJapan = lang === 'ja' || cList.some((c) => c === 'japan' || c === 'japonia' || c === 'jp');
  const isPoland =
    lang === 'pl' ||
    lang === 'pol' ||
    cList.some((c) => c === 'pl' || c === 'pol' || c === 'poland' || c === 'polska');
  const isAnimation = genreIds.includes(16);
  const isFamilyOrKids = genreIds.includes(10751) || genreIds.includes(10762);

  if (isPoland) {
    return ContentCategory.NATIVE_DOMESTIC;
  }
  if (isFromKidsSection || (isAnimation && isFamilyOrKids)) {
    return ContentCategory.KIDS_FAMILY;
  }
  if (isAnimation && isJapan) {
    return ContentCategory.ANIME;
  }
  if (genreIds.includes(99)) {
    return ContentCategory.DOCUMENTARY;
  }
  if (
    lang === 'en' ||
    cList.some(
      (c) =>
        c.includes('united states') ||
        c.includes('united kingdom') ||
        c.includes('stany zjednoczone') ||
        c.includes('wielka brytania') ||
        c === 'us' ||
        c === 'gb' ||
        c === 'uk',
    )
  ) {
    return ContentCategory.FOREIGN_MAINSTREAM;
  }
  return ContentCategory.FOREIGN_WORLD;
}

export function isOriginallyPolish({
  originalLanguage = '',
  countries = [],
  sourceVersion = '',
  sourceFilename = '',
  allSources = [],
} = {}) {
  const lang = String(originalLanguage || '').toLowerCase().trim();
  if (lang === 'pl' || lang === 'pol') return true;
  const cList = (countries || []).map((c) => String(c).toLowerCase().trim());
  if (cList.some((c) => ['pl', 'pol', 'poland', 'polska'].includes(c))) return true;

  const checkSource = (ver, file) => {
    const v = String(ver || '').toLowerCase().trim();
    const f = String(file || '').toLowerCase().trim();
    const isExplicitForeign =
      v.includes('dub') ||
      v.includes('lektor') ||
      v.includes('lek') ||
      v.includes('napisy') ||
      v.includes('sub') ||
      f.includes('dubbing') ||
      f.includes('pldub') ||
      f.includes('lektor') ||
      f.includes('pllek') ||
      f.includes('plsub');
    const isExplicitPolish =
      (v === 'pl' || v === 'org' || v.includes('oryg') || f.includes('.pl.') || f.includes('[pl]')) &&
      !isExplicitForeign;
    return isExplicitPolish;
  };

  if (!lang && cList.length === 0) {
    if (checkSource(sourceVersion, sourceFilename)) return true;
    if (
      allSources.length > 0 &&
      allSources.some((s) => checkSource(s.version, s.filename)) &&
      !allSources.some((s) => {
        const v = String(s.version || '').toLowerCase().trim();
        return v.includes('dub') || v.includes('lektor') || v.includes('lek');
      })
    ) {
      return true;
    }
  }
  return false;
}

export function hasPolishSpokenAudio(isPolishProduction, sourceVersion = '', sourceFilename = '') {
  const v = String(sourceVersion || '').toLowerCase().trim();
  const f = String(sourceFilename || '').toLowerCase().trim();

  const isForeignTrack = v.includes('eng') || v.includes('en') || f.includes('.en.') || f.includes('[eng]');
  const isDubbed = (v.includes('dub') || f.includes('dubbing') || f.includes('pldub')) && !isForeignTrack;
  const isLektor = v.includes('lektor') || v.includes('lek') || f.includes('lektor') || f.includes('pllek');
  if (isDubbed || isLektor) return true;

  if (isPolishProduction) {
    return !isForeignTrack;
  }

  const isExplicitPlAudio =
    (v === 'pl' || v === 'pol' || f.includes('.pl.') || f.includes('[pl]')) &&
    !v.includes('napisy') &&
    !v.includes('sub') &&
    !f.includes('plsub') &&
    !f.includes('napisy');
  return isExplicitPlAudio;
}

export function resolveShouldEnableLektor({
  isPolishProduction = false,
  hasPolishSpokenAudio = false,
  contentRule = null,
  isLektorSettingEnabled = false,
} = {}) {
  if (!isLektorSettingEnabled) return false;
  if (isPolishProduction) return false;
  if (hasPolishSpokenAudio) return false;
  if (contentRule?.audioMode === AudioPreferenceMode.ORIGINAL_ONLY) return false;

  return true;
}

export function resolveSubtitlesEnabled({
  isPolishProduction = false,
  hasPolishSpokenAudio = false,
  contentRule = null,
  shouldEnableLektor = false,
  savedSubtitlesEnabled = null,
} = {}) {
  if (savedSubtitlesEnabled !== null && savedSubtitlesEnabled !== undefined) {
    return Boolean(savedSubtitlesEnabled);
  }
  if (shouldEnableLektor) {
    return true;
  }
  const mode = contentRule?.audioMode || AudioPreferenceMode.LEKTOR_PREFERRED;
  switch (mode) {
    case AudioPreferenceMode.ORIGINAL_SUBTITLES:
      return true;
    case AudioPreferenceMode.ORIGINAL_ONLY:
      return false;
    case AudioPreferenceMode.DUBBING_PREFERRED:
    case AudioPreferenceMode.LEKTOR_PREFERRED:
    default:
      return Boolean(contentRule?.subtitlesEnabled);
  }
}

export function sanitizeContentRules(raw) {
  if (!raw || typeof raw !== 'object') return { ...DEFAULT_CONTENT_RULES };
  const out = { ...DEFAULT_CONTENT_RULES };
  for (const [catKey, defaultRule] of Object.entries(DEFAULT_CONTENT_RULES)) {
    if (raw[catKey] && typeof raw[catKey] === 'object') {
      out[catKey] = {
        audioMode: String(raw[catKey].audioMode || defaultRule.audioMode),
        subtitlesEnabled: Boolean(raw[catKey].subtitlesEnabled),
        subtitleLanguage: String(raw[catKey].subtitleLanguage || defaultRule.subtitleLanguage || 'pl'),
      };
    }
  }
  return out;
}
