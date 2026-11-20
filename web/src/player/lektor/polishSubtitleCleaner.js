const HTML_TAG_REGEX = /<[^>]+>/g;
const ASS_TAG_REGEX = /\{[^}]+\}/g;
const INVISIBLE_UNICODE_REGEX = /[\u200B-\u200F\u00AD\uFEFF]/g;
const BRACKET_SFX_REGEX = /\[[^\]]*\]/g;
const ASTERISK_SFX_REGEX = /\*[^*]+\*/g;
const PARENTHESIS_SFX_REGEX = new RegExp(
  '\\((?:[\\p{L}\\s,\'-]*?(?:muzyk[\\p{L}]*|westchn[\\p{L}]*|wzdych[\\p{L}]*|' +
    'śmiech[\\p{L}]*|śmiej[\\p{L}]*|chichot[\\p{L}]*|dźwięk[\\p{L}]*|' +
    'braw[\\p{L}]*|oklask[\\p{L}]*|szept[\\p{L}]*|krzyk[\\p{L}]*|krzycz[\\p{L}]*|' +
    'wrzask[\\p{L}]*|telefon|dzwon[\\p{L}]*|płacz[\\p{L}]*|szloch[\\p{L}]*|' +
    'kaszl[\\p{L}]*|kich[\\p{L}]*|strzał[\\p{L}]*|wystrzał[\\p{L}]*|pukan[\\p{L}]*|' +
    'syren[\\p{L}]*|cisz[\\p{L}]*|jęk[\\p{L}]*|stęk[\\p{L}]*|chrząk[\\p{L}]*|' +
    'oddech[\\p{L}]*|dyszy|sapie|gwizd[\\p{L}]*|wybuch[\\p{L}]*|eksplozj[\\p{L}]*|' +
    'krok[\\p{L}]*|trzask[\\p{L}]*|drzwi[\\p{L}]*|silnik[\\p{L}]*|klakson[\\p{L}]*|' +
    'alarm[\\p{L}]*|pisk[\\p{L}]*|szum[\\p{L}]*|grzmot[\\p{L}]*|deszcz[\\p{L}]*|' +
    'wiatr[\\p{L}]*|pies|szczek[\\p{L}]*|miau|ptak[\\p{L}]*|radio|telewiz[\\p{L}]*|' +
    'głośnik[\\p{L}]*|z\\s+offu|w\\s+tle|po\\s+angielsku|po\\s+francusku|' +
    'po\\s+niemiecku|po\\s+hiszpańsku|po\\s+rosyjsku|po\\s+włosku|' +
    'po\\s+japońsku|niezrozumiał[\\p{L}]*)[\\p{L}\\s,\'-]*?)\\)',
  'giu',
);
const STANDALONE_PAREN_LINE = /^([-—–]\s*)?\([^)]+\)$/;
const LEADING_COLON_AFTER_STRIP = /^([-—–]\s*)?:\s+/;
const SPEAKER_PREFIX_REGEX = /^([-—–]\s*)?([A-ZĄĆĘŁŃÓŚŹŻ][A-ZĄĆĘŁŃÓŚŹŻ0-9\s]{1,22}):\s+/;

const CREDIT_LINE_REGEX =
  /^\s*(?:tłumaczenie|korekta|napisy|synchro|synchronizacja|opracowanie|tłum\.|kor\.|subtitles\s+by|synced\s+and\s+corrected)\s*[:-].*/i;
const WATERMARK_REGEX =
  /.*(?:opensubtitles\.(?:org|com)|napiprojekt(?:\.pl)?|grupahatak(?:\.pl)?|napisy24\.pl|subscene\.com|addic7ed\.com|podnapisi\.net|www\.[a-z0-9-]+\.[a-z]{2,}).*/i;

const NON_SPEAKER_HEADERS = new Set([
  'UWAGA', 'ROZDZIAŁ', 'CZĘŚĆ', 'AKT', 'NOTATKA', 'CEL', 'BŁĄD',
  'STATUS', 'GODZINA', 'DATA', 'MIEJSCE', 'TYTUŁ',
]);

