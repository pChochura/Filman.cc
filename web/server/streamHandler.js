import { loadServerConfig, sendJson } from './env.js';

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

export async function handleStreamRequest(req, res, configOverrides = {}) {
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Methods', 'GET, HEAD, OPTIONS');
  res.setHeader('Access-Control-Allow-Headers', '*');

  if (req.method === 'OPTIONS') {
    res.statusCode = 204;
    res.end();
    return;
  }

  const { streamApiBaseUrl } = loadServerConfig(process.cwd(), configOverrides);
  if (!streamApiBaseUrl) {
    sendJson(res, 500, { error: 'STREAM_API_BASE_URL is not configured' });
    return;
  }

  const params = getSearchParams(req);
  const health = params.get('health');
  const pathParam = params.get('path') || '';

  const targetUrl =
    health === '1'
      ? `${streamApiBaseUrl}/api/health`
      : `${streamApiBaseUrl}/api/streams/${decodeURIComponent(pathParam).replace(/^\/+/, '')}`;

  const abortController = new AbortController();
  const onClose = () => abortController.abort();
  req.on?.('close', onClose);

  try {
    const upstream = await fetch(targetUrl, {
      headers: {
        Accept: req.headers?.accept || 'text/event-stream, application/json, */*',
      },
      signal: abortController.signal,
    });

    res.statusCode = upstream.status;
    upstream.headers.forEach((val, key) => {
      const lower = key.toLowerCase();
      if (
        lower !== 'transfer-encoding' &&
        lower !== 'content-encoding' &&
        lower !== 'content-length'
      ) {
        res.setHeader(key, val);
      }
    });

    res.setHeader('Cache-Control', 'no-cache');
    res.setHeader('X-Accel-Buffering', 'no');
    res.setHeader('Access-Control-Allow-Origin', '*');

    if (!upstream.body || req.method === 'HEAD') {
      res.end();
      return;
    }

    const reader = upstream.body.getReader();
    while (true) {
      const { done, value } = await reader.read();
      if (done) break;
      res.write(value);
      if (typeof res.flush === 'function') {
        res.flush();
      }
    }
    res.end();
  } catch (err) {
    if (err?.name === 'AbortError') {
      res.end();
      return;
    }
    if (!res.headersSent) {
      sendJson(res, 502, { error: err?.message || 'Stream proxy error' });
    } else {
      res.end();
    }
  } finally {
    req.off?.('close', onClose);
  }
}
