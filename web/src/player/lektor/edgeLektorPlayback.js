import { LektorEdgeVoiceOptions, LektorTiming, getLektorVoiceById } from '../../data/models/lektorModels.js';
import { adjustedCuePosition } from './lektorCueTiming.js';
import {
  DEFAULT_NATURAL_MS_PER_CHAR,
  calculateNeuralRatePercent,
  calculateResidualEdgeSpeed,
} from './lektorSpeedCalculator.js';
import { estimateMp3DurationMs } from './mp3DurationEstimator.js';

const PREFETCH_CUES = 8;
const MAX_CONCURRENT_PREFETCH = 2;
const IMMINENT_PREFETCH_WINDOW_MS = 6000;
const DUCK_HYSTERESIS_MS = 400;
const RETRY_DELAY_MS = 5000;
const MIN_CHARS_FOR_EMA = 12;
const MIN_MS_PER_CHAR = 30.0;
const MAX_MS_PER_CHAR = 120.0;
const EMA_ALPHA = 0.25;
const LEADING_TRIM_MARGIN_MS = 20;
const TRAILING_TRIM_MARGIN_MS = 50;
const MIN_LEADING_CLIP_MS = 25;
const MIN_CLIP_DURATION_MS = 50;

export async function synthesizeEdgeTtsOverHttp({
  text,
  voiceName,
  rate = LektorEdgeVoiceOptions.DEFAULT_RATE,
  pitch = LektorEdgeVoiceOptions.DEFAULT_PITCH,
  volume = LektorEdgeVoiceOptions.DEFAULT_VOLUME,
  signal,
}) {
  const res = await fetch('/api/tts', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ text, voiceName, rate, pitch, volume }),
    signal,
  });
  if (!res.ok) {
    throw new Error(`TTS HTTP ${res.status}`);
  }
  const startHeader = res.headers.get('X-Speech-Start-Ms');
  const endHeader = res.headers.get('X-Speech-End-Ms');
  const speechStartMs = startHeader !== null ? Number.parseInt(startHeader, 10) : null;
  const speechEndMs = endHeader !== null ? Number.parseInt(endHeader, 10) : null;
  const audioBuffer = await res.arrayBuffer();
  const audioBytes = new Uint8Array(audioBuffer);
  return {
    audioBytes,
    speechStartMs: Number.isFinite(speechStartMs) ? speechStartMs : null,
    speechEndMs: Number.isFinite(speechEndMs) ? speechEndMs : null,
  };
}

