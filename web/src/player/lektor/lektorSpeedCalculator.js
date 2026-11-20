import { adjustedCuePosition } from './lektorCueTiming.js';

export const MIN_SPEED = 0.92;
export const MAX_SPEED = 1.45;
export const NORMAL_SPEED = 1.0;
export const DEFAULT_PAUSE_MARGIN_MS = 180;
export const DEFAULT_NATURAL_MS_PER_CHAR = 62.0;
export const MIN_RESIDUAL_EDGE_SPEED = 1.0;

const NEURAL_MIN_RELAXED_RATE = -10;
const NEURAL_MAX_RATE = 45;
const NEURAL_MAX_SPEEDUP_DELTA = 25;
const NEURAL_MAX_RELAX_DELTA = 6;

function clamp(val, min, max) {
  return Math.max(min, Math.min(max, val));
}

function availableWindow(cue, nextCue, startDelayMs) {
  if (nextCue && nextCue.startMs > cue.startMs) {
    const totalSpanMs = nextCue.startMs - (cue.startMs + startDelayMs);
    return Math.max(cue.durationMs - startDelayMs, totalSpanMs - DEFAULT_PAUSE_MARGIN_MS);
  }
  return Math.max(1000, cue.durationMs - startDelayMs + 500);
}

export function calculateSpeed({
  audioDurationMs,
  cue,
  nextCue,
  currentPositionMs,
  timingOffsetMs,
}) {
  if (audioDurationMs <= 0) return NORMAL_SPEED;
  const adjusted = adjustedCuePosition(currentPositionMs, timingOffsetMs);
  const startDelayMs = Math.max(0, adjusted - cue.startMs);
  const avail = availableWindow(cue, nextCue, startDelayMs);

  if (avail <= 0) return MAX_SPEED;

  if (audioDurationMs > avail) {
    return clamp(audioDurationMs / avail, NORMAL_SPEED, MAX_SPEED);
  }
  if (avail > audioDurationMs * 1.4 && cue.durationMs - startDelayMs > audioDurationMs * 1.2) {
    const relaxedTarget = Math.max(audioDurationMs, cue.durationMs - startDelayMs);
    return clamp(audioDurationMs / relaxedTarget, MIN_SPEED, NORMAL_SPEED);
  }
  return NORMAL_SPEED;
}

export function calculateNeuralRatePercent({
  textLength,
  cue,
  nextCue,
  baseRatePercent,
  naturalMsPerChar = DEFAULT_NATURAL_MS_PER_CHAR,
}) {
  const baseMultiplier = Math.max(0.5, 1.0 + baseRatePercent / 100.0);
  const estimatedNaturalMs = Math.max(1, textLength) * naturalMsPerChar;
  const estimatedBaseMs = estimatedNaturalMs / baseMultiplier;

  const avail =
    nextCue && nextCue.startMs > cue.startMs
      ? Math.max(cue.durationMs, nextCue.startMs - cue.startMs - DEFAULT_PAUSE_MARGIN_MS)
      : Math.max(1000, cue.durationMs + 500);

  if (avail <= 0) {
    return clamp(baseRatePercent + NEURAL_MAX_SPEEDUP_DELTA, -25, NEURAL_MAX_RATE);
  }

  if (estimatedBaseMs > avail) {
    const targetMultiplier = baseMultiplier * (estimatedBaseMs / avail);
    const rawPercent = Math.round((targetMultiplier - 1.0) * 100.0);
    const maxAllowed = Math.min(NEURAL_MAX_RATE, baseRatePercent + NEURAL_MAX_SPEEDUP_DELTA);
    return clamp(rawPercent, baseRatePercent, maxAllowed);
  }

  if (avail > estimatedBaseMs * 1.5 && cue.durationMs > estimatedBaseMs * 1.3) {
    const relaxedPercent = Math.round((estimatedNaturalMs / cue.durationMs - 1.0) * 100.0);
    const minAllowed = Math.max(NEURAL_MIN_RELAXED_RATE, baseRatePercent - NEURAL_MAX_RELAX_DELTA);
    return clamp(relaxedPercent, minAllowed, baseRatePercent);
  }

  return baseRatePercent;
}

export function calculateResidualEdgeSpeed({
  audioDurationMs,
  cue,
  nextCue,
  currentPositionMs,
  timingOffsetMs,
}) {
  if (audioDurationMs <= 0) return NORMAL_SPEED;
  const adjusted = adjustedCuePosition(currentPositionMs, timingOffsetMs);
  const startDelayMs = Math.max(0, adjusted - cue.startMs);
  const avail = availableWindow(cue, nextCue, startDelayMs);

  if (avail <= 0) return MAX_SPEED;
  if (audioDurationMs <= avail) return NORMAL_SPEED;
  return clamp(audioDurationMs / avail, MIN_RESIDUAL_EDGE_SPEED, MAX_SPEED);
}
