import test from 'node:test';
import assert from 'node:assert/strict';
import { parseSrtOrVtt } from '../src/player/lektor/srtParser.js';
import { cleanPolishSubtitle } from '../src/player/lektor/polishSubtitleCleaner.js';
import {
  PolishNormalizationMode,
  normalizePolishText,
} from '../src/player/lektor/polishTextNormalizer.js';
import { groupLektorSentences } from '../src/player/lektor/lektorSentenceGrouper.js';
import {
  calculateNeuralRatePercent,
  calculateSpeed,
} from '../src/player/lektor/lektorSpeedCalculator.js';
import { estimateMp3DurationMs } from '../src/player/lektor/mp3DurationEstimator.js';
import { alignSubtitleTracks } from '../src/data/api/subtitleTrackAligner.js';
import { SubtitleWarning, diagnoseSubtitle, isLowQualityPolishSubtitle } from '../src/data/api/subtitleDiagnostics.js';
import { decodeSubtitleBytes } from '../server/subtitleDecoder.js';

test('parseSrtOrVtt parses SRT and WEBVTT cues accurately', () => {
  const srt = `1
00:00:01,000 --> 00:00:03,500
Cześć!

2
00:00:04,000 --> 00:00:06,000
Jak się masz?`;

  const cues = parseSrtOrVtt(srt);
  assert.equal(cues.length, 2);
  assert.equal(cues[0].startMs, 1000);
  assert.equal(cues[0].endMs, 3500);
  assert.equal(cues[0].text, 'Cześć!');

  const vtt = `WEBVTT

00:02.500 --> 00:05.000
Drugi test`;
  const vttCues = parseSrtOrVtt(vtt);
  assert.equal(vttCues.length, 1);
  assert.equal(vttCues[0].startMs, 2500);
  assert.equal(vttCues[0].endMs, 5000);
});

test('cleanPolishSubtitle strips SDH/SFX, credits, formatting, and speaker labels', () => {
  const raw = `<i>[MUZYKA]</i>
JAN: (westchnienie) CO TY TU ROBISZ?!
Tłumaczenie: Ktoś Tam`;
  const cleaned = cleanPolishSubtitle(raw);
  assert.ok(!cleaned.includes('MUZYKA'));
  assert.ok(!cleaned.includes('JAN:'));
  assert.ok(!cleaned.includes('westchnienie'));
  assert.ok(!cleaned.includes('Tłumaczenie'));
  assert.ok(cleaned.toLowerCase().includes('co ty tu robisz'));
});

test('normalizePolishText handles OFFLINE number/abbreviation expansion and NEURAL calm mode', () => {
  const offline = normalizePolishText('np. 15 m.in. 25 zł', {
    mode: PolishNormalizationMode.OFFLINE,
    calmExclamations: false,
  });
  assert.ok(offline.includes('na przykład'));
  assert.ok(offline.includes('piętnaście'));
  assert.ok(offline.includes('złotych'));

  const neuralCalm = normalizePolishText('Dzień dobry! Idziemy szybko zobaczyć co tam słychać!', {
    mode: PolishNormalizationMode.NEURAL,
    calmExclamations: true,
  });
  assert.ok(neuralCalm.startsWith('Dzień dobry.'));
  assert.ok(neuralCalm.endsWith('.'));
});

test('groupLektorSentences merges split continuation cues cleanly', () => {
  const cues = [
    { index: 1, startMs: 1000, endMs: 2500, durationMs: 1500, text: 'Nie jestem pewien' },
    { index: 2, startMs: 2600, endMs: 4200, durationMs: 1600, text: 'że to dobry pomysł.' },
  ];
  const grouped = groupLektorSentences(cues, {
    mode: PolishNormalizationMode.NEURAL,
    calmExclamations: true,
  });
  assert.equal(grouped.length, 1);
  assert.ok(grouped[0].text.includes('Nie jestem pewien, że to dobry pomysł.'));
});

test('calculateSpeed & calculateNeuralRatePercent adapt cleanly to cue window', () => {
  const cue = { index: 1, startMs: 1000, endMs: 2500, durationMs: 1500, text: 'Dłuższe zdanie' };
  const nextCue = { index: 2, startMs: 3000, endMs: 5000, durationMs: 2000, text: 'Dalej' };

  const speed = calculateSpeed({
    audioDurationMs: 2400,
    cue,
    nextCue,
    currentPositionMs: 1000,
    timingOffsetMs: 0,
  });
  assert.ok(speed > 1.0 && speed <= 1.45);

  const neuralRate = calculateNeuralRatePercent({
    textLength: 60,
    cue,
    nextCue,
    baseRatePercent: 10,
  });
  assert.ok(neuralRate >= 10 && neuralRate <= 45);
});

test('estimateMp3DurationMs calculates duration from MPEG2 L3 48kbps header', () => {
  // MPEG2 (version=2 -> bits 10), Layer 3 (bits 01) -> 0xFF 0xF3; bitrate idx 6 = 48kbps -> 0x64
  const buf = new Uint8Array(6000);
  buf[0] = 0xff;
  buf[1] = 0xf3;
  buf[2] = 0x64;
  // 6000 bytes * 8000 / 48000 = 1000ms
  assert.equal(estimateMp3DurationMs(buf), 1000);
});

test('alignSubtitleTracks recovers fixed lag offset across matching tracks', () => {
  const refCues = Array.from({ length: 12 }, (_, i) => ({
    startMs: (i + 1) * 5000,
    endMs: (i + 1) * 5000 + 2000,
  }));
  const targetCues = refCues.map((c) => ({
    startMs: c.startMs - 1500,
    endMs: c.endMs - 1500,
  }));

  const aligned = alignSubtitleTracks(refCues, targetCues);
  assert.ok(aligned !== null);
  assert.equal(aligned.bestOffsetMs, 1500);
  assert.equal(aligned.bestFpsRatio, 1.0);
  assert.ok(aligned.confidence > 0.95);
});

test('decodeSubtitleBytes decodes UTF-8 and windows-1250 Polish bytes without corruption', () => {
  const utf8Text = 'Zażółć gęślą jaźń';
  assert.equal(decodeSubtitleBytes(Buffer.from(utf8Text, 'utf8')), utf8Text);

  // windows-1250 bytes for "Cześć, żółć" (ś=0x9C, ć=0xE6, ż=0xBF, ó=0xF3, ł=0xB3)
  const win1250Bytes = Buffer.from([0x43, 0x7a, 0x65, 0x9c, 0xe6, 0x2c, 0x20, 0xbf, 0xf3, 0xb3, 0xe6]);
  assert.equal(decodeSubtitleBytes(win1250Bytes), 'Cześć, żółć');
});

test('diagnoseSubtitle & isLowQualityPolishSubtitle flag unreadable and unaccented subs', () => {
  assert.equal(diagnoseSubtitle('not valid srt'), SubtitleWarning.UNREADABLE);
  assert.equal(isLowQualityPolishSubtitle('Hello \uFFFD world'), true);

  const noDiacritics = Array.from({ length: 12 }, (_, i) =>
    `${i + 1}\n00:00:${String(i * 2).padStart(2, '0')},000 --> 00:00:${String(i * 2 + 1).padStart(2, '0')},000\nTo jest bardzo dlugi polski tekst bez zadnych polskich znakow diakrytycznych tutaj\n`,
  ).join('\n');
  assert.equal(isLowQualityPolishSubtitle(noDiacritics), true);
});
