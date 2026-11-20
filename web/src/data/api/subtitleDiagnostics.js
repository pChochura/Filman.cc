import { parseSrtOrVtt } from '../../player/lektor/srtParser.js';

export const SubtitleWarning = {
  UNREADABLE: 'UNREADABLE',
  POSSIBLY_INCOMPLETE: 'POSSIBLY_INCOMPLETE',
};

const POLISH_DIACRITICS = new Set([
  'ą', 'ć', 'ę', 'ł', 'ń', 'ó', 'ś', 'ź', 'ż',
  'Ą', 'Ć', 'Ę', 'Ł', 'Ń', 'Ó', 'Ś', 'Ź', 'Ż',
]);

const MIN_LETTERS_FOR_DIACRITIC_CHECK = 200;
const MIN_POLISH_DIACRITIC_RATIO = 0.008;
const LETTER_REGEX = /\p{L}/u;

export function diagnoseSubtitle(content, durationMs = 0) {
  const cues = parseSrtOrVtt(content).sort((a, b) => a.startMs - b.startMs);
  if (cues.length === 0) return SubtitleWarning.UNREADABLE;

  let lastEnd = 0;
  for (const c of cues) {
    if (c.endMs > lastEnd) lastEnd = c.endMs;
  }

  let coveredUntil = cues[0].endMs;
  let hasLongGap = false;
  for (let i = 1; i < cues.length; i++) {
    const cue = cues[i];
    const gap = cue.startMs - coveredUntil;
    if (cue.endMs > coveredUntil) coveredUntil = cue.endMs;
    if (gap >= 10 * 60_000) {
      hasLongGap = true;
      break;
    }
  }

  const endsEarly =
    durationMs >= 10 * 60_000 &&
    lastEnd < durationMs * 0.7 &&
    durationMs - lastEnd >= 10 * 60_000;

  return cues.length < 10 || hasLongGap || endsEarly
    ? SubtitleWarning.POSSIBLY_INCOMPLETE
    : null;
}

export function isLowQualityPolishSubtitle(content) {
  if (!content) return false;
  if (content.includes('\uFFFD')) return true;

  const cues = parseSrtOrVtt(content);
  if (cues.length === 0) return false;

  let letterCount = 0;
  let diacriticCount = 0;
  for (const cue of cues) {
    for (const ch of cue.text) {
      if (LETTER_REGEX.test(ch)) {
        letterCount++;
        if (POLISH_DIACRITICS.has(ch)) diacriticCount++;
      }
    }
  }

  return (
    letterCount >= MIN_LETTERS_FOR_DIACRITIC_CHECK &&
    diacriticCount / letterCount < MIN_POLISH_DIACRITIC_RATIO
  );
}
