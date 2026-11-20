const DIALOGUE_END_PUNCTUATION = new Set(['.', '!', '?', ',', ';', ':', '…', '"', '”', "'"]);
const CLOSING_PUNCTUATION = new Set(['"', "'", '”', '’', ')', ']', '»']);
const TERMINAL_PUNCTUATION = new Set(['.', '!', '?', '…', ':', ';']);

const CURRENCY_ZL = /(\d[\d\s,.]*\d|\d)\s*zł(?!\p{L})/giu;
const CURRENCY_USD_PREFIX = /\$(\d[\d\s,.]*\d|\d)/g;
const CURRENCY_USD_SUFFIX = /(\d[\d\s,.]*\d|\d)\s*\$/g;
const CURRENCY_EUR_PREFIX = /€(\d[\d\s,.]*\d|\d)/g;
const CURRENCY_EUR_SUFFIX = /(\d[\d\s,.]*\d|\d)\s*€/g;
const CURRENCY_GBP_PREFIX = /£(\d[\d\s,.]*\d|\d)/g;
const CURRENCY_GBP_SUFFIX = /(\d[\d\s,.]*\d|\d)\s*£/g;

const EDGE_OK_REGEX = /(?<!\p{L})[Oo]\.?[Kk](?!\p{L})(?:\.(?=\s*(?:\p{Lu}|[,!?…:;]|$)))?/gu;
const EDGE_MR_REGEX = /(?<!\p{L})Mr\.?\s+(?=\p{Lu})/gu;
const EDGE_MRS_MS_REGEX = /(?<!\p{L})M(?:rs|s)\.?\s+(?=\p{Lu})/gu;
const EDGE_VS_REGEX = /(?<!\p{L})vs\.?(?=\s)/giu;
const EDGE_AMPERSAND_REGEX = /(?<=\p{L})\s+&\s+(?=\p{L})/gu;
const EDGE_FILLER_REGEX =
  /(?<!\p{L})(?:h+m{2,}|m{3,}|y{3,}|e{3,}|p+f{2,}|t+s{2,}|s+h{2,}|ć+ś+|c+ś+|u+g+h+)(?!\p{L})(?:[,.…]+)?/giu;

const ROMAN_CENTURY_REGEX = /(?:(\b[Ww])\s+)?\b([IVX]+)\b\s+(wiek|wieku)\b/g;
const ROMAN_WAR_REGEX = /\b([IVX]+)\b\s+(wojna|wojny|wojnie|wojną|wojnę)\b/g;
const ROMAN_AFTER_MASC_NOUN_REGEX = /\b(rozdział|akt|tom|sezon|odcinek|etap|poziom)\s+([IVX]+)\b/gi;
const ROMAN_AFTER_FEM_NOUN_REGEX = /\b(część|klasa|grupa|liga|kategoria)\s+([IVX]+)\b/gi;
const ROMAN_AFTER_MASC_REGAL_REGEX =
  /\b(Jan\s+Paweł|Henryk|Ludwik|Karol|Filip|Aleksander|Mikołaj|Zygmunt|Władysław|Bolesław|Kazimierz|Stanisław|August|Piotr|Fryderyk|Ryszard|Edward|Jerzy|Wilhelm|Napoleon|Benedykt|Król|Papież|Cesarz|Car|Książę)\s+([IVX]+)\b/g;
const ROMAN_AFTER_FEM_REGAL_REGEX = /\b(Elżbieta|Katarzyna|Królowa)\s+([IVX]+)\b/g;

const SPACE_BEFORE_PUNCT_REGEX = /[ \t]+([?!,.;:…])/g;
const TRAILING_INTERRUPTED_DASH = /(?<=\p{L})--+(?=\s|$)/gu;
const TRAILING_SINGLE_DASH = /(?<=\p{L})[ \t]+-(?=[ \t]*$)/gmu;
const MULTI_DOTS_FOUR_PLUS = /\.{4,}/g;
const DOUBLE_DOTS = /(?<!\.)\.{2}(?!\.)/g;
const MULTI_QUESTION = /\?{2,}/g;
const MULTI_EXCLAMATION = /!{2,}/g;
const MIXED_QUESTION_EXCL = /(?:\?!|!\?)+/g;
const SPACE_AFTER_PUNCT = /([,;:?!])(\p{L})/gu;
const HORIZONTAL_SPACES = /[ \t]+/g;

