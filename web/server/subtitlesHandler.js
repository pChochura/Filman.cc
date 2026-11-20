import { loadServerConfig, readRequestBody, sendJson } from './env.js';
import { decodeSubtitleBytes } from './subtitleDecoder.js';

const CHUNK_SIZE = 65536;
const DEFAULT_SUBTITLE_USER_AGENT = 'Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36';

function getSearchParams(req) {
  const parsed = new URL(req.url || '/', 'http://localhost');
  if (req.query && typeof req.query === 'object') {
    for (const [k, v] of Object.entries(req.query)) {
      if (v !== undefined && !parsed.searchParams.has(k)) {
        parsed.searchParams.set(k, Array.isArray(v) ? v[0] : String(v));
      }
    }
  }
  return parsed.searchParams;
}

function extractCharset(contentType) {
  if (!contentType) return null;
  const match = /charset=([^\s;]+)/i.exec(contentType);
  return match ? match[1].replace(/['"]/g, '').trim() : null;
}

function sumLittleEndianUint64(bytes) {
  const dv = new DataView(bytes.buffer, bytes.byteOffset, bytes.byteLength);
  let sum = 0n;
  const usable = Math.floor(bytes.byteLength / 8) * 8;
  for (let i = 0; i < usable; i += 8) {
    sum = (sum + dv.getBigUint64(i, true)) & 0xffffffffffffffffn;
  }
  return sum;
}

export function computeOpenSubtitlesHash(headBytes, tailBytes, fileSize) {
  const headSum = sumLittleEndianUint64(headBytes);
  const tailSum = sumLittleEndianUint64(tailBytes);
  const total = (BigInt(fileSize) + headSum + tailSum) & 0xffffffffffffffffn;
  return total.toString(16).padStart(16, '0');
}

async function probeVideo({ url, headers = {} }) {
  if (!url || /\.m3u8/i.test(url) || /\.mpd/i.test(url)) {
    return null;
  }
  try {
    const headResp = await fetch(url, {
      headers: {
        ...headers,
        Range: `bytes=0-${CHUNK_SIZE - 1}`,
      },
      redirect: 'follow',
      signal: AbortSignal.timeout(5000),
    });
    if (headResp.status !== 206) return null;

    const finalUrl = headResp.url || url;
    const contentDisposition = headResp.headers.get('content-disposition');
    const contentRange = headResp.headers.get('content-range');
    let totalLength = -1;
    if (contentRange) {
      const slashIdx = contentRange.lastIndexOf('/');
      if (slashIdx !== -1) {
        const parsed = Number.parseInt(contentRange.slice(slashIdx + 1).trim(), 10);
        if (Number.isFinite(parsed)) totalLength = parsed;
      }
    }

    const headBytes = new Uint8Array(await headResp.arrayBuffer());
    if (headBytes.length < CHUNK_SIZE || totalLength <= CHUNK_SIZE * 2) {
      return {
        movieHash: null,
        fileSize: totalLength > 0 ? totalLength : null,
        finalUrl,
        contentDisposition,
      };
    }

    const tailResp = await fetch(finalUrl, {
      headers: {
        ...headers,
        Range: `bytes=${totalLength - CHUNK_SIZE}-${totalLength - 1}`,
      },
      redirect: 'follow',
      signal: AbortSignal.timeout(5000),
    });

    if (tailResp.status !== 206) {
      return {
        movieHash: null,
        fileSize: totalLength,
        finalUrl,
        contentDisposition,
      };
    }

    const tailBytes = new Uint8Array(await tailResp.arrayBuffer());
    if (tailBytes.length < CHUNK_SIZE) {
      return {
        movieHash: null,
        fileSize: totalLength,
        finalUrl,
        contentDisposition,
      };
    }

    const movieHash = computeOpenSubtitlesHash(headBytes, tailBytes, totalLength);
    return {
      movieHash,
      fileSize: totalLength,
      finalUrl,
      contentDisposition,
    };
  } catch {
    return null;
  }
}

export async function handleSubtitlesRequest(req, res, configOverrides = {}) {
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Methods', 'GET, POST, OPTIONS');
  res.setHeader('Access-Control-Allow-Headers', 'Content-Type');

  if (req.method === 'OPTIONS') {
    res.statusCode = 204;
    res.end();
    return;
  }

  const { openSubtitlesApiKey, wyzieSubsApiKey } = loadServerConfig(process.cwd(), configOverrides);
  const params = getSearchParams(req);
  const action = params.get('action') || '';
  const provider = params.get('provider') || '';

  try {
    if (action === 'probe' && req.method === 'POST') {
      const raw = await readRequestBody(req);
      const body = raw ? JSON.parse(raw) : {};
      const probe = await probeVideo(body);
      sendJson(res, 200, probe || { movieHash: null, fileSize: null, finalUrl: null, contentDisposition: null });
      return;
    }

    if (action === 'fetch') {
      const targetUrl = params.get('url');
      if (!targetUrl) {
        sendJson(res, 400, { error: 'Missing url parameter' });
        return;
      }
      const isDownloaded = params.get('downloaded') === '1';
      let customHeaders = {};
      const headersParam = params.get('headers');
      if (headersParam && !isDownloaded) {
        try {
          const parsed = JSON.parse(headersParam);
          if (parsed && typeof parsed === 'object') customHeaders = parsed;
        } catch {
          // ignore
        }
      }
      const hasUa = Object.keys(customHeaders).some((k) => k.toLowerCase() === 'user-agent');
      if (isDownloaded || !hasUa) {
        customHeaders['User-Agent'] = DEFAULT_SUBTITLE_USER_AGENT;
      }

      const resp = await fetch(targetUrl, {
        headers: customHeaders,
        redirect: 'follow',
        signal: AbortSignal.timeout(30_000),
      });
      if (!resp.ok) {
        sendJson(res, resp.status, { error: `Failed to fetch subtitle: HTTP ${resp.status}` });
        return;
      }
      const buf = new Uint8Array(await resp.arrayBuffer());
      const declaredCharset = extractCharset(resp.headers.get('content-type'));
      const text = decodeSubtitleBytes(buf, declaredCharset);
      sendJson(res, 200, { text });
      return;
    }

    if (provider === 'opensubtitles') {
      if (!openSubtitlesApiKey) {
        sendJson(res, 200, action === 'download' ? { error: 'OPEN_SUBTITLES_API_KEY not configured' } : { data: [] });
        return;
      }
      if (action === 'download' && req.method === 'POST') {
        const raw = await readRequestBody(req);
        const body = raw ? JSON.parse(raw) : {};
        const resp = await fetch('https://api.opensubtitles.com/api/v1/download', {
          method: 'POST',
          headers: {
            'Api-Key': openSubtitlesApiKey,
            'User-Agent': 'tiwi',
            Accept: 'application/json',
            'Content-Type': 'application/json',
          },
          body: JSON.stringify({ file_id: body.file_id }),
          signal: AbortSignal.timeout(15_000),
        });
        const text = await resp.text();
        res.statusCode = resp.status;
        res.setHeader('Content-Type', 'application/json; charset=utf-8');
        res.end(text);
        return;
      }

      const forwardParams = new URLSearchParams();
      for (const key of [
        'languages',
        'moviehash',
        'tmdb_id',
        'parent_tmdb_id',
        'imdb_id',
        'parent_imdb_id',
        'query',
        'season_number',
        'episode_number',
      ]) {
        const val = params.get(key);
        if (val !== null && val !== '') forwardParams.set(key, val);
      }

      const resp = await fetch(`https://api.opensubtitles.com/api/v1/subtitles?${forwardParams.toString()}`, {
        headers: {
          'Api-Key': openSubtitlesApiKey,
          'User-Agent': 'tiwi',
          Accept: 'application/json',
        },
        signal: AbortSignal.timeout(15_000),
      });
      const text = await resp.text();
      res.statusCode = resp.status;
      res.setHeader('Content-Type', 'application/json; charset=utf-8');
      res.end(text);
      return;
    }

    if (provider === 'wyzie') {
      if (!wyzieSubsApiKey) {
        sendJson(res, 200, []);
        return;
      }
      const id = params.get('id');
      if (!id) {
        sendJson(res, 200, []);
        return;
      }
      const wyzieUrl = new URL('https://sub.wyzie.io/search');
      wyzieUrl.searchParams.set('id', id);
      wyzieUrl.searchParams.set('key', wyzieSubsApiKey);
      const season = params.get('season');
      const episode = params.get('episode');
      const language = params.get('language');
      if (season && episode) {
        wyzieUrl.searchParams.set('season', season);
        wyzieUrl.searchParams.set('episode', episode);
      }
      if (language) {
        wyzieUrl.searchParams.set('language', language.toLowerCase());
      }

      const resp = await fetch(wyzieUrl.toString(), {
        signal: AbortSignal.timeout(30_000),
      });
      if (!resp.ok) {
        sendJson(res, 200, []);
        return;
      }
      const text = await resp.text();
      res.statusCode = 200;
      res.setHeader('Content-Type', 'application/json; charset=utf-8');
      res.end(text);
      return;
    }

    sendJson(res, 400, { error: 'Invalid subtitles request' });
  } catch (err) {
    sendJson(res, 502, { error: err?.message || 'Subtitles request failed' });
  }
}