export function createEdgeLektorPlayback({
  onSpeechStart,
  onSpeechEnd,
  nativePosition,
  onError,
}) {
  const cache = new Map();
  const pendingControllers = new Map();
  let cues = [];
  let positionMs = 0;
  let timingOffsetMs = LektorTiming.DEFAULT_OFFSET_MS;
  let playing = false;
  let enabled = false;
  let voice = getLektorVoiceById('edge_marek');
  let rate = LektorEdgeVoiceOptions.DEFAULT_RATE;
  let pitch = LektorEdgeVoiceOptions.DEFAULT_PITCH;
  let volume = LektorEdgeVoiceOptions.DEFAULT_VOLUME;
  let lastSpoken = -1;
  let activeCue = null;
  let isSpeechDucked = false;
  let naturalMsPerChar = null;
  let retryAfterMs = 0;
  let lastErrorAt = 0;
  let tickTimer = null;

  let speechAudio = null;
  let currentObjectUrl = null;
  let currentClipEndSec = null;

  function ensureAudioElement() {
    if (typeof Audio === 'undefined') return null;
    if (!speechAudio) {
      speechAudio = new Audio();
      speechAudio.preload = 'auto';
      speechAudio.addEventListener('playing', () => {
        if (activeCue !== null && !isSpeechDucked) {
          isSpeechDucked = true;
          onSpeechStart?.();
        }
      });
      speechAudio.addEventListener('ended', () => {
        if (activeCue !== null) {
          finishSpeech(true);
        }
      });
      speechAudio.addEventListener('timeupdate', () => {
        if (
          activeCue !== null &&
          currentClipEndSec !== null &&
          speechAudio.currentTime >= currentClipEndSec
        ) {
          speechAudio.pause();
          finishSpeech(true);
        }
      });
      speechAudio.addEventListener('error', () => {
        if (activeCue !== null) {
          finishSpeech(false);
          reportError();
        }
      });
    }
    return speechAudio;
  }

  function unlockAudio() {
    const audio = ensureAudioElement();
    if (!audio) return;
    // Play a tiny silent WAV data URI on user gesture so subsequent auto-cue plays work reliably
    if (!audio.src) {
      audio.src =
        'data:audio/wav;base64,UklGRiQAAABXQVZFZm10IBAAAAABAAEARKwAAIhYAQACABAAZGF0YQAAAAA=';
      audio.play().then(() => audio.pause()).catch(() => {});
    }
  }

  function clearAudioUrl() {
    if (currentObjectUrl) {
      URL.revokeObjectURL(currentObjectUrl);
      currentObjectUrl = null;
    }
  }

  function stopAudioElement() {
    currentClipEndSec = null;
    if (speechAudio) {
      try {
        speechAudio.pause();
        speechAudio.removeAttribute('src');
        speechAudio.load();
      } catch {
        // ignore
      }
    }
    clearAudioUrl();
  }

  function reportError() {
    const now = Date.now();
    if (lastErrorAt === 0 || now - lastErrorAt >= 20_000) {
      lastErrorAt = now;
      onError?.();
    }
  }

  function isNextCueImminent() {
    const nextCue = cues.find((c) => c.index > lastSpoken);
    if (!nextCue) return false;
    const curPos = nativePosition?.() ?? positionMs;
    const adjusted = adjustedCuePosition(curPos, timingOffsetMs);
    return nextCue.startMs - adjusted <= DUCK_HYSTERESIS_MS;
  }

  function finishSpeech(keepDuckedIfImminent) {
    if (activeCue === null && !isSpeechDucked) return;
    activeCue = null;
    currentClipEndSec = null;
    if (keepDuckedIfImminent && playing && isNextCueImminent()) {
      return;
    }
    isSpeechDucked = false;
    onSpeechEnd?.();
  }

  function cancelPlayback() {
    for (const ctrl of pendingControllers.values()) {
      ctrl.abort();
    }
    pendingControllers.clear();
    stopAudioElement();
    finishSpeech(false);
  }

  function startLoopIfNeeded() {
    if (tickTimer !== null || !enabled) return;
    tickTimer = setInterval(() => {
      if (!enabled) return;
      if (playing) {
        const livePos = nativePosition?.();
        if (typeof livePos === 'number') positionMs = livePos;
      }
      tick();
    }, 30);
  }

  function stopLoop() {
    if (tickTimer !== null) {
      clearInterval(tickTimer);
      tickTimer = null;
    }
  }

  function updateNaturalRateEstimate(textLength, result, synthesizedRatePercent) {
    if (textLength < MIN_CHARS_FOR_EMA) return;
    const speechDuration =
      result.speechStartMs !== null &&
      result.speechEndMs !== null &&
      result.speechEndMs > result.speechStartMs
        ? result.speechEndMs - result.speechStartMs
        : estimateMp3DurationMs(result.audioBytes);
    if (speechDuration <= 0) return;

    const multiplier = Math.max(0.5, 1.0 + synthesizedRatePercent / 100.0);
    const observed = Math.max(
      MIN_MS_PER_CHAR,
      Math.min(MAX_MS_PER_CHAR, (speechDuration * multiplier) / textLength),
    );
    naturalMsPerChar =
      naturalMsPerChar !== null
        ? naturalMsPerChar * (1.0 - EMA_ALPHA) + observed * EMA_ALPHA
        : observed;
  }

  function launchPrefetchJob(cue) {
    const nextCue = cues.find((c) => c.index === cue.index + 1) || null;
    const baseRatePercent = LektorEdgeVoiceOptions.parseRatePercent(rate);
    const targetRatePercent = calculateNeuralRatePercent({
      textLength: cue.text.length,
      cue,
      nextCue,
      baseRatePercent,
      naturalMsPerChar: naturalMsPerChar ?? DEFAULT_NATURAL_MS_PER_CHAR,
    });
    const targetRate = LektorEdgeVoiceOptions.formatRatePercent(targetRatePercent);
    const generationVoice = voice;
    const controller = new AbortController();
    pendingControllers.set(cue.index, controller);

    synthesizeEdgeTtsOverHttp({
      text: cue.text,
      voiceName: generationVoice.edgeVoiceName,
      rate: targetRate,
      pitch,
      volume,
      signal: controller.signal,
    })
      .then((result) => {
        if (enabled && voice.id === generationVoice.id && cue.index > lastSpoken) {
          cache.set(cue.index, result);
          updateNaturalRateEstimate(cue.text.length, result, targetRatePercent);
        }
      })
      .catch((err) => {
        if (err?.name === 'AbortError') return;
        retryAfterMs = Date.now() + RETRY_DELAY_MS;
        reportError();
      })
      .finally(() => {
        if (pendingControllers.get(cue.index) === controller) {
          pendingControllers.delete(cue.index);
        }
      });
  }

  function playCue(cue, result) {
    const audio = ensureAudioElement();
    if (!audio) return;

    const nextCue = cues.find((c) => c.index === cue.index + 1) || null;
    const speechDurationMs =
      result.speechStartMs !== null &&
      result.speechEndMs !== null &&
      result.speechEndMs > result.speechStartMs
        ? result.speechEndMs - result.speechStartMs + LEADING_TRIM_MARGIN_MS + TRAILING_TRIM_MARGIN_MS
        : estimateMp3DurationMs(result.audioBytes);

    const speed = calculateResidualEdgeSpeed({
      audioDurationMs: speechDurationMs,
      cue,
      nextCue,
      currentPositionMs: positionMs,
      timingOffsetMs,
    });

    stopAudioElement();
    activeCue = cue.index;

    const blob = new Blob([result.audioBytes], { type: 'audio/mpeg' });
    currentObjectUrl = URL.createObjectURL(blob);
    audio.src = currentObjectUrl;
    audio.preservesPitch = true;
    audio.mozPreservesPitch = true;
    audio.webkitPreservesPitch = true;
    audio.playbackRate = speed;

    let clipStartSec = 0;
    currentClipEndSec = null;
    if (
      result.speechStartMs !== null &&
      result.speechEndMs !== null &&
      result.speechEndMs > result.speechStartMs
    ) {
      const clipStartMs = Math.max(0, result.speechStartMs - LEADING_TRIM_MARGIN_MS);
      const clipEndMs = result.speechEndMs + TRAILING_TRIM_MARGIN_MS;
      if (clipStartMs >= MIN_LEADING_CLIP_MS) {
        clipStartSec = clipStartMs / 1000;
      }
      if (clipEndMs > clipStartMs + MIN_CLIP_DURATION_MS) {
        currentClipEndSec = clipEndMs / 1000;
      }
    }

    const startPlayback = () => {
      if (activeCue !== cue.index) return;
      if (clipStartSec > 0) {
        try {
          audio.currentTime = clipStartSec;
        } catch {
          // ignore
        }
      }
      audio.playbackRate = speed;
      audio.play().catch(() => {
        finishSpeech(false);
      });
    };

    if (audio.readyState >= 1) {
      startPlayback();
    } else {
      audio.addEventListener('loadedmetadata', startPlayback, { once: true });
    }
  }

  function tick() {
    const adjusted = adjustedCuePosition(positionMs, timingOffsetMs);
    const window = [];
    for (const c of cues) {
      if (
        c.index > lastSpoken &&
        (c.endMs + 300 >= adjusted || pendingControllers.has(c.index))
      ) {
        window.push(c);
        if (window.length >= PREFETCH_CUES) break;
      }
    }

    const wanted = new Set(window.map((c) => c.index));
    for (const k of cache.keys()) {
      if (!wanted.has(k)) cache.delete(k);
    }
    for (const [cueIdx, ctrl] of pendingControllers.entries()) {
      if (!wanted.has(cueIdx) || cueIdx <= lastSpoken) {
        ctrl.abort();
        pendingControllers.delete(cueIdx);
      }
    }

    const due = window.find((cue) => {
      const nextStart = cues.find((c) => c.index === cue.index + 1)?.startMs;
      const deadline =
        nextStart && nextStart > cue.startMs
          ? Math.min(nextStart, cue.endMs + 800)
          : cue.endMs + 800;
      return adjusted >= cue.startMs && adjusted <= deadline;
    });

    if (playing && due && activeCue === null) {
      const cached = cache.get(due.index);
      if (cached) {
        cache.delete(due.index);
        lastSpoken = due.index;
        playCue(due, cached);
      }
    }

    if (activeCue === null && isSpeechDucked) {
      const nextCue = cues.find((c) => c.index > lastSpoken);
      if (!nextCue || nextCue.startMs - adjusted > DUCK_HYSTERESIS_MS) {
        isSpeechDucked = false;
        onSpeechEnd?.();
      }
    }

    if (Date.now() < retryAfterMs) return;
    while (pendingControllers.size < MAX_CONCURRENT_PREFETCH) {
      const next = window.find(
        (c) => !cache.has(c.index) && !pendingControllers.has(c.index) && c.index > lastSpoken,
      );
      if (!next) break;
      if (pendingControllers.size > 0 && next.startMs - adjusted > IMMINENT_PREFETCH_WINDOW_MS) {
        break;
      }
      launchPrefetchJob(next);
    }
  }

  return {
    unlockAudio,
    setEnabled(val) {
      if (enabled === val) return;
      enabled = val;
      if (val) {
        startLoopIfNeeded();
      } else {
        stopLoop();
        cancelPlayback();
        cache.clear();
      }
    },
    setVoice(v) {
      if (!v?.isOnline || voice.id === v.id) return;
      cancelPlayback();
      cache.clear();
      naturalMsPerChar = null;
      voice = v;
    },
    setVoiceOptions({ rate: r, pitch: p, volume: vol }) {
      if (rate === r && pitch === p && volume === vol) return;
      cancelPlayback();
      cache.clear();
      rate = r;
      pitch = p;
      volume = vol;
    },
    setTimingOffsetMs(val) {
      timingOffsetMs = val;
    },
    setCues(newCues) {
      cancelPlayback();
      cache.clear();
      naturalMsPerChar = null;
      cues = newCues || [];
      lastSpoken = -1;
    },
    update(pos, isPlaying) {
      positionMs = pos;
      if (playing === isPlaying) return;
      playing = isPlaying;
      if (playing) {
        if (activeCue !== null && speechAudio) {
          speechAudio.play().catch(() => {});
        }
        if (enabled) tick();
      } else {
        if (speechAudio) {
          try {
            speechAudio.pause();
          } catch {
            // ignore
          }
        }
        if (activeCue !== null || isSpeechDucked) {
          isSpeechDucked = false;
          onSpeechEnd?.();
        }
      }
    },
    seek(pos) {
      cancelPlayback();
      cache.clear();
      positionMs = pos;
      const adjusted = adjustedCuePosition(pos, timingOffsetMs);
      let prior = -1;
      for (const c of cues) {
        if (c.endMs < adjusted) prior = c.index;
      }
      lastSpoken = prior;
    },
    release() {
      stopLoop();
      cancelPlayback();
      cache.clear();
      speechAudio = null;
    },
  };
}