const SUBORDINATE_COMMA_REGEX =
  /(\p{L}+)[ \t]+(że|żeby|ponieważ|gdyż|chociaż|który|która|które|którego|której|którym|których|którą|którzy)(?!\p{L})/giu;
const NO_COMMA_BEFORE_SUBORDINATE = new Set([
  'chyba', 'mimo', 'pomimo', 'dlatego', 'zwłaszcza', 'tak', 'bardziej',
  'to', 'tylko', 'jako', 'zamiast', 'ani', 'lub', 'albo', 'i', 'oraz',
  'w', 'we', 'z', 'ze', 'o', 'na', 'do', 'od', 'po', 'pod', 'nad',
  'przed', 'przez', 'przy', 'dla', 'bez', 'u', 'ku', 'za', 'między', 'wśród',
]);

const EXCLAMATION_SENTENCE_REGEX = /([^.!?\n]+!)/g;
const WORD_REGEX = /\p{L}+/gu;
const CALM_MILD_EXCLAMATIONS = new Set([
  'cześć', 'hej', 'dzień dobry', 'dobry wieczór', 'dobranoc', 'do widzenia',
  'dziękuję', 'dzięki', 'proszę', 'przepraszam', 'dobrze', 'dobra', 'jasne',
  'świetnie', 'super', 'chodź', 'chodźmy', 'spokojnie', 'oczywiście', 'zgoda',
  'okej', 'witaj', 'witam', 'witajcie',
]);

const ROMAN_VALUES = {
  I: 1, II: 2, III: 3, IV: 4, V: 5, VI: 6, VII: 7, VIII: 8, IX: 9, X: 10,
  XI: 11, XII: 12, XIII: 13, XIV: 14, XV: 15, XVI: 16, XVII: 17, XVIII: 18, XIX: 19, XX: 20, XXI: 21,
};

const ORD_MASC_NOM = [
  '', 'pierwszy', 'drugi', 'trzeci', 'czwarty', 'piąty', 'szósty', 'siódmy', 'ósmy',
  'dziewiąty', 'dziesiąty', 'jedenasty', 'dwunasty', 'trzynasty', 'czternasty',
  'piętnasty', 'szesnasty', 'siedemnasty', 'osiemnasty', 'dziewiętnasty',
  'dwudziesty', 'dwudziesty pierwszy',
];
const ORD_MASC_GEN = [
  '', 'pierwszego', 'drugiego', 'trzeciego', 'czwartego', 'piątego', 'szóstego',
  'siódmego', 'ósmego', 'dziewiątego', 'dziesiątego', 'jedenastego', 'dwunastego',
  'trzynastego', 'czternastego', 'piętnastego', 'szesnastego', 'siedemnastego',
  'osiemnastego', 'dziewiętnastego', 'dwudziestego', 'dwudziestego pierwszego',
];
const ORD_MASC_LOC = [
  '', 'pierwszym', 'drugim', 'trzecim', 'czwartym', 'piątym', 'szóstym',
  'siódmym', 'ósmym', 'dziewiątym', 'dziesiątym', 'jedenastym', 'dwunastym',
  'trzynastym', 'czternastym', 'piętnastym', 'szesnastym', 'siedemnastym',
  'osiemnastym', 'dziewiętnastym', 'dwudziestym', 'dwudziestym pierwszym',
];
const ORD_FEM_NOM = [
  '', 'pierwsza', 'druga', 'trzecia', 'czwarta', 'piąta', 'szósta', 'siódma', 'ósma',
  'dziewiąta', 'dziesiąta', 'jedenasta', 'dwunasta', 'trzynasta', 'czternasta',
  'piętnasta', 'szesnasta', 'siedemnasta', 'osiemnasta', 'dziewiętnasta',
  'dwudziesta', 'dwudziesta pierwsza',
];
const ORD_FEM_GEN = [
  '', 'pierwszej', 'drugiej', 'trzeciej', 'czwartej', 'piątej', 'szóstej',
  'siódmej', 'ósmej', 'dziewiątej', 'dziesiątej', 'jedenastej', 'dwunastej',
  'trzynastej', 'czternastej', 'piętnastej', 'szesnastej', 'siedemnastej',
  'osiemnastej', 'dziewiętnastej', 'dwudziestej', 'dwudziestej pierwszej',
];
const ORD_FEM_ACC = [
  '', 'pierwszą', 'drugą', 'trzecią', 'czwartą', 'piątą', 'szóstą', 'siódmą', 'ósmą',
  'dziewiątą', 'dziesiątą', 'jedenastą', 'dwunastą', 'trzynastą', 'czternastą',
  'piętnastą', 'szesnastą', 'siedemnastą', 'osiemnastą', 'dziewiętnastą',
  'dwudziestą', 'dwudziestą pierwszą',
];

