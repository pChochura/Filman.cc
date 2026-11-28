import { describe, it } from 'node:test';
import assert from 'node:assert/strict';
import {
  createSubtitleTimeline,
  detectDialogueEndMs,
  hasSpokenDialogueAt,
  isDialogueCue,
} from '../src/data/api/subtitleTimeline.js';

describe('SubtitleTimeline dialogue end and credits detection', () => {
  it('identifies dialogue cues and filters metadata/credit cues', () => {
    assert.equal(isDialogueCue({ text: 'Hello, how are you?' }), true);
    assert.equal(isDialogueCue({ text: 'Napisy: Grupa Hatak' }), false);
    assert.equal(isDialogueCue({ text: 'Tłumaczenie: Jan Kowalski' }), false);
    assert.equal(isDialogueCue({ text: 'https://opensubtitles.org' }), false);
    assert.equal(isDialogueCue({ text: '♪ (music playing) ♪' }), false);
    assert.equal(isDialogueCue({ text: '' }), false);
  });

  it('detectDialogueEndMs skips trailing credit cues and returns accurate credit start', () => {
    const cues = [
      { index: 1, startMs: 10000, endMs: 15000, text: 'First line' },
      { index: 2, startMs: 2400000, endMs: 2500000, text: 'Last line spoken by character.' },
      { index: 3, startMs: 2510000, endMs: 2520000, text: 'Tłumaczenie: Jan Kowalski' },
      { index: 4, startMs: 2525000, endMs: 2530000, text: 'Sync: www.opensubtitles.org' },
    ];
    const durationMs = 2700000; // 45 minutes
    const timeline = createSubtitleTimeline(cues);

    const detected = detectDialogueEndMs(timeline, durationMs);
    // Last dialogue cue ends at 2,500,000. Buffer is +2000ms -> 2,502,000.
    assert.equal(detected, 2502000);
  });

  it('detectDialogueEndMs returns null if cues end outside sane window', () => {
    const cues = [
      { index: 1, startMs: 10000, endMs: 15000, text: 'Opening scene only' },
      { index: 2, startMs: 500000, endMs: 600000, text: 'Subtitle cuts off here' },
    ];
    const durationMs = 2700000;
    const timeline = createSubtitleTimeline(cues);

    assert.equal(detectDialogueEndMs(timeline, durationMs), null);
  });

  it('hasSpokenDialogueAt returns true during speech and false during credits or silence', () => {
    const cues = [
      { index: 1, startMs: 10000, endMs: 20000, text: 'Important speech' },
      { index: 2, startMs: 30000, endMs: 40000, text: 'Napisy: Grupa Hatak' },
    ];
    const timeline = createSubtitleTimeline(cues);

    assert.equal(hasSpokenDialogueAt(timeline, 15000), true);
    assert.equal(hasSpokenDialogueAt(timeline, 25000), false);
    assert.equal(hasSpokenDialogueAt(timeline, 35000), false);
  });
});
