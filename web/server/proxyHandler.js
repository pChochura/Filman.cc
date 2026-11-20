import { Readable } from 'node:stream';

export const PLAYER_USER_AGENT =
  'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Safari/537.36';

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

function buildProxyUrl(resolvedUrl, headersParam) {
  const encodedUrl = encodeURIComponent(resolvedUrl);
  const encodedHeaders = headersParam ? `&headers=${encodeURIComponent(headersParam)}` : '';
  return `/api/proxy?url=${encodedUrl}${encodedHeaders}`;
}

export function rewriteHlsPlaylist(text, baseUrl, headersParam) {
  const lines = text.split(/\r?\n/);
  const rewritten = lines.map((line) => {
    const trimmed = line.trim();
    if (!trimmed) return line;
    if (trimmed.startsWith('#')) {
      return line.replace(/URI="([^"]+)"/gi, (_match, rawUri) => {
        try {
          const abs = new URL(rawUri, baseUrl).toString();
          return `URI="${buildProxyUrl(abs, headersParam)}"`;
        } catch {
          return `URI="${rawUri}"`;
        }
      });
    }
    try {
      const abs = new URL(trimmed, baseUrl).toString();
      return buildProxyUrl(abs, headersParam);
    } catch {
      return line;
    }
  });
  return rewritten.join('\n');
}

function looksLikeHls(targetUrl, contentType) {
  const lowerCt = (contentType || '').toLowerCase();
  if (lowerCt.includes('mpegurl') || lowerCt.includes('x-mpegurl')) return true;
  try {
    const u = new URL(targetUrl);
    if (u.pathname.toLowerCase().endsWith('.m3u8') || u.pathname.includes('/m3u8-proxy')) {
      return true;
    }
  } catch {
    // ignore
  }
  return targetUrl.toLowerCase().includes('.m3u8');
}

export async function handleProxyRequest(req, res) {
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Methods', 'GET, HEAD, OPTIONS');
  res.setHeader('Access-Control-Allow-Headers', '*');
  res.setHeader(
    'Access-Control-Expose-Headers',
    'Content-Length, Content-Range, Accept-Ranges, X-Final-Url, X-Content-Disposition',
  );

  if (req.method === 'OPTIONS') {
    res.statusCode = 204;
    res.end();
    return;
  }

  const params = getSearchParams(req);
  const target = params.get('url');
  const headersParam = params.get('headers') || '';

  if (!target) {
    res.statusCode = 400;
    res.end('Missing url parameter');
    return;
  }

  let parsedTarget;
  try {
    parsedTarget = new URL(target);
    if (parsedTarget.protocol !== 'http:' && parsedTarget.protocol !== 'https:') {
      res.statusCode = 400;
      res.end('Unsupported url protocol');
      return;
    }
  } catch {
    res.statusCode = 400;
    res.end('Invalid url parameter');
    return;
  }

  let customHeaders = {};
  if (headersParam) {
    try {
      const parsed = JSON.parse(headersParam);
      if (parsed && typeof parsed === 'object') {
        customHeaders = parsed;
      }
    } catch {
      // ignore invalid JSON headers
    }
  }

  const fetchHeaders = {
    'User-Agent': PLAYER_USER_AGENT,
    ...customHeaders,
  };
  if (req.headers?.range) {
    fetchHeaders['Range'] = req.headers.range;
  }

  const abortController = new AbortController();
  const onClose = () => abortController.abort();
  req.on?.('close', onClose);

  try {
    const upstream = await fetch(parsedTarget.toString(), {
      method: req.method === 'HEAD' ? 'HEAD' : 'GET',
      headers: fetchHeaders,
      redirect: 'follow',
      signal: abortController.signal,
    });

    const contentType = upstream.headers.get('content-type') || '';
    const isPlaylist = req.method !== 'HEAD' && looksLikeHls(upstream.url || target, contentType);

    res.statusCode = upstream.status;
    upstream.headers.forEach((value, key) => {
      const lower = key.toLowerCase();
      if (
        lower === 'content-security-policy' ||
        lower === 'x-frame-options' ||
        lower === 'transfer-encoding' ||
        lower === 'content-encoding'
      ) {
        return;
      }
      if (isPlaylist && lower === 'content-length') {
        return;
      }
      res.setHeader(key, value);
    });

    if (upstream.url) {
      res.setHeader('X-Final-Url', upstream.url);
    }
    const disposition = upstream.headers.get('content-disposition');
    if (disposition) {
      res.setHeader('X-Content-Disposition', disposition);
    }
    res.setHeader('Access-Control-Allow-Origin', '*');

    if (req.method === 'HEAD' || !upstream.body) {
      res.end();
      return;
    }

    if (isPlaylist) {
      const text = await upstream.text();
      if (text.trimStart().startsWith('#EXTM3U')) {
        const rewritten = rewriteHlsPlaylist(text, upstream.url || target, headersParam);
        res.setHeader('Content-Type', contentType || 'application/vnd.apple.mpegurl');
        res.end(rewritten);
        return;
      }
      res.end(text);
      return;
    }

    const nodeStream = Readable.fromWeb(upstream.body);
    nodeStream.on('error', () => {
      if (!res.writableEnded) res.end();
    });
    nodeStream.pipe(res);
  } catch (err) {
    if (err?.name === 'AbortError') {
      res.end();
      return;
    }
    if (!res.headersSent) {
      res.statusCode = 502;
      res.end(`Proxy error: ${err?.message || 'Unknown error'}`);
    } else if (!res.writableEnded) {
      res.end();
    }
  } finally {
    req.off?.('close', onClose);
  }
}
