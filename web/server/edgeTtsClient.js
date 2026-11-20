import crypto from 'node:crypto';
import WebSocket from 'ws';
import {
  GEC_VERSION,
  TRUSTED_CLIENT_TOKEN,
  generateGecToken,
} from './edgeGecToken.js';

const ENDPOINT = 'wss://speech.platform.bing.com/consumer/speech/synthesize/readaloud/edge/v1';
const VOICE_LIST_URL =
  `https://speech.platform.bing.com/consumer/speech/synthesize/readaloud/voices/list?trustedclienttoken=${TRUSTED_CLIENT_TOKEN}`;
const USER_AGENT =
  'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 ' +
  '(KHTML, like Gecko) Chrome/143.0.0.0 Safari/537.36 Edg/143.0.0.0';
const ORIGIN = 'chrome-extension://jdiccldimpdaibmpdkjnbmckianbfold';
const REQUEST_TIMEOUT_MS = 30_000;
const MAX_AUDIO_BYTES = 2 * 1024 * 1024;
const TICKS_PER_MS = 10_000;

export const ALLOWED_EDGE_VOICES = new Set([
  'pl-PL-MarekNeural',
  'pl-PL-ZofiaNeural',
  'en-US-AndrewMultilingualNeural',
  'en-US-AvaMultilingualNeural',
  'en-US-BrianMultilingualNeural',
  'en-US-EmmaMultilingualNeural',
  'fr-FR-RemyMultilingualNeural',
  'fr-FR-VivienneMultilingualNeural',
]);

let clockSkewSeconds = 0;

export function getClockSkewSeconds() {
  return clockSkewSeconds;
}

export function setClockSkewSeconds(val) {
  clockSkewSeconds = Number(val) || 0;
}

function uuidNoDashes() {
  return crypto.randomUUID().replace(/-/g, '');
}

export function formatEdgeTimestamp(millis) {
  return new Date(millis).toISOString();
}

export function escapeXmlText(text) {
  let out = '';
  for (const ch of String(text)) {
    const code = ch.codePointAt(0);
    if (code === 38) out += '&amp;';
    else if (code === 60) out += '&lt;';
    else if (code === 62) out += '&gt;';
    else if (code === 34) out += '&quot;';
    else if (code === 39) out += '&apos;';
    else if (
      code === 9 ||
      code === 10 ||
      code === 13 ||
      (code >= 0x20 && code <= 0xd7ff) ||
      (code >= 0xe000 && code <= 0xfffd) ||
      (code >= 0x10000 && code <= 0x10ffff)
    ) {
      out += ch;
    } else {
      out += ' ';
    }
  }
  return out;
}

export function buildConfigMessage(timestamp) {
  return (
    `X-Timestamp:${timestamp}\r\n` +
    `Content-Type:application/json; charset=utf-8\r\n` +
    `Path:speech.config\r\n\r\n` +
    `{"context":{"synthesis":{"audio":{"metadataoptions":{"sentenceBoundaryEnabled":"false","wordBoundaryEnabled":"true"},"outputFormat":"audio-24khz-48kbitrate-mono-mp3"}}}}`
  );
}

export function buildSsmlMessage({
  requestId,
  timestamp,
  text,
  voiceName = 'pl-PL-MarekNeural',
  rate = '+10%',
  pitch = '+0Hz',
  volume = '+0%',
}) {
  if (!ALLOWED_EDGE_VOICES.has(voiceName)) {
    throw new Error(`Voice must be an Edge neural voice: ${voiceName}`);
  }
  const safeText = escapeXmlText(text);
  return (
    `X-RequestId:${requestId}\r\n` +
    `Content-Type:application/ssml+xml\r\n` +
    `X-Timestamp:${timestamp}\r\n` +
    `Path:ssml\r\n\r\n` +
    `<speak version="1.0" xmlns="http://www.w3.org/2001/10/synthesis" xml:lang="pl-PL">` +
    `<voice name="${voiceName}"><prosody rate="${rate}" pitch="${pitch}" volume="${volume}">` +
    `${safeText}</prosody></voice></speak>`
  );
}

function parseHeaders(headerStr) {
  const map = {};
  for (const line of headerStr.split('\r\n')) {
    const sep = line.indexOf(':');
    if (sep > 0) {
      const k = line.slice(0, sep).trim().toLowerCase();
      const v = line.slice(sep + 1).trim();
      map[k] = v;
    }
  }
  return map;
}