function isAtSentenceStart(text, index) {
  let i = index - 1;
  while (i >= 0 && /\s/.test(text[i])) i--;
  return i < 0 || '.!?…\n„"'.includes(text[i]);
}

function isFollowedBySentenceEndOrNewSentence(text, indexAfter) {
  let i = indexAfter;
  while (i < text.length && text[i] === ' ') i++;
  if (i >= text.length || text[i] === '\n') return true;
  const ch = text[i];
  return ch === ch.toLocaleUpperCase('pl-PL') && ch !== ch.toLocaleLowerCase('pl-PL');
}

function capitalizeIfSentenceStart(text, index, word) {
  if (!isAtSentenceStart(text, index) || !word) return word;
  return word[0].toLocaleUpperCase('pl-PL') + word.slice(1);
}

export function formatDialogueLines(cleanedText, preserveTurnBreak) {
  const rawLines = cleanedText.split('\n');
  const nonBlankEntries = [];
  for (const raw of rawLines) {
    const trimmedStart = raw.trimStart();
    const hasDash =
      trimmedStart.startsWith('-') ||
      trimmedStart.startsWith('—') ||
      trimmedStart.startsWith('–') ||
      trimmedStart.startsWith('•');
    const body = raw.replace(/^[-—–•\s]+/, '').trim();
    if (body.length > 0) {
      nonBlankEntries.push({ hasDash, body });
    }
  }
  if (nonBlankEntries.length === 0) {
    return { text: '', isMultiSpeaker: false };
  }

  const isMultiSpeaker =
    nonBlankEntries.length >= 2 && nonBlankEntries.slice(1).some((e) => e.hasDash);

  let out = '';
  for (let i = 0; i < nonBlankEntries.length; i++) {
    const { hasDash, body } = nonBlankEntries[i];
    const nextIsDialogue = i + 1 < nonBlankEntries.length && nonBlankEntries[i + 1].hasDash;
    if (i > 0) {
      out += preserveTurnBreak && hasDash ? '\n' : ' ';
    }
    out += body;
    if (nextIsDialogue && !DIALOGUE_END_PUNCTUATION.has(body[body.length - 1])) {
      out += '.';
    }
  }
  return { text: out, isMultiSpeaker };
}

function polishCurrencyUnit(numStr, sg, plNom, plGen, decGen) {
  if (numStr.includes(',') || numStr.includes('.')) return decGen;
  const digits = numStr.replace(/\s+/g, '');
  const n = Number.parseInt(digits, 10);
  if (!Number.isFinite(n)) return plGen;
  const absN = Math.abs(n);
  const mod100 = absN % 100;
  const mod10 = absN % 10;
  if (absN === 1) return sg;
  if (mod100 >= 12 && mod100 <= 14) return plGen;
  if (mod10 >= 2 && mod10 <= 4) return plNom;
  return plGen;
}

