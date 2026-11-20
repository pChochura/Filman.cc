export function adjustedCuePosition(positionMs, offsetMs) {
  return positionMs - offsetMs;
}

export function isCueDue(cue, positionMs, offsetMs) {
  const adjusted = adjustedCuePosition(positionMs, offsetMs);
  return adjusted >= cue.startMs && adjusted <= cue.endMs + 300;
}
