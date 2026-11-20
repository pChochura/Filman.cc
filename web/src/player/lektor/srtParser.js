const TIMESTAMP_LINE_REGEX =
  /((?:\d{1,2}:)?\d{1,2}:\d{2}[,.]\d{3})\s*-->\s*((?:\d{1,2}:)?\d{1,2}:\d{2}[,.]\d{3})/;

function parseTimestampToMs(timestamp) {
  const normalized = String(timestamp || '').replace(',', '.').trim();
  const parts = normalized.split(':');
  if (parts.length === 3) {
    const hours = Number.parseInt(parts[0], 10) || 0;
    const minutes = Number.parseInt(parts[1], 10) || 0;
    const secParts = parts[2].split('.');
    const seconds = Number.parseInt(secParts[0], 10) || 0;
    const millis = Number.parseInt((secParts[1] || '').padEnd(3, '0').slice(0, 3), 10) || 0;
    return hours * 3_600_000 + minutes * 60_000 + seconds * 1_000 + millis;
  }
  if (parts.length === 2) {
    const minutes = Number.parseInt(parts[0], 10) || 0;
    const secParts = parts[1].split('.');
    const seconds = Number.parseInt(secParts[0], 10) || 0;
    const millis = Number.parseInt((secParts[1] || '').padEnd(3, '0').slice(0, 3), 10) || 0;
    return minutes * 60_000 + seconds * 1_000 + millis;
  }
  return 0;
}

export function parseSrtOrVtt(rawSrt, transformText = null) {
  if (!rawSrt || typeof rawSrt !== 'string') return [];
  const sanitized = rawSrt.replace(/^\uFEFF/, '').replace(/\r\n/g, '\n').replace(/\r/g, '\n');
  const lines = sanitized.split('\n');
  const cues = [];

  let currentIndex = 1;
  let currentStartMs = -1;
  let currentEndMs = -1;
  let textLines = [];

  const flushCurrentCue = () => {
    if (currentStartMs >= 0 && currentEndMs > currentStartMs && textLines.length > 0) {
      const rawText = textLines.join('\n').trim();
      const finalText = transformText ? transformText(rawText) : rawText;
      if (finalText && finalText.trim().length > 0) {
        cues.push({
          index: currentIndex,
          startMs: currentStartMs,
          endMs: currentEndMs,
          durationMs: currentEndMs - currentStartMs,
          text: finalText,
        });
        currentIndex++;
      }
    }
    currentStartMs = -1;
    currentEndMs = -1;
    textLines = [];
  };

  for (const line of lines) {
    const trimmed = line.trim();
    if (
      trimmed.startsWith('WEBVTT') ||
      trimmed.startsWith('NOTE') ||
      trimmed.startsWith('STYLE') ||
      trimmed.startsWith('REGION')
    ) {
      continue;
    }

    const match = TIMESTAMP_LINE_REGEX.exec(trimmed);
    if (match) {
      flushCurrentCue();
      currentStartMs = parseTimestampToMs(match[1]);
      currentEndMs = parseTimestampToMs(match[2]);
    } else if (trimmed.length === 0) {
      flushCurrentCue();
    } else if (currentStartMs >= 0) {
      textLines.push(line);
    }
  }

  flushCurrentCue();
  return cues;
}
