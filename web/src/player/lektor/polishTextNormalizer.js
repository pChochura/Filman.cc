import { cleanPolishSubtitle } from './polishSubtitleCleaner.js';
import {
  applyEdgePhonetics,
  cleanPunctuation,
  fixCurrencyAndSymbols,
  formatDialogueLines,
  restoreMissingCommas,
  softenCalmExclamations,
} from './polishProsodyConditioner.js';

export const PolishNormalizationMode = {
  OFFLINE: 'OFFLINE',
  NEURAL: 'NEURAL',
};

const ABBREVIATIONS = [
  [/\bnp\./gi, 'na przykład'],
  [/\bm\.in\./gi, 'między innymi'],
  [/\btzw\./gi, 'tak zwany'],
  [/\btzn\./gi, 'to znaczy'],
  [/\bdr\b/gi, 'doktor'],
  [/\bprof\./gi, 'profesor'],
  [/\bul\./gi, 'ulica'],
  [/(\d+)\s*r\./gi, '$1 roku'],
  [/\btys\./gi, 'tysięcy'],
  [/\bmln\b/gi, 'milionów'],
  [/\bmld\b/gi, 'miliardów'],
  [/\bitd\./gi, 'i tak dalej'],
  [/\bitp\./gi, 'i tym podobne'],
  [/\bgodz\./gi, 'godzina'],
  [/\bart\./gi, 'artykuł'],
  [/\bust\./gi, 'ustęp'],
  [/\bpkt\b/gi, 'punkt'],
  [/\bok\./gi, 'około'],
  [/\bp\.n\.e\./gi, 'przed naszą erą'],
  [/\bn\.e\./gi, 'naszej ery'],
  [/\bwg\b/gi, 'według'],
];

const NUMBER_PATTERN = /\b(\d{1,6})\b/g;

const UNITS = ['', 'jeden', 'dwa', 'trzy', 'cztery', 'pięć', 'sześć', 'siedem', 'osiem', 'dziewięć'];
const TEENS = [
  'dziesięć', 'jedenaście', 'dwanaście', 'trzynaście', 'czternaście', 'piętnaście',
  'szesnaście', 'siedemnaście', 'osiemnaście', 'dziewiętnaście',
];
const TENS = [
  '', 'dziesięć', 'dwadzieścia', 'trzydzieści', 'czterdzieści', 'pięćdziesiąt',
  'sześćdziesiąt', 'siedemdziesiąt', 'osiemdziesiąt', 'dziewięćdziesiąt',
];
const HUNDREDS = [
  '', 'sto', 'dwieście', 'trzysta', 'czterysta', 'pięćset',
  'sześćset', 'siedemset', 'osiemset', 'dziewięćset',
];

function threeDigitsToWords(n) {
  const parts = [];
  const h = Math.floor(n / 100);
  const t = Math.floor((n % 100) / 10);
  const u = n % 10;

  if (h > 0) parts.push(HUNDREDS[h]);
  if (t === 1) {
    parts.push(TEENS[u]);
  } else {
    if (t > 1) parts.push(TENS[t]);
    if (u > 0) parts.push(UNITS[u]);
  }
  return parts.join(' ').trim();
}

export function numberToPolishWords(n) {
  if (n === 0) return 'zero';
  if (n < 0) return `minus ${numberToPolishWords(-n)}`;

  const parts = [];
  const thousands = Math.floor(n / 1000);
  const remainder = n % 1000;

  if (thousands > 0) {
    if (thousands === 1) {
      parts.push('tysiąc');
    } else {
      parts.push(threeDigitsToWords(thousands));
      const tRem100 = thousands % 100;
      const tRem10 = thousands % 10;
      if (tRem100 >= 11 && tRem100 <= 19) parts.push('tysięcy');
      else if (tRem10 >= 2 && tRem10 <= 4) parts.push('tysiące');
      else parts.push('tysięcy');
    }
  }
  if (remainder > 0) {
    parts.push(threeDigitsToWords(remainder));
  }
  return parts.join(' ').trim();
}

function normalizeOffline(text) {
  let result = text.replace(/%/g, ' procent');
  for (const [pattern, replacement] of ABBREVIATIONS) {
    result = result.replace(pattern, replacement);
  }
  return result.replace(NUMBER_PATTERN, (full, numStr) => {
    const num = Number.parseInt(numStr, 10);
    return Number.isFinite(num) ? numberToPolishWords(num) : full;
  });
}

function normalizeNeural(text, calmExclamations) {
  let result = applyEdgePhonetics(text);
  result = restoreMissingCommas(result);
  if (calmExclamations) {
    result = softenCalmExclamations(result);
  }
  return result;
}

export function normalizePolishWithMetadata(
  text,
  options = { mode: PolishNormalizationMode.OFFLINE, calmExclamations: false },
) {
  const cleaned = cleanPolishSubtitle(text);
  if (!cleaned || !cleaned.trim()) {
    return { text: '', isMultiSpeaker: false };
  }
  const isNeural = options.mode === PolishNormalizationMode.NEURAL;
  const dialogue = formatDialogueLines(cleaned, isNeural);
  let result = fixCurrencyAndSymbols(dialogue.text);

  result = isNeural
    ? normalizeNeural(result, Boolean(options.calmExclamations))
    : normalizeOffline(result);

  result = cleanPunctuation(result);
  return { text: result, isMultiSpeaker: dialogue.isMultiSpeaker };
}

export function normalizePolishText(text, options) {
  return normalizePolishWithMetadata(text, options).text;
}
