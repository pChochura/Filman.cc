export function getPolishSystemVoices() {
  if (typeof window === 'undefined' || !window.speechSynthesis) {
    return [];
  }
  const all = window.speechSynthesis.getVoices() || [];
  const seen = new Set();
  const out = [];
  for (const v of all) {
    const lang = String(v.lang || '').toLowerCase();
    if (!lang.startsWith('pl')) continue;
    if (seen.has(v.name)) continue;
    seen.add(v.name);
    out.push({
      name: v.name,
      lang: v.lang,
      localService: Boolean(v.localService),
    });
  }
  return out;
}

export function createSystemLektorSpeech({
  onSpeechStart,
  onSpeechEnd,
  systemVoiceName = null,
}) {
  let activeVoiceName = systemVoiceName;
  let speaking = false;
  let currentUtterance = null;

  function findVoice() {
    if (typeof window === 'undefined' || !window.speechSynthesis) return null;
    const voices = window.speechSynthesis.getVoices() || [];
    if (activeVoiceName) {
      const exact = voices.find((v) => v.name === activeVoiceName);
      if (exact) return exact;
    }
    return voices.find((v) => String(v.lang || '').toLowerCase().startsWith('pl')) || null;
  }

  return {
    setVoiceName(name) {
      activeVoiceName = name || null;
    },
    speak(text, speed = 1.0) {
      if (typeof window === 'undefined' || !window.speechSynthesis || !text) {
        return false;
      }
      try {
        window.speechSynthesis.cancel();
        const utterance = new SpeechSynthesisUtterance(text);
        utterance.lang = 'pl-PL';
        utterance.rate = Math.max(0.5, Math.min(2.0, speed));
        const selected = findVoice();
        if (selected) utterance.voice = selected;

        currentUtterance = utterance;
        utterance.onstart = () => {
          if (currentUtterance !== utterance) return;
          speaking = true;
          onSpeechStart?.();
        };
        utterance.onend = () => {
          if (currentUtterance !== utterance) return;
          speaking = false;
          currentUtterance = null;
          onSpeechEnd?.();
        };
        utterance.onerror = () => {
          if (currentUtterance !== utterance) return;
          speaking = false;
          currentUtterance = null;
          onSpeechEnd?.();
        };
        window.speechSynthesis.speak(utterance);
        return true;
      } catch {
        speaking = false;
        return false;
      }
    },
    stop() {
      currentUtterance = null;
      if (typeof window !== 'undefined' && window.speechSynthesis) {
        try {
          window.speechSynthesis.cancel();
        } catch {
          // ignore
        }
      }
      if (speaking) {
        speaking = false;
        onSpeechEnd?.();
      }
    },
    isSpeaking() {
      return speaking;
    },
    release() {
      this.stop();
    },
  };
}
