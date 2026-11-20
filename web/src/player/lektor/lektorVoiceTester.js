import { getLektorVoiceById, systemVoiceNameFromId } from '../../data/models/lektorModels.js';
import { settingsStore } from '../../data/local/settingsStore.js';
import { t } from '../../i18n/index.js';
import { synthesizeEdgeTtsOverHttp } from './edgeLektorPlayback.js';
import { createSystemLektorSpeech } from './systemLektorSpeech.js';

let activePreviewAudio = null;
let activePreviewUrl = null;
let activeSystemPreview = null;
let activePreviewController = null;

export function stopLektorVoicePreview() {
  if (activePreviewController) {
    activePreviewController.abort();
    activePreviewController = null;
  }
  if (activePreviewAudio) {
    try {
      activePreviewAudio.pause();
    } catch {
      // ignore
    }
    activePreviewAudio = null;
  }
  if (activePreviewUrl) {
    URL.revokeObjectURL(activePreviewUrl);
    activePreviewUrl = null;
  }
  if (activeSystemPreview) {
    activeSystemPreview.release();
    activeSystemPreview = null;
  }
}

export async function testLektorVoice(voiceId = settingsStore.get().lektorVoice, onToast) {
  stopLektorVoicePreview();
  const voice = getLektorVoiceById(voiceId);
  const sampleText = t('lektor_system_tts_preview');

  if (voice.isOnline) {
    const settings = settingsStore.get();
    const controller = new AbortController();
    activePreviewController = controller;
    try {
      const result = await synthesizeEdgeTtsOverHttp({
        text: sampleText,
        voiceName: voice.edgeVoiceName,
        rate: settings.lektorEdgeRate,
        pitch: settings.lektorEdgePitch,
        volume: settings.lektorEdgeVolume,
        signal: controller.signal,
      });
      if (activePreviewController !== controller) return;
      const blob = new Blob([result.audioBytes], { type: 'audio/mpeg' });
      activePreviewUrl = URL.createObjectURL(blob);
      activePreviewAudio = new Audio(activePreviewUrl);
      activePreviewAudio.addEventListener(
        'ended',
        () => {
          stopLektorVoicePreview();
        },
        { once: true },
      );
      await activePreviewAudio.play();
    } catch (err) {
      if (err?.name !== 'AbortError') {
        onToast?.(t('lektor_edge_error'));
      }
    } finally {
      if (activePreviewController === controller) {
        activePreviewController = null;
      }
    }
    return;
  }

  const sysName = systemVoiceNameFromId(voiceId);
  const sys = createSystemLektorSpeech({
    systemVoiceName: sysName,
    onSpeechEnd: () => {
      if (activeSystemPreview === sys) activeSystemPreview = null;
    },
  });
  activeSystemPreview = sys;
  const ok = sys.speak(sampleText, 1.0);
  if (!ok) {
    onToast?.(t('lektor_system_tts_unavailable'));
  }
}
