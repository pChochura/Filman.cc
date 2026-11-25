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

  try {
    const headers = {
      Accept: 'application/json',
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
      signal: AbortSignal.timeout(15_000),
    };

    if (req.method === 'POST') {
      headers['Content-Type'] = 'application/json; charset=utf-8';
      fetchOptions.body = await readRequestBody(req);
    }

    const upstream = await fetch(targetUrl, fetchOptions);
    const bodyText = await upstream.text();
    res.statusCode = upstream.status;
    res.setHeader('Content-Type', upstream.headers.get('content-type') || 'application/json; charset=utf-8');
    res.setHeader('Access-Control-Allow-Origin', '*');
    res.end(bodyText);
  } catch (err) {
    sendJson(res, 502, {
      success: false,
      message: err?.message || 'Failed to connect to the devices server',
    });
  }
}