export function parseWordBoundaryRangeMs(metadataJson) {
  try {
    const envelope = JSON.parse(metadataJson);
    const items = Array.isArray(envelope?.Metadata) ? envelope.Metadata : [];
    let minStartMs = null;
    let maxEndMs = null;
    for (const item of items) {
      if (item?.Type !== 'WordBoundary' && item?.Type !== 'SentenceBoundary') continue;
      const offsetTicks = item?.Data?.Offset;
      const durationTicks = item?.Data?.Duration;
      if (typeof offsetTicks !== 'number' || typeof durationTicks !== 'number') continue;
      if (offsetTicks < 0 || durationTicks <= 0) continue;
      const startMs = Math.floor(offsetTicks / TICKS_PER_MS);
      const endMs = Math.floor((offsetTicks + durationTicks) / TICKS_PER_MS);
      minStartMs = minStartMs === null ? startMs : Math.min(minStartMs, startMs);
      maxEndMs = maxEndMs === null ? endMs : Math.max(maxEndMs, endMs);
    }
    if (minStartMs !== null && maxEndMs !== null && maxEndMs > minStartMs) {
      return { speechStartMs: minStartMs, speechEndMs: maxEndMs };
    }
    return null;
  } catch {
    return null;
  }
}

export function parseAudioFrame(frame) {
  const buf = Buffer.isBuffer(frame) ? frame : Buffer.from(frame);
  if (buf.length < 2) throw new Error('Missing speech frame header length');
  const headerLength = buf.readUInt16BE(0);
  if (headerLength > buf.length - 2) throw new Error('Invalid speech frame header length');
  const headerText = buf.subarray(2, 2 + headerLength).toString('utf8');
  const fields = parseHeaders(headerText);
  if (fields['path'] !== 'audio') throw new Error('Unexpected binary speech frame');
  const payload = buf.subarray(2 + headerLength);
  const contentType = fields['content-type'];
  if (
    (payload.length > 0 && contentType !== 'audio/mpeg') ||
    (payload.length === 0 && contentType !== undefined && contentType !== 'audio/mpeg')
  ) {
    throw new Error('Unexpected speech audio content type');
  }
  return payload;
}

function updateClockSkewFromDateHeader(dateHeader) {
  if (!dateHeader) return false;
  const parsed = Date.parse(dateHeader);
  if (Number.isNaN(parsed)) return false;
  const serverSeconds = Math.floor(parsed / 1000);
  const localSeconds = Math.floor(Date.now() / 1000);
  clockSkewSeconds = serverSeconds - localSeconds;
  return true;
}

async function syncClockSkewFromServer() {
  try {
    const resp = await fetch(VOICE_LIST_URL, {
      headers: { 'User-Agent': USER_AGENT },
    });
    updateClockSkewFromDateHeader(resp.headers.get('date'));
  } catch {
    // Best-effort clock skew sync
  }
}

class SpeechConnectionError extends Error {
  constructor(code, hasServerDate, message) {
    super(message);
    this.code = code;
    this.hasServerDate = hasServerDate;
  }
}

