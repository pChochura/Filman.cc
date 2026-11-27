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
  const coarseStep = 2; // 200ms coarse search

  const targetBitSet = new Uint8Array(totalBins);
  const activeBins = new Int32Array(totalBins);

  for (const ratio of CANDIDATE_RATIOS) {
    targetBitSet.fill(0);
    rasterize(targetCues, targetBitSet, ratio);
    let numActive = 0;
    for (let i = 0; i < totalBins; i++) {
      if (targetBitSet[i]) activeBins[numActive++] = i;
    }
    if (numActive === 0) continue;

    const denom = Math.sqrt(refActiveCount * numActive);
    if (denom <= 0) continue;

    // 1. Coarse search across entire lag range
    let bestCoarseScore = -1;
    let bestCoarseLag = 0;

    for (let lag = -maxLagBins; lag <= maxLagBins; lag += coarseStep) {
      let overlap = 0;
      for (let j = 0; j < numActive; j++) {
        const r = activeBins[j] + lag;
        if (r >= 0 && r < totalBins && refBitSet[r]) overlap++;
      }
      const score = overlap / denom;
      if (score > bestCoarseScore) {
        bestCoarseScore = score;
        bestCoarseLag = lag;
      }
    }

    // 2. Fine search around best coarse lag (+/- coarseStep with step = 1)
    const fineStart = Math.max(-maxLagBins, bestCoarseLag - coarseStep);
    const fineEnd = Math.min(maxLagBins, bestCoarseLag + coarseStep);
    for (let lag = fineStart; lag <= fineEnd; lag++) {
      let overlap = 0;
      for (let j = 0; j < numActive; j++) {
        const r = activeBins[j] + lag;
        if (r >= 0 && r < totalBins && refBitSet[r]) overlap++;
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