export const ACRONYM_WHITELIST = new Set([
  'FBI', 'CIA', 'NSA', 'DEA', 'SWAT', 'NYPD', 'LAPD', 'USA', 'UK', 'UE',
  'ONZ', 'NATO', 'NASA', 'DNA', 'RNA', 'HIV', 'AIDS', 'VIP', 'CEO', 'CFO',
  'CTO', 'HR', 'PR', 'IT', 'AI', 'TV', 'DJ', 'MC', 'CD', 'DVD', 'USB',
  'SIM', 'PIN', 'GPS', 'SMS', 'MMS', 'SOS', 'PDF', 'URL', 'HTTP', 'WWW',
  'ATM', 'SUV', 'BMW', 'VW', 'PKP', 'PKS', 'ZUS', 'NFZ', 'US', 'CBŚ',
  'CBŚP', 'ABW', 'CBA', 'BOR', 'SOP', 'GROM', 'PSP', 'OSP', 'WOPR', 'GOPR',
  'TOPR', 'MON', 'MSZ', 'TVP', 'TVN', 'RMF', 'PRL', 'RP', 'PESEL', 'REGON',
  'PIT', 'VAT', 'KRS', 'OK', 'BBC', 'CNN', 'HBO', 'KGB', 'FSB', 'SBU',
]);

const NON_ACRONYM_CONSONANTS = new Set([
  'DR', 'NR', 'ŚW', 'ŚP', 'WG', 'NP', 'MLN', 'MLD', 'TYS', 'PKT', 'ART', 'UST',
]);

const ROMAN_NUMERALS_MULTI = new Set([
  'II', 'III', 'IV', 'VI', 'VII', 'VIII', 'IX', 'XI', 'XII',
  'XIII', 'XIV', 'XV', 'XVI', 'XVII', 'XVIII', 'XIX', 'XX', 'XXI',
]);

const POLISH_VOWELS = new Set(['A', 'Ą', 'E', 'Ę', 'I', 'O', 'Ó', 'U', 'Y']);
const WORD_TOKEN_REGEX = /\p{L}+/gu;
const LETTER_REGEX = /\p{L}/u;

const NAMED_ENTITIES = {
  amp: '&',
  lt: '<',
  gt: '>',
  quot: '"',
  apos: "'",
  nbsp: ' ',
  ndash: '–',
  mdash: '—',
  hellip: '…',
  laquo: '«',
  raquo: '»',
};

function unescapeHtmlEntities(text) {
  return text.replace(/&(#x[0-9a-f]+|#\d+|[a-z]+);/gi, (full, body) => {
    const lower = body.toLowerCase();
    if (lower.startsWith('#x')) {
      const cp = Number.parseInt(lower.slice(2), 16);
      return Number.isFinite(cp) && cp > 0 ? String.fromCodePoint(cp) : full;
    }
    if (lower.startsWith('#')) {
      const cp = Number.parseInt(lower.slice(1), 10);
      return Number.isFinite(cp) && cp > 0 ? String.fromCodePoint(cp) : full;
    }
    return NAMED_ENTITIES[lower] ?? full;
  });
}

function isLetter(ch) {
  return LETTER_REGEX.test(ch);
}

function isUpperLetter(ch) {
  return isLetter(ch) && ch === ch.toLocaleUpperCase('pl-PL') && ch !== ch.toLocaleLowerCase('pl-PL');
}

function isPreservedAcronym(token) {
  if (ACRONYM_WHITELIST.has(token) || ROMAN_NUMERALS_MULTI.has(token)) return true;
  if (NON_ACRONYM_CONSONANTS.has(token)) return false;
  if (token.length >= 2 && token.length <= 4) {
    for (const ch of token) {
      if (POLISH_VOWELS.has(ch)) return false;
    }
    return true;
  }
  return false;
}