export function fixCurrencyAndSymbols(text) {
  let result = text.replace(CURRENCY_ZL, (_m, g1) => {
    const num = g1.trim();
    return `${num} ${polishCurrencyUnit(num, 'złoty', 'złote', 'złotych', 'złotego')}`;
  });

  const replacePair = (input, prefixRe, suffixRe, sg, plNom, plGen, decGen) => {
    const afterPrefix = input.replace(prefixRe, (_m, g1) => {
      const num = g1.trim();
      return `${num} ${polishCurrencyUnit(num, sg, plNom, plGen, decGen)}`;
    });
    return afterPrefix.replace(suffixRe, (_m, g1) => {
      const num = g1.trim();
      return `${num} ${polishCurrencyUnit(num, sg, plNom, plGen, decGen)}`;
    });
  };

  result = replacePair(result, CURRENCY_USD_PREFIX, CURRENCY_USD_SUFFIX, 'dolar', 'dolary', 'dolarów', 'dolara');
  result = result
    .replace(CURRENCY_EUR_PREFIX, (_m, g1) => `${g1.trim()} euro`)
    .replace(CURRENCY_EUR_SUFFIX, (_m, g1) => `${g1.trim()} euro`);
  result = replacePair(result, CURRENCY_GBP_PREFIX, CURRENCY_GBP_SUFFIX, 'funt', 'funty', 'funtów', 'funta');

  return result
    .replace(/\$/g, ' dolarów ')
    .replace(/€/g, ' euro ')
    .replace(/£/g, ' funtów ');
}

function stripFillerVocalizations(text) {
  if (!EDGE_FILLER_REGEX.test(text)) return text;
  EDGE_FILLER_REGEX.lastIndex = 0;
  const stripped = text.replace(EDGE_FILLER_REGEX, '').trim();
  if (!stripped) return '';
  return stripped
    .split('\n')
    .map((line) => {
      const clean = line
        .replace(/^[ ,;:.…]+/, '')
        .replace(/,\s*$/, '.')
        .trim();
      if (!clean) return '';
      return clean[0].toLocaleUpperCase('pl-PL') + clean.slice(1);
    })
    .join('\n');
}

function expandContextualRomanNumerals(text) {
  let result = text.replace(ROMAN_CENTURY_REGEX, (full, wPrefix, roman, noun, offset) => {
    const ordIdx = ROMAN_VALUES[roman];
    if (!ordIdx) return full;
    let ordWord;
    if (noun.toLowerCase() === 'wiek') ordWord = ORD_MASC_NOM[ordIdx];
    else if (wPrefix) ordWord = ORD_MASC_LOC[ordIdx];
    else ordWord = ORD_MASC_GEN[ordIdx];

    if (wPrefix) return `${wPrefix} ${ordWord} ${noun}`;
    return `${capitalizeIfSentenceStart(text, offset, ordWord)} ${noun}`;
  });

  result = result.replace(ROMAN_WAR_REGEX, (full, roman, noun, offset, currentStr) => {
    const ordIdx = ROMAN_VALUES[roman];
    if (!ordIdx) return full;
    const lowerNoun = noun.toLocaleLowerCase('pl-PL');
    let ordWord;
    if (lowerNoun === 'wojna') ordWord = ORD_FEM_NOM[ordIdx];
    else if (lowerNoun === 'wojnę') ordWord = ORD_FEM_ACC[ordIdx];
    else ordWord = ORD_FEM_GEN[ordIdx];
    return `${capitalizeIfSentenceStart(currentStr, offset, ordWord)} ${noun}`;
  });

  result = result.replace(ROMAN_AFTER_MASC_NOUN_REGEX, (full, noun, roman) => {
    const ordIdx = ROMAN_VALUES[roman];
    return ordIdx ? `${noun} ${ORD_MASC_NOM[ordIdx]}` : full;
  });
  result = result.replace(ROMAN_AFTER_FEM_NOUN_REGEX, (full, noun, roman) => {
    const ordIdx = ROMAN_VALUES[roman];
    return ordIdx ? `${noun} ${ORD_FEM_NOM[ordIdx]}` : full;
  });
  result = result.replace(ROMAN_AFTER_MASC_REGAL_REGEX, (full, title, roman) => {
    const ordIdx = ROMAN_VALUES[roman];
    return ordIdx ? `${title} ${ORD_MASC_NOM[ordIdx]}` : full;
  });
  return result.replace(ROMAN_AFTER_FEM_REGAL_REGEX, (full, title, roman) => {
    const ordIdx = ROMAN_VALUES[roman];
    return ordIdx ? `${title} ${ORD_FEM_NOM[ordIdx]}` : full;
  });
}

