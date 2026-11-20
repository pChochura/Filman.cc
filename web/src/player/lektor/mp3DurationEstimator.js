const BITRATES_MPEG2_L3 = [0, 8, 16, 24, 32, 40, 48, 56, 64, 80, 96, 112, 128, 144, 160, 0];
const BITRATES_MPEG1_L3 = [0, 32, 40, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320, 0];
const SAMPLE_RATES_MPEG1 = [44100, 48000, 32000];
const SAMPLE_RATES_MPEG2 = [22050, 24000, 16000];
const SAMPLE_RATES_MPEG25 = [11025, 12000, 8000];

export function estimateMp3DurationMs(audioBytes) {
  const bytes =
    audioBytes instanceof Uint8Array ? audioBytes : new Uint8Array(audioBytes || []);
  if (bytes.length < 4) return 0;

  let offset = 0;
  const maxOffset = Math.min(bytes.length - 4, 4096);
  while (offset <= maxOffset) {
    const b0 = bytes[offset] & 0xff;
    const b1 = bytes[offset + 1] & 0xff;
    if (b0 === 0xff && (b1 & 0xe0) === 0xe0) {
      const mpegVersion = (b1 >> 3) & 0x03;
      const layer = (b1 >> 1) & 0x03;
      if (layer === 1 && mpegVersion !== 1) {
        const b2 = bytes[offset + 2] & 0xff;
        const bitrateIdx = (b2 >> 4) & 0x0f;
        const sampleRateIdx = (b2 >> 2) & 0x03;
        const bitrates = mpegVersion === 3 ? BITRATES_MPEG1_L3 : BITRATES_MPEG2_L3;
        const sampleRates =
          mpegVersion === 3
            ? SAMPLE_RATES_MPEG1
            : mpegVersion === 2
              ? SAMPLE_RATES_MPEG2
              : SAMPLE_RATES_MPEG25;
        if (bitrateIdx >= 1 && bitrateIdx <= 14 && sampleRateIdx >= 0 && sampleRateIdx <= 2) {
          const bitrateKbps = bitrates[bitrateIdx];
          const sampleRate = sampleRates[sampleRateIdx];
          if (bitrateKbps > 0 && sampleRate > 0) {
            const bitrateBps = bitrateKbps * 1000;
            return Math.floor((bytes.length * 8000) / bitrateBps);
          }
        }
      }
    }
    offset++;
  }

  return Math.floor((bytes.length * 1000) / 6000);
}
