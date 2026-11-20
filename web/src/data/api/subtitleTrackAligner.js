const BIN_MS = 100;
const CANDIDATE_RATIOS = [
  1.0,
  25.0 / 23.976,
  23.976 / 25.0,
  24.0 / 23.976,
  23.976 / 24.0,
];

function rasterize(cues, bitSet, ratio) {
  const totalBins = bitSet.length;
  for (const cue of cues) {
    const startBin = Math.max(0, Math.min(totalBins - 1, Math.trunc((cue.startMs * ratio) / BIN_MS)));
    const endBin = Math.max(0, Math.min(totalBins - 1, Math.trunc((cue.endMs * ratio) / BIN_MS)));
    for (let i = startBin; i <= endBin; i++) {
      bitSet[i] = 1;
    }
  }
}

export function alignSubtitleTracks(referenceCues, targetCues, maxLagMs = 60_000) {
  if (!referenceCues || !targetCues || referenceCues.length < 5 || targetCues.length < 5) {
    return null;
  }

  let refMaxTime = 0;
  for (const c of referenceCues) {
    if (c.endMs > refMaxTime) refMaxTime = c.endMs;
  }
  let targetMaxTime = 0;
  for (const c of targetCues) {
    if (c.endMs > targetMaxTime) targetMaxTime = c.endMs;
  }
  if (refMaxTime <= 0 || targetMaxTime <= 0) return null;

  const maxDurationMs = Math.max(refMaxTime, targetMaxTime) + maxLagMs;
  const totalBins = Math.trunc(maxDurationMs / BIN_MS) + 10;

  const refBitSet = new Uint8Array(totalBins);
  rasterize(referenceCues, refBitSet, 1.0);
  let refActiveCount = 0;
  for (let i = 0; i < totalBins; i++) {
    if (refBitSet[i]) refActiveCount++;
  }
  if (refActiveCount === 0) return null;

  let bestOffsetMs = 0;
  let bestRatio = 1.0;
  let maxScore = -1.0;
  const maxLagBins = Math.trunc(maxLagMs / BIN_MS);

  const targetBitSet = new Uint8Array(totalBins);
  for (const ratio of CANDIDATE_RATIOS) {
    targetBitSet.fill(0);
    rasterize(targetCues, targetBitSet, ratio);
    let targetActiveCount = 0;
    for (let i = 0; i < totalBins; i++) {
      if (targetBitSet[i]) targetActiveCount++;
    }
    if (targetActiveCount === 0) continue;

    const denom = Math.sqrt(refActiveCount * targetActiveCount);
    for (let lag = -maxLagBins; lag <= maxLagBins; lag++) {
      let overlap = 0;
      const startTarget = Math.max(0, -lag);
      const endTarget = Math.min(totalBins, totalBins - lag);

      for (let targetIdx = startTarget; targetIdx < endTarget; targetIdx++) {
        if (targetBitSet[targetIdx] && refBitSet[targetIdx + lag]) {
          overlap++;
        }
      }

      const score = overlap / denom;
      if (score > maxScore) {
        maxScore = score;
        bestOffsetMs = lag * BIN_MS;
        bestRatio = ratio;
      }
    }
  }

  return {
    bestOffsetMs,
    bestFpsRatio: bestRatio,
    confidence: maxScore,
  };
}