export function applyEdgePhonetics(text) {
  let result = text.replace(EDGE_OK_REGEX, (match, offset) => {
    const atStart = isAtSentenceStart(text, offset);
    const endsWithPeriod = match.endsWith('.');
    const word = atStart ? 'Okej' : 'okej';
    if (endsWithPeriod && isFollowedBySentenceEndOrNewSentence(text, offset + match.length)) {
      return `${word}.`;
    }
    return word;
  });

  result = result.replace(EDGE_MR_REGEX, (_m, offset, str) =>
    isAtSentenceStart(str, offset) ? 'Mister ' : 'mister ',
  );
  result = result.replace(EDGE_MRS_MS_REGEX, (_m, offset, str) =>
    isAtSentenceStart(str, offset) ? 'Pani ' : 'pani ',
  );
  result = result.replace(EDGE_VS_REGEX, 'kontra');
  result = result.replace(EDGE_AMPERSAND_REGEX, ' i ');
  result = expandContextualRomanNumerals(result);
  result = stripFillerVocalizations(result);
  return result;
}

export function cleanPunctuation(text) {
  let result = text
    .replace(SPACE_BEFORE_PUNCT_REGEX, '$1')
    .replace(TRAILING_INTERRUPTED_DASH, '…')
    .replace(TRAILING_SINGLE_DASH, '…')
    .replace(MULTI_DOTS_FOUR_PLUS, '...')
    .replace(DOUBLE_DOTS, '...')
    .replace(MIXED_QUESTION_EXCL, '?')
    .replace(MULTI_QUESTION, '?')
    .replace(MULTI_EXCLAMATION, '!')
    .replace(SPACE_AFTER_PUNCT, '$1 $2');

  return result
    .split('\n')
    .map((line) => line.replace(HORIZONTAL_SPACES, ' ').trim())
    .filter(Boolean)
    .join('\n');
}

export function restoreMissingCommas(text) {
  return text.replace(SUBORDINATE_COMMA_REGEX, (full, prevWord, conjunction) => {
    if (NO_COMMA_BEFORE_SUBORDINATE.has(prevWord.toLocaleLowerCase('pl-PL'))) {
      return full;
    }
    return `${prevWord}, ${conjunction}`;
  });
}

export function softenCalmExclamations(text) {
  return text.replace(EXCLAMATION_SENTENCE_REGEX, (segment) => {
    const words = segment.match(WORD_REGEX) || [];
    const normalizedBody = words.join(' ').toLocaleLowerCase('pl-PL');
    if (words.length >= 4 || CALM_MILD_EXCLAMATIONS.has(normalizedBody)) {
      return `${segment.slice(0, -1)}.`;
    }
    return segment;
  });
}

export function ensureTerminalPunctuation(text) {
  return text
    .split('\n')
    .map((rawLine) => {
      const trimmed = rawLine.trim();
      if (!trimmed) return null;
      let endIdx = trimmed.length - 1;
      while (endIdx >= 0 && (CLOSING_PUNCTUATION.has(trimmed[endIdx]) || /\s/.test(trimmed[endIdx]))) {
        endIdx--;
      }
      if (endIdx < 0 || TERMINAL_PUNCTUATION.has(trimmed[endIdx])) {
        return trimmed;
      }
      return `${trimmed}.`;
    })
    .filter((x) => x !== null)
    .join('\n');
}
