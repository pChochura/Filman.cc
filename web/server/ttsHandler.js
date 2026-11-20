import { readRequestBody, sendJson } from './env.js';
import { ALLOWED_EDGE_VOICES, synthesizeEdgeTts } from './edgeTtsClient.js';

export async function handleTtsRequest(req, res) {
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Methods', 'POST, OPTIONS');
  res.setHeader('Access-Control-Allow-Headers', 'Content-Type');

  if (req.method === 'OPTIONS') {
    res.statusCode = 204;
    res.end();
    return;
  }

  if (req.method !== 'POST') {
    sendJson(res, 405, { error: 'Method not allowed' });
    return;
  }

  try {
    const rawBody = await readRequestBody(req);
    let payload = {};
    try {
      payload = rawBody ? JSON.parse(rawBody) : {};
    } catch {
      sendJson(res, 400, { error: 'Invalid JSON body' });
      return;
    }

    const text = typeof payload.text === 'string' ? payload.text : '';
    const voiceName = typeof payload.voiceName === 'string' ? payload.voiceName : 'pl-PL-MarekNeural';
    const rate = typeof payload.rate === 'string' ? payload.rate : '+10%';
    const pitch = typeof payload.pitch === 'string' ? payload.pitch : '+0Hz';
    const volume = typeof payload.volume === 'string' ? payload.volume : '+0%';

    if (!text.trim()) {
      sendJson(res, 400, { error: 'Missing text' });
      return;
    }
    if (!ALLOWED_EDGE_VOICES.has(voiceName)) {
      sendJson(res, 400, { error: 'Unsupported Edge TTS voice' });
      return;
    }

    const result = await synthesizeEdgeTts({ text, voiceName, rate, pitch, volume });
    res.statusCode = 200;
    res.setHeader('Content-Type', 'audio/mpeg');
    res.setHeader('Content-Length', result.audio.length);
    res.setHeader('Cache-Control', 'private, max-age=300');
    res.setHeader('Access-Control-Expose-Headers', 'X-Speech-Start-Ms, X-Speech-End-Ms');
    if (result.speechStartMs !== null && result.speechStartMs !== undefined) {
      res.setHeader('X-Speech-Start-Ms', String(result.speechStartMs));
    }
    if (result.speechEndMs !== null && result.speechEndMs !== undefined) {
      res.setHeader('X-Speech-End-Ms', String(result.speechEndMs));
    }
    res.end(result.audio);
  } catch (err) {
    sendJson(res, 502, { error: err?.message || 'Edge TTS synthesis failed' });
  }
}
