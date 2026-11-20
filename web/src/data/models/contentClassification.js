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
  const cList = (countries || []).map((c) => String(c).toLowerCase());
  const isJapan = lang === 'ja' || cList.some((c) => c === 'japan' || c === 'japonia');
  const isPoland = lang === 'pl' || cList.some((c) => c === 'poland' || c === 'polska');
  const isAnimation = genreIds.includes(16);
  const isFamilyOrKids = genreIds.includes(10751) || genreIds.includes(10762);

  if (isFromKidsSection || (isAnimation && isFamilyOrKids)) {
    return ContentCategory.KIDS_FAMILY;
  }
  if (isAnimation && isJapan) {
    return ContentCategory.ANIME;
  }
  if (isPoland) {
    return ContentCategory.NATIVE_DOMESTIC;
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
        c.includes('wielka brytania'),
    )
  ) {
    return ContentCategory.FOREIGN_MAINSTREAM;
  }
  return ContentCategory.FOREIGN_WORLD;
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
