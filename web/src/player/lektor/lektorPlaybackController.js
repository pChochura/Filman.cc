import {
  DEFAULT_LEKTOR_VOICE_ID,
  LektorDucking,
  LektorEdgeVoiceOptions,
  LektorTiming,
  getLektorVoiceById,
  systemVoiceNameFromId,
} from '../../data/models/lektorModels.js';
import { parseSrtOrVtt } from './srtParser.js';
import { PolishNormalizationMode } from './polishTextNormalizer.js';
import { groupLektorSentences } from './lektorSentenceGrouper.js';
import { adjustedCuePosition, isCueDue } from './lektorCueTiming.js';
import { calculateSpeed } from './lektorSpeedCalculator.js';
import { createLektorVolumeDucker } from './lektorVolumeDucker.js';
import { createEdgeLektorPlayback } from './edgeLektorPlayback.js';
import { createSystemLektorSpeech } from './systemLektorSpeech.js';

export function createLektorPlaybackController({
  onDuckingVolumeChange,
  nativePosition = () => null,
  nativeIsPlaying = () => null,
  onError = () => {},
}) {
  const volumeDucker = createLektorVolumeDucker(onDuckingVolumeChange);
  const edgePlayback = createEdgeLektorPlayback({
    onSpeechStart: () => volumeDucker.duck(),
    onSpeechEnd: () => volumeDucker.restore(),
    nativePosition,
    onError,
  });

  let enabled = false;
  let selectedVoice = getLektorVoiceById(DEFAULT_LEKTOR_VOICE_ID);
  let systemVoiceName = null;
  let timingOffsetMs = LektorTiming.DEFAULT_OFFSET_MS;
  let edgeCalmMode = LektorEdgeVoiceOptions.DEFAULT_CALM_MODE;
  let rawParsedCues = [];
  let cues = [];
  let currentPositionMs = 0;
  let isPlaying = false;
  let lastSpokenCueIndex = -1;
  let interruptedSystemCueIndex = null;
  let systemSpeech = null;
  let systemLoopTimer = null;

  function currentNormalizationOptions() {
    const isOnline = selectedVoice.isOnline;
    return {
      mode: isOnline ? PolishNormalizationMode.NEURAL : PolishNormalizationMode.OFFLINE,
      calmExclamations: isOnline && edgeCalmMode,
    };
  }

  function ensureSystemLoop() {
    if (systemLoopTimer !== null) return;
    systemLoopTimer = setInterval(() => {
      if (!enabled || selectedVoice.isOnline || !isPlaying || cues.length === 0) return;
      checkAndPlaySystemCue();
    }, 30);
  }

  function stopSystemLoop() {
    if (systemLoopTimer !== null) {
      clearInterval(systemLoopTimer);
      systemLoopTimer = null;
    }
  }

  function ensureSystemEngine() {
    if (!selectedVoice.isSystem) {
      systemSpeech?.release();
      systemSpeech = null;
      stopSystemLoop();
      return;
    }
    if (!systemSpeech) {
      systemSpeech = createSystemLektorSpeech({
        onSpeechStart: () => volumeDucker.duck(),
        onSpeechEnd: () => {
          interruptedSystemCueIndex = null;
          volumeDucker.restore();
        },
        systemVoiceName,
      });
    } else {
      systemSpeech.setVoiceName(systemVoiceName);
    }
    ensureSystemLoop();
  }

  function checkAndPlaySystemCue() {
    if (!systemSpeech || systemSpeech.isSpeaking()) return;
    const matching =
      (interruptedSystemCueIndex !== null &&
        cues.find((c) => c.index === interruptedSystemCueIndex)) ||
      cues.find(
        (c) =>
          isCueDue(c, currentPositionMs, timingOffsetMs) && c.index > lastSpokenCueIndex,
      );
    if (!matching) return;

    const nextCue = cues.find((c) => c.index === matching.index + 1) || null;
    const estimatedDurationMs = matching.text.length * 70;
    const speed = calculateSpeed({
      audioDurationMs: estimatedDurationMs,
      cue: matching,
      nextCue,
      currentPositionMs,
      timingOffsetMs,
    });

    interruptedSystemCueIndex = matching.index;
    if (systemSpeech.speak(matching.text, speed)) {
      lastSpokenCueIndex = matching.index;
    } else {
      interruptedSystemCueIndex = null;
    }
  }

  function regroupSubtitles(preservePosition = true) {
    if (rawParsedCues.length === 0) return;
    cues = groupLektorSentences(rawParsedCues, currentNormalizationOptions());
    if (preservePosition && currentPositionMs > 0) {
      const adj = adjustedCuePosition(currentPositionMs, timingOffsetMs);
      let prior = -1;
      for (const c of cues) {
        if (c.endMs < adj) prior = c.index;
      }
      lastSpokenCueIndex = prior;
    } else {
      lastSpokenCueIndex = -1;
    }
    edgePlayback.setCues(cues);
    if (preservePosition && currentPositionMs > 0) {
      edgePlayback.seek(currentPositionMs);
    }
  }

  return {
    unlockAudio() {
      edgePlayback.unlockAudio();
    },
    setEnabled(val) {
      if (enabled === val) return;
      enabled = val;
      edgePlayback.setEnabled(val && selectedVoice.isOnline);
      if (val) {
        ensureSystemEngine();
      } else {
        interruptedSystemCueIndex = null;
        systemSpeech?.release();
        systemSpeech = null;
        stopSystemLoop();
        volumeDucker.restore();
      }
    },
    setVoiceId(voiceId) {
      const nextVoice = getLektorVoiceById(voiceId);
      const nextSysName = systemVoiceNameFromId(voiceId);
      if (selectedVoice.id === nextVoice.id && systemVoiceName === nextSysName) return;

      const wasOnline = selectedVoice.isOnline;
      selectedVoice = nextVoice;
      systemVoiceName = nextSysName;

      if (nextVoice.isOnline) {
        edgePlayback.setVoice(nextVoice);
      }
      edgePlayback.setEnabled(enabled && nextVoice.isOnline);

      if (wasOnline !== nextVoice.isOnline && rawParsedCues.length > 0) {
        regroupSubtitles(true);
      }
      if (enabled) {
        interruptedSystemCueIndex = null;
        systemSpeech?.release();
        systemSpeech = null;
        volumeDucker.restore();
        ensureSystemEngine();
      }
    },
    setDuckingVolume(vol) {
      volumeDucker.setDuckingVolume(vol);
    },
    setTimingOffsetMs(offset) {
      timingOffsetMs = LektorTiming.fromStored(offset);
      edgePlayback.setTimingOffsetMs(timingOffsetMs);
    },
    setEdgeVoiceOptions({ rate, pitch, volume, calmMode }) {
      edgePlayback.setVoiceOptions({ rate, pitch, volume });
      if (typeof calmMode === 'boolean' && edgeCalmMode !== calmMode) {
        edgeCalmMode = calmMode;
        if (selectedVoice.isOnline && rawParsedCues.length > 0) {
          regroupSubtitles(true);
        }
      }
    },
    setSubtitles(rawSrt) {
      this.clearSubtitles();
      if (!rawSrt) return;
      rawParsedCues = parseSrtOrVtt(rawSrt, null);
      cues = groupLektorSentences(rawParsedCues, currentNormalizationOptions());
      lastSpokenCueIndex = -1;
      edgePlayback.setCues(cues);
    },
    clearSubtitles() {
      rawParsedCues = [];
      cues = [];
      interruptedSystemCueIndex = null;
      systemSpeech?.stop();
      volumeDucker.restore();
      edgePlayback.setCues([]);
    },
    updatePlaybackState(positionMs, playing, buffering = false) {
      const livePos = nativePosition();
      const effectivePos = typeof livePos === 'number' ? livePos : positionMs;
      const livePlaying = nativeIsPlaying();
      const effectivePlaying =
        typeof livePlaying === 'boolean' ? livePlaying : Boolean(playing && !buffering);

      edgePlayback.update(effectivePos, effectivePlaying);
      const discontinuity = Math.abs(effectivePos - currentPositionMs) > 2000;
      currentPositionMs = effectivePos;
      const wasPlaying = isPlaying;
      isPlaying = effectivePlaying;

      if (discontinuity) {
        this.onSeek(effectivePos);
      } else if (wasPlaying !== effectivePlaying && !effectivePlaying) {
        systemSpeech?.stop();
        volumeDucker.restore();
      }
    },
    onSeek(newPosMs) {
      edgePlayback.seek(newPosMs);
      currentPositionMs = newPosMs;
      const adj = adjustedCuePosition(newPosMs, timingOffsetMs);
      let prior = -1;
      for (const c of cues) {
        if (c.endMs < adj) prior = c.index;
      }
      lastSpokenCueIndex = prior;
      interruptedSystemCueIndex = null;
      systemSpeech?.stop();
      volumeDucker.restore();
    },
    release() {
      enabled = false;
      edgePlayback.release();
      stopSystemLoop();
      systemSpeech?.release();
      systemSpeech = null;
      volumeDucker.releaseImmediate();
    },
  };
}
