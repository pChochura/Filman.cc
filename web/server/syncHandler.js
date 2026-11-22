import { loadServerConfig, readRequestBody, sendJson } from './env.js';

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

export async function handleSyncRequest(req, res, configOverrides = {}) {
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Methods', 'GET, POST, OPTIONS');
  res.setHeader('Access-Control-Allow-Headers', 'Content-Type, Authorization');

  if (req.method === 'OPTIONS') {
    res.statusCode = 204;
    res.end();
    return;
  }

  const { streamApiBaseUrl, streamApiKey } = loadServerConfig(process.cwd(), configOverrides);
  if (!streamApiBaseUrl) {
    sendJson(res, 500, { success: false, message: 'Sync API base URL is not configured' });
    return;
  }

  const params = getSearchParams(req);
  const action = params.get('action') || '';

  let targetUrl = '';
  let method = 'GET';
  if (action === 'login') {
    targetUrl = `${streamApiBaseUrl}/api/auth/login`;
    method = 'POST';
  } else if (action === 'register') {
    targetUrl = `${streamApiBaseUrl}/api/auth/register`;
    method = 'POST';
  } else if (action === 'pull') {
    const since = params.get('since') || '0';
    targetUrl = `${streamApiBaseUrl}/api/sync/pull?since=${encodeURIComponent(since)}`;
    method = 'GET';
  } else if (action === 'push') {
    targetUrl = `${streamApiBaseUrl}/api/sync/push`;
    method = 'POST';
  } else {
    sendJson(res, 400, { success: false, message: 'Unsupported sync action' });
    return;
  }

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
      method,
      headers,
      signal: AbortSignal.timeout(15_000),
    };

    if (method === 'POST') {
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
      message: err?.message || 'Failed to connect to the sync server',
    });
  }
}
