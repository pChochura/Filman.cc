import { ensureTerminalPunctuation } from './polishProsodyConditioner.js';
import {
  PolishNormalizationMode,
  normalizePolishWithMetadata,
} from './polishTextNormalizer.js';

const OFFLINE_MAX_GAP_MS = 1500;
const NEURAL_MAX_GAP_MS = 750;
const MAX_DURATION_MS = 20000;
const MAX_CUES = 8;
const MAX_TEXT_LENGTH = 600;
const MICRO_MERGE_MAX_GAP_MS = 250;
const MICRO_MERGE_MAX_DURATION_MS = 4500;
const MICRO_MERGE_MAX_LENGTH = 150;
const MICRO_MERGE_MAX_CUES = 3;
const PAUSE_COMMA_MIN_GAP_MS = 350;
const HESITATION_BREAK_MIN_GAP_MS = 450;

const LEADING_FORMATTING = /^(?:\s|<[^>]*>|\{[^}]*\})*/;
const CLOSING_PUNCT = new Set(['"', "'", '”', '’', ')', ']', '»']);
const OPENING_PUNCT = new Set(['"', "'", '„', '“', '(', '[', '«']);
const JOIN_SKIP_PUNCT = new Set([',', ';', ':', '…', '.', '!', '?', '—']);

const TRAILING_NO_COMMA_WORDS = new Set([
  'w', 'we', 'z', 'ze', 'do', 'na', 'po', 'od', 'o', 'u', 'ku', 'za',
  'pod', 'nad', 'przed', 'przez', 'przy', 'dla', 'bez', 'i', 'a', 'oraz',
  'lub', 'albo', 'czy', 'ani', 'nie', 'się', 'to', 'ten', 'ta', 'te',
  'mój', 'moja', 'moje', 'twój', 'jego', 'jej', 'ich', 'nasz', 'wasz',
]);

const MANDATORY_COMMA_NEXT_WORDS = new Set([
  'że', 'żeby', 'aby', 'iż', 'bo', 'ponieważ', 'gdyż', 'ale', 'lecz',
  'więc', 'zatem', 'jednak', 'chociaż', 'choć', 'skoro', 'dopóki', 'zanim',
  'który', 'która', 'które', 'którego', 'której', 'którym', 'których',
  'którymi', 'którą', 'którzy',
]);

const NO_COMMA_BEFORE_CONJUNCTION = new Set([
  'chyba', 'mimo', 'pomimo', 'dlatego', 'tak', 'tylko', 'jako', 'zamiast',
  'w', 'we', 'z', 'ze', 'o', 'na', 'do', 'od', 'po', 'pod', 'nad',
  'przed', 'przez', 'przy', 'dla', 'bez', 'u', 'ku', 'za',
]);

function trimEndSet(text, charSet) {
  let s = text.trimEnd();
  while (s.length > 0 && charSet.has(s[s.length - 1])) {
    s = s.slice(0, -1).trimEnd();
  }
  return s;
}

function trimStartSet(text, charSet) {
  let s = text.trimStart();
  while (s.length > 0 && charSet.has(s[0])) {
    s = s.slice(1).trimStart();
  }
  return s;
}

function endsWithEllipsis(text) {
  const last = trimEndSet(text, CLOSING_PUNCT);
  return last.endsWith('...') || last.endsWith('…');
}

function startsWithEllipsis(text) {
  const first = trimStartSet(text, OPENING_PUNCT);
  return first.startsWith('...') || first.startsWith('…');
}

function startsWithUppercase(text) {
  const first = trimStartSet(text, OPENING_PUNCT);
  const ch = first[0];
  if (!ch) return false;
  return ch === ch.toLocaleUpperCase('pl-PL') && ch !== ch.toLocaleLowerCase('pl-PL');
}

function endsSentence(text) {
  const last = trimEndSet(text, CLOSING_PUNCT);
  if (last.endsWith('...') || last.endsWith('…')) return false;
  const ch = last[last.length - 1];
  return ch === '.' || ch === '!' || ch === '?';
}

function extractLastWord(text) {
  const m = /(\p{L}+)$/u.exec(text);
  return m ? m[1] : null;
}

function extractFirstWord(text) {
  const m = /^(\p{L}+)/u.exec(text.trimStart());
  return m ? m[1] : null;
}

function prepareCueText(text, isNeural) {
  return isNeural
    ? text.replace(/[ \t]+/g, ' ').trim()
    : text.replace(/\s+/g, ' ').trim();
}

function shouldInsertJoinComma(currentText, nextText, gapMs) {
  const trimmedCurrent = currentText.trimEnd();
  const lastChar = trimmedCurrent[trimmedCurrent.length - 1];
  if (!lastChar || JOIN_SKIP_PUNCT.has(lastChar) || endsSentence(trimmedCurrent)) {
    return false;
  }
  const lastWord = extractLastWord(trimmedCurrent)?.toLocaleLowerCase('pl-PL');
  if (!lastWord) return false;
  if (gapMs >= PAUSE_COMMA_MIN_GAP_MS && !TRAILING_NO_COMMA_WORDS.has(lastWord)) {
    return true;
  }
  const firstNextWord = extractFirstWord(nextText)?.toLocaleLowerCase('pl-PL');
  if (!firstNextWord) return false;
  return (
    MANDATORY_COMMA_NEXT_WORDS.has(firstNextWord) &&
    !NO_COMMA_BEFORE_CONJUNCTION.has(lastWord)
  );
}

