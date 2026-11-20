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