function isAfterSentenceBoundary(line, tokenStart) {
  let i = tokenStart - 1;
  const skipChars = new Set(['"', '”', '„', '(', '[', '—', '–', '-']);
  while (i >= 0 && (/\s/.test(line[i]) || skipChars.has(line[i]))) {
    if ((line[i] === '-' || line[i] === '—' || line[i] === '–') && i === 0) return true;
    i--;
  }
  return i < 0 || line[i] === '.' || line[i] === '!' || line[i] === '?';
}

function transformToken(token, isShoutingLine, atSentenceStart) {
  const chars = [...token];
  const isAllUpper =
    chars.length >= 2 && chars.every((ch) => !isLetter(ch) || isUpperLetter(ch));
  const isSingleUpperInShouting = isShoutingLine && chars.length === 1 && isUpperLetter(chars[0]);
  if (!isAllUpper && !isSingleUpperInShouting) return token;

  if (isPreservedAcronym(token)) return token;

  const lowered = token.toLocaleLowerCase('pl-PL');
  if (atSentenceStart) {
    const first = lowered[0];
    return first ? first.toLocaleUpperCase('pl-PL') + lowered.slice(1) : lowered;
  }
  return lowered;
}

function decapitalizeAllCaps(line) {
  let totalLetters = 0;
  let upperLetters = 0;
  for (const ch of line) {
    if (isLetter(ch)) {
      totalLetters++;
      if (isUpperLetter(ch)) upperLetters++;
    }
  }
  if (upperLetters === 0) return line;

  const isShoutingLine = totalLetters >= 4 && upperLetters * 100 >= totalLetters * 85;
  let seenSentenceStart = false;
  return line.replace(WORD_TOKEN_REGEX, (token, offset) => {
    const atSentenceStart = !seenSentenceStart || isAfterSentenceBoundary(line, offset);
    seenSentenceStart = true;
    return transformToken(token, isShoutingLine, atSentenceStart);
  });
}

function stripSpeakerPrefix(line) {
  const match = SPEAKER_PREFIX_REGEX.exec(line);
  if (!match) return line;
  const label = match[2].trim();
  const labelBase = label.split(' ')[0];
  if (
    NON_SPEAKER_HEADERS.has(label) ||
    NON_SPEAKER_HEADERS.has(labelBase) ||
    ACRONYM_WHITELIST.has(label)
  ) {
    return line;
  }
  const dashPrefix = match[1] || '';
  return dashPrefix + line.slice(match[0].length);
}

function cleanLine(rawLine) {
  const trimmedRaw = rawLine.trim();
  if (!trimmedRaw) return null;
  if (trimmedRaw.includes('♪') || trimmedRaw.includes('♫')) return null;
  if (CREDIT_LINE_REGEX.test(trimmedRaw) || WATERMARK_REGEX.test(trimmedRaw)) return null;

  let line = trimmedRaw
    .replace(BRACKET_SFX_REGEX, '')
    .replace(ASTERISK_SFX_REGEX, '')
    .replace(PARENTHESIS_SFX_REGEX, '')
    .trim();
  if (!line || STANDALONE_PAREN_LINE.test(line)) return null;

  line = line.replace(LEADING_COLON_AFTER_STRIP, (_m, dash) => dash || '');
  line = stripSpeakerPrefix(line).trim();
  if (!line) return null;

  return decapitalizeAllCaps(line);
}

export function cleanPolishSubtitle(rawText) {
  let result = String(rawText || '')
    .replace(HTML_TAG_REGEX, '')
    .replace(ASS_TAG_REGEX, '');
  result = unescapeHtmlEntities(result);
  result = result.replace(/\u00A0/g, ' ').replace(INVISIBLE_UNICODE_REGEX, '');
  const cleanedLines = result.split('\n').map(cleanLine).filter((x) => x !== null);
  return cleanedLines.join('\n');
}
