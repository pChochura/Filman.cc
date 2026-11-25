import { loadServerConfig, readRequestBody, sendJson } from './env.js';

export async function handleDevicesRequest(req, res, configOverrides = {}) {
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Methods', 'GET, POST, OPTIONS');
  res.setHeader('Access-Control-Allow-Headers', 'Content-Type, Authorization, X-Api-Key');

  if (req.method === 'OPTIONS') {
    res.statusCode = 204;
    res.end();
    return;
  }

  const { streamApiBaseUrl, streamApiKey } = loadServerConfig(process.cwd(), configOverrides);
  if (!streamApiBaseUrl) {
    sendJson(res, 500, { success: false, message: 'Devices API base URL is not configured' });
    return;
  }

  const parsedUrl = new URL(req.url || '/', 'http://localhost');
  // Determine subpath relative to /api/devices or full /api/devices path
  let targetPath = parsedUrl.pathname;
  if (!targetPath.startsWith('/api/devices')) {
    targetPath = `/api/devices${targetPath.startsWith('/') ? '' : '/'}${targetPath}`;
  }

  const targetUrl = `${streamApiBaseUrl}${targetPath}${parsedUrl.search}`;
  const isEventStream = Boolean(
    req.headers?.accept?.includes('text/event-stream') || targetPath.includes('/events')
  );

  const abortController = new AbortController();
  const onClose = () => abortController.abort();
  req.on?.('close', onClose);

  try {
    const headers = {
      Accept: req.headers?.accept || (isEventStream ? 'text/event-stream' : 'application/json'),
    };
    if (streamApiKey) {
      headers['X-Api-Key'] = streamApiKey;
    }
    const auth = req.headers?.authorization || req.headers?.Authorization;
    if (auth) {
      headers['Authorization'] = auth;
    }

    const fetchOptions = {
      method: req.method,
      headers,
      signal: isEventStream ? abortController.signal : AbortSignal.timeout(15_000),
    };

    if (req.method === 'POST') {
      headers['Content-Type'] = 'application/json; charset=utf-8';
      fetchOptions.body = await readRequestBody(req);
    }

    const upstream = await fetch(targetUrl, fetchOptions);
    const contentType = upstream.headers.get('content-type') || 'application/json; charset=utf-8';

    res.statusCode = upstream.status;
    res.setHeader('Content-Type', contentType);
    res.setHeader('Access-Control-Allow-Origin', '*');

    if (contentType.includes('text/event-stream') || isEventStream) {
      res.setHeader('Cache-Control', 'no-cache, no-transform');
      res.setHeader('Connection', 'keep-alive');
      res.setHeader('X-Accel-Buffering', 'no');

      if (!upstream.body) {
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
      return;
    }

    const bodyText = await upstream.text();
    res.end(bodyText);
  } catch (err) {
    if (err?.name === 'AbortError') {
      res.end();
      return;
    }
    if (!res.headersSent) {
      sendJson(res, 502, {
        success: false,
        message: err?.message || 'Failed to connect to the devices server',
      });
    } else {
      res.end();
    }
  } finally {
    req.off?.('close', onClose);
  }
}