function canMicroMerge(start, endMs, count, currentText, cue, nextText) {
  const gapMs = cue.startMs - endMs;
  if (gapMs < 0 || gapMs > MICRO_MERGE_MAX_GAP_MS) return false;
  if (currentText.length > 45 && nextText.length > 35) return false;
  if (currentText.length + 1 + nextText.length > MICRO_MERGE_MAX_LENGTH) return false;
  if (cue.endMs - start.startMs > MICRO_MERGE_MAX_DURATION_MS) return false;
  return count < MICRO_MERGE_MAX_CUES;
}

function shouldJoin({
  start,
  endMs,
  count,
  currentText,
  groupIsMultiSpeaker,
  cue,
  nextText,
  nextIsDialogue,
  nextIsMultiSpeaker,
  hasPairedEllipsis,
  isNeural,
}) {
  if (groupIsMultiSpeaker || nextIsDialogue || nextIsMultiSpeaker) return false;
  if (cue.startMs < start.startMs) return false;

  const gapMs = cue.startMs - endMs;
  const hesitationBreak =
    isNeural &&
    !hasPairedEllipsis &&
    gapMs > HESITATION_BREAK_MIN_GAP_MS &&
    endsWithEllipsis(currentText) &&
    startsWithUppercase(nextText);

  const currentEnded = endsSentence(currentText) || hesitationBreak;
  if (currentEnded) {
    return isNeural && canMicroMerge(start, endMs, count, currentText, cue, nextText);
  }

  const maxGapMs = isNeural ? NEURAL_MAX_GAP_MS : OFFLINE_MAX_GAP_MS;
  if (gapMs > maxGapMs) return false;
  if (cue.endMs - start.startMs > MAX_DURATION_MS) return false;
  if (count >= MAX_CUES) return false;
  return currentText.length + 1 + nextText.length <= MAX_TEXT_LENGTH;
}

export function groupLektorSentences(
  rawCues,
  options = { mode: PolishNormalizationMode.NEURAL, calmExclamations: true },
) {
  const isNeural = options.mode === PolishNormalizationMode.NEURAL;
  const result = [];

  let first = null;
  let endMs = 0;
  let count = 0;
  let isMultiSpeaker = false;
  let accText = '';

  const flush = () => {
    if (!first) return;
    const finalText = isNeural ? ensureTerminalPunctuation(accText) : accText;
    result.push({
      index: result.length + 1,
      startMs: first.startMs,
      endMs,
      durationMs: Math.max(0, endMs - first.startMs),
      text: finalText,
    });
    first = null;
    accText = '';
    count = 0;
    isMultiSpeaker = false;
  };

  for (const cue of rawCues) {
    const normalized = normalizePolishWithMetadata(cue.text, options);
    const normalizedText = prepareCueText(normalized.text, isNeural);
    if (!normalizedText) {
      flush();
      continue;
    }

    const strippedLeading = cue.text.replace(LEADING_FORMATTING, '');
    const nextIsDialogue =
      strippedLeading.startsWith('-') ||
      strippedLeading.startsWith('–') ||
      strippedLeading.startsWith('—') ||
      strippedLeading.startsWith('•');

    const hasPairedEllipsis =
      first !== null && endsWithEllipsis(accText) && startsWithEllipsis(normalizedText);

    const canJoin =
      first !== null &&
      shouldJoin({
        start: first,
        endMs,
        count,
        currentText: accText,
        groupIsMultiSpeaker: isMultiSpeaker,
        cue,
        nextText: normalizedText,
        nextIsDialogue,
        nextIsMultiSpeaker: normalized.isMultiSpeaker,
        hasPairedEllipsis,
        isNeural,
      });

    if (canJoin) {
      const gapMs = cue.startMs - endMs;
      if (hasPairedEllipsis) {
        const cleanedCurrent = accText.replace(/(?:\.{3}|…)\s*$/, '').trimEnd();
        const cleanedNext = normalizedText.replace(/^\s*(?:\.{3}|…)\s*/, '').trimStart();
        if (cleanedCurrent && cleanedNext) {
          const lastChar = cleanedCurrent[cleanedCurrent.length - 1];
          const sep =
            gapMs >= PAUSE_COMMA_MIN_GAP_MS && !JOIN_SKIP_PUNCT.has(lastChar) ? ', ' : ' ';
          accText = cleanedCurrent + sep + cleanedNext;
        } else {
          accText = cleanedCurrent + cleanedNext;
        }
      } else {
        const sep = isNeural && shouldInsertJoinComma(accText, normalizedText, gapMs) ? ', ' : ' ';
        accText = `${accText}${sep}${normalizedText}`;
      }
      endMs = Math.max(endMs, cue.endMs);
      count++;
    } else {
      flush();
      first = cue;
      accText = normalizedText;
      endMs = cue.endMs;
      count = 1;
      isMultiSpeaker = normalized.isMultiSpeaker;
    }
  }

  flush();
  return result;
}
