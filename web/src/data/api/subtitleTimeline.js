export function createSubtitleTimeline(cues = [], offsetMs = 0, fpsScale = 1.0) {
  return {
    cues,
    offsetMs,
    fpsScale,
  };
}

export function activeCuesAt(timeline, positionMs) {
  if (!timeline || !Array.isArray(timeline.cues) || timeline.cues.length === 0) {
    return [];
  }
  const scale = Math.max(0.001, timeline.fpsScale || 1.0);
  const targetCueTime = Math.trunc((positionMs - (timeline.offsetMs || 0)) / scale);
  return timeline.cues.filter(
    (cue) => targetCueTime >= cue.startMs && targetCueTime <= cue.endMs,
  );
}

const METADATA_OR_CREDITS_REGEX =
  /(?:napisy|tłumaczenie|tlumaczenie|synchro|korekta|redakcja|wersja\s+polska|dystrybucja|transkrypcja|dialogi|opensubtitles|subtitles\s+by|translated\s+by|sync(?:ed)?\s+by|ripped\s+by|encoded\s+by|created\s+by|addic7ed|podnapisi|yify|hatak|grupahatak|animesub|tvseries|dvdrip|web-dl|bluray|x264|x265|1080p|720p|https?:\/\/|www\.|\.com|\.org|\.pl|\.net)/i;

export function isDialogueCue(cue) {
  if (!cue || !cue.text) return false;
  const clean = String(cue.text)
    .replace(/<[^>]+>/g, '')
    .replace(/\{[^}]+\}/g, '')
    .replace(/[[(][^)\]]*[)\]]/g, '')
    .replace(/[♪♫#*]/g, '')
    .trim();
  if (!clean) return false;
  return !METADATA_OR_CREDITS_REGEX.test(cue.text);
}

export function detectDialogueEndMs(timeline, durationMs) {
  if (!timeline || !Array.isArray(timeline.cues) || timeline.cues.length === 0 || durationMs <= 30000) {
    return null;
  }
  const cues = timeline.cues;
  let lastCue = null;
  for (let i = cues.length - 1; i >= 0; i--) {
    if (isDialogueCue(cues[i])) {
      lastCue = cues[i];
      break;
    }
  }
  if (!lastCue) return null;

  const scale = Math.max(0.001, timeline.fpsScale || 1.0);
  const effectiveEndMs = Math.trunc(lastCue.endMs * scale) + (timeline.offsetMs || 0);

  const minWindow = durationMs * 0.70;
  const maxWindow = durationMs * 0.995;
  if (effectiveEndMs < minWindow || effectiveEndMs > maxWindow) {
    return null;
  }

  return Math.min(durationMs - 1000, effectiveEndMs + 2000);
}

export function hasSpokenDialogueAt(timeline, positionMs) {
  const active = activeCuesAt(timeline, positionMs);
  return active.some(isDialogueCue);
}

export function cleanDisplayCueText(rawText) {
  if (!rawText) return '';
  return String(rawText)
    .replace(/\{[^}]+\}/g, '')
    .replace(/<br\s*\/?>/gi, '\n')
    .replace(/<[^>]+>/g, '')
    .replace(/&amp;/gi, '&')
    .replace(/&lt;/gi, '<')
    .replace(/&gt;/gi, '>')
    .replace(/&quot;/gi, '"')
    .replace(/&#39;|&apos;/gi, "'")
    .replace(/&nbsp;/gi, ' ')
    .trim();
}