function executeSynthesize({ text, voiceName, rate, pitch, volume }) {
  return new Promise((resolve, reject) => {
    const nowMillis = Date.now() + clockSkewSeconds * 1000;
    const nowSeconds = Math.floor(nowMillis / 1000);
    const gec = generateGecToken(nowSeconds, TRUSTED_CLIENT_TOKEN);
    const connectionId = uuidNoDashes();
    const requestId = uuidNoDashes();
    const muid = uuidNoDashes().toUpperCase();

    const url =
      `${ENDPOINT}?TrustedClientToken=${TRUSTED_CLIENT_TOKEN}` +
      `&ConnectionId=${connectionId}` +
      `&Sec-MS-GEC=${gec}` +
      `&Sec-MS-GEC-Version=${GEC_VERSION}`;

    const ws = new WebSocket(url, {
      headers: {
        'User-Agent': USER_AGENT,
        Origin: ORIGIN,
        'Cache-Control': 'no-cache',
        Pragma: 'no-cache',
        'Accept-Encoding': 'gzip, deflate, br, zstd',
        'Accept-Language': 'pl-PL,pl;q=0.9,en;q=0.5',
        Cookie: `muid=${muid};`,
      },
    });

    const audioChunks = [];
    let totalAudioBytes = 0;
    let minSpeechStartMs = null;
    let maxSpeechEndMs = null;
    let settled = false;

    const finish = (err, value) => {
      if (settled) return;
      settled = true;
      clearTimeout(timer);
      try {
        ws.terminate();
      } catch {
        // ignore
      }
      if (err) reject(err);
      else resolve(value);
    };

    const timer = setTimeout(() => {
      finish(new Error('Speech request timed out'));
    }, REQUEST_TIMEOUT_MS);

    ws.on('open', () => {
      try {
        const timestamp = formatEdgeTimestamp(Date.now() + clockSkewSeconds * 1000);
        ws.send(buildConfigMessage(timestamp));
        ws.send(
          buildSsmlMessage({
            requestId,
            timestamp,
            text,
            voiceName,
            rate,
            pitch,
            volume,
          }),
        );
      } catch (err) {
        finish(err);
      }
    });

    ws.on('message', (data, isBinary) => {
      if (!isBinary) {
        const str = data.toString('utf8');
        const sepIdx = str.indexOf('\r\n\r\n');
        const headerPart = sepIdx >= 0 ? str.slice(0, sepIdx) : str;
        const bodyPart = sepIdx >= 0 ? str.slice(sepIdx + 4) : '';
        const msgPath = parseHeaders(headerPart)['path'];

        if (msgPath === 'audio.metadata') {
          const range = parseWordBoundaryRangeMs(bodyPart);
          if (range) {
            minSpeechStartMs =
              minSpeechStartMs === null ? range.speechStartMs : Math.min(minSpeechStartMs, range.speechStartMs);
            maxSpeechEndMs =
              maxSpeechEndMs === null ? range.speechEndMs : Math.max(maxSpeechEndMs, range.speechEndMs);
          }
        } else if (msgPath === 'turn.end') {
          if (totalAudioBytes === 0) {
            finish(new Error('Speech response contained no audio'));
          } else {
            finish(null, {
              audio: Buffer.concat(audioChunks),
              speechStartMs: minSpeechStartMs,
              speechEndMs: maxSpeechEndMs,
            });
          }
        } else if (msgPath === 'turn.start' || msgPath === 'response') {
          // ignore control messages
        } else {
          finish(new Error(`Unexpected speech response path: ${msgPath}`));
        }
      } else {
        try {
          const payload = parseAudioFrame(data);
          if (totalAudioBytes + payload.length > MAX_AUDIO_BYTES) {
            finish(new Error('Speech audio exceeds size limit'));
            return;
          }
          if (payload.length > 0) {
            audioChunks.push(payload);
            totalAudioBytes += payload.length;
          }
        } catch (err) {
          finish(err);
        }
      }
    });

    ws.on('unexpected-response', (_req, res) => {
      const code = res?.statusCode;
      const dateHeader = res?.headers?.date;
      if (code === 403) {
        const synced = updateClockSkewFromDateHeader(dateHeader);
        finish(new SpeechConnectionError(403, synced, 'Speech connection failed (HTTP 403)'));
      } else {
        finish(new Error(`Speech connection failed (HTTP ${code ?? 'unavailable'})`));
      }
    });

    ws.on('error', (err) => {
      finish(new Error(`Speech connection failed: ${err.message}`));
    });

    ws.on('close', (code) => {
      finish(new Error(`Speech connection closed before completion (${code})`));
    });
  });
}

export async function synthesizeEdgeTts({
  text,
  voiceName = 'pl-PL-MarekNeural',
  rate = '+10%',
  pitch = '+0Hz',
  volume = '+0%',
}) {
  if (!text || !String(text).trim()) {
    throw new Error('Subtitle text must not be blank');
  }
  if (!ALLOWED_EDGE_VOICES.has(voiceName)) {
    throw new Error('Voice must be an Edge neural voice');
  }

  try {
    return await executeSynthesize({ text, voiceName, rate, pitch, volume });
  } catch (err) {
    if (err instanceof SpeechConnectionError && err.code === 403) {
      if (!err.hasServerDate) {
        await syncClockSkewFromServer();
      }
      return await executeSynthesize({ text, voiceName, rate, pitch, volume });
    }
    throw err;
  }
}
