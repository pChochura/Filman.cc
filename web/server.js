import http from 'node:http';
import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import { fileURLToPath } from 'node:url';
import { loadServerConfig, sendJson } from './server/env.js';
import { handleStreamRequest } from './server/streamHandler.js';
import { handleProxyRequest } from './server/proxyHandler.js';
import { handleSyncRequest } from './server/syncHandler.js';
import { handleSubtitlesRequest } from './server/subtitlesHandler.js';
import { handleTtsRequest } from './server/ttsHandler.js';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const DIST_DIR = path.resolve(__dirname, 'dist');

const MIME_TYPES = {
  '.html': 'text/html; charset=utf-8',
  '.js': 'application/javascript; charset=utf-8',
  '.mjs': 'application/javascript; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.png': 'image/png',
  '.jpg': 'image/jpeg',
  '.jpeg': 'image/jpeg',
  '.gif': 'image/gif',
  '.webp': 'image/webp',
  '.svg': 'image/svg+xml',
  '.ico': 'image/x-icon',
  '.woff': 'font/woff',
  '.woff2': 'font/woff2',
  '.ttf': 'font/ttf',
  '.txt': 'text/plain; charset=utf-8',
};

const authCache = new Map();
const AUTH_CACHE_TTL_MS = 5 * 60 * 1000;
const NEG_AUTH_CACHE_TTL_MS = 3 * 1000;
const COOKIE_NAME = 'tiwi_session_auth';
const COOKIE_MAX_AGE_SEC = 30 * 24 * 60 * 60; // 30 days

function getCookieSecret(config) {
  return config.streamApiKey || config.routerAuthPass || process.env.ROUTER_AUTH_PASS || 'tiwi_persistent_auth_secret_key';
}

function createSessionCookie(payload, secret) {
  const data = JSON.stringify({
    ...payload,
    exp: Date.now() + COOKIE_MAX_AGE_SEC * 1000,
  });
  const b64 = Buffer.from(data, 'utf8').toString('base64url');
  const sig = crypto.createHmac('sha256', secret).update(b64).digest('base64url');
  return `${b64}.${sig}`;
}

function verifySessionCookie(cookieValue, secret) {
  if (!cookieValue || typeof cookieValue !== 'string') return null;
  const dotIdx = cookieValue.lastIndexOf('.');
  if (dotIdx <= 0) return null;

  const b64 = cookieValue.slice(0, dotIdx);
  const sig = cookieValue.slice(dotIdx + 1);

  const expectedSig = crypto.createHmac('sha256', secret).update(b64).digest('base64url');
  try {
    const sigBuf = Buffer.from(sig);
    const expectedBuf = Buffer.from(expectedSig);
    if (sigBuf.length !== expectedBuf.length || !crypto.timingSafeEqual(sigBuf, expectedBuf)) {
      return null;
    }
    const dataStr = Buffer.from(b64, 'base64url').toString('utf8');
    const data = JSON.parse(dataStr);
    if (data.exp && Date.now() > data.exp) {
      return null;
    }
    return data;
  } catch {
    return null;
  }
}

function parseCookies(req) {
  const list = {};
  const rc = req.headers.cookie;
  if (!rc) return list;

  rc.split(';').forEach((cookie) => {
    const parts = cookie.split('=');
    const name = parts.shift().trim();
    if (name) {
      list[name] = decodeURIComponent(parts.join('='));
    }
  });
  return list;
}

function attachAuthCookie(res, payload, secret) {
  const cookieVal = createSessionCookie(payload, secret);
  res.setHeader(
    'Set-Cookie',
    `${COOKIE_NAME}=${cookieVal}; Path=/; Max-Age=${COOKIE_MAX_AGE_SEC}; SameSite=Lax; HttpOnly`
  );
}

async function authenticateRequest(req, res, config) {
  const authUser = config.routerAuthUser || process.env.ROUTER_AUTH_USER;
  const authPass = config.routerAuthPass || process.env.ROUTER_AUTH_PASS;
  const authToken = config.routerAuthToken || process.env.ROUTER_AUTH_TOKEN;
  const secret = getCookieSecret(config);

  // If no auth is configured and no stream API is configured, allow all traffic
  if (!authUser && !authPass && !authToken && !config.streamApiBaseUrl) {
    return true;
  }

  // 1. Check persistent session cookie (keeps users logged in across reloads & tab restarts)
  const cookies = parseCookies(req);
  const cookieSession = cookies[COOKIE_NAME]
    ? verifySessionCookie(cookies[COOKIE_NAME], secret)
    : null;

  const authHeader = req.headers['authorization'] || '';

  // If valid cookie exists and no explicit Basic Auth was sent, grant access immediately
  if (cookieSession && !authHeader) {
    req.userSession = cookieSession.session || null;
    req.authSource = 'cookie';
    return true;
  }

  // 2. Check Bearer / Token Auth
  if (authToken) {
    if (authHeader.startsWith('Bearer ')) {
      const token = authHeader.slice(7).trim();
      if (token === authToken) return true;
    }
    const url = new URL(req.url || '/', 'http://localhost');
    if (url.searchParams.get('token') === authToken) {
      return true;
    }
  }

  // 3. Extract credentials from Basic Auth or ?auth=user:pass query parameter
  let user = '';
  let pass = '';

  if (authHeader.startsWith('Basic ')) {
    const b64 = authHeader.slice(6).trim();
    try {
      const decoded = Buffer.from(b64, 'base64').toString('utf8');
      const colonIdx = decoded.indexOf(':');
      if (colonIdx > 0) {
        user = decoded.slice(0, colonIdx);
        pass = decoded.slice(colonIdx + 1);
      }
    } catch {
      return false;
    }
  } else {
    // Check ?auth=username:password query param (useful for one-click bookmark login)
    const url = new URL(req.url || '/', 'http://localhost');
    const queryAuth = url.searchParams.get('auth');
    if (queryAuth && queryAuth.includes(':')) {
      const colonIdx = queryAuth.indexOf(':');
      user = queryAuth.slice(0, colonIdx);
      pass = queryAuth.slice(colonIdx + 1);
    }
  }

  // If cookie was already verified and matches current user, continue
  if (cookieSession && (!user || cookieSession.user === user)) {
    req.userSession = cookieSession.session || null;
    req.authSource = 'cookie';
    return true;
  }

  if (!user || !pass) {
    return false;
  }

  // Check memory cache first
  const cacheKey = `${user}:${pass}`;
  const cached = authCache.get(cacheKey);
  const now = Date.now();
  if (cached) {
    if (now < cached.expiresAt) {
      if (!cached.ok) return false;
      req.userSession = cached.session || null;
      attachAuthCookie(res, { user, session: req.userSession }, secret);
      return true;
    }
    authCache.delete(cacheKey);
  }

  const isAdmin = authUser && authPass && user === authUser && pass === authPass;

  // Verify against app user accounts via Auth API
  if (config.streamApiBaseUrl) {
    try {
      const loginUrl = `${config.streamApiBaseUrl}/api/auth/login`;
      const headers = {
        'Content-Type': 'application/json',
        'Accept': 'application/json',
      };
      if (config.streamApiKey) {
        headers['X-Api-Key'] = config.streamApiKey;
      }

      const authRes = await fetch(loginUrl, {
        method: 'POST',
        headers,
        body: JSON.stringify({ username: user, password: pass }),
      });

      if (authRes.ok) {
        const data = await authRes.json();
        if (data && data.success !== false && data.token) {
          const session = {
            token: data.token,
            userId: data.user?.id || null,
            username: data.user?.username || user,
          };
          req.userSession = session;
          authCache.set(cacheKey, {
            ok: true,
            session,
            expiresAt: now + AUTH_CACHE_TTL_MS,
          });
          attachAuthCookie(res, { user, session }, secret);
          return true;
        }
      }
    } catch (err) {
      console.error('[Tiwi Router] Auth API check failed:', err.message);
    }
  }

  // Master/admin credentials check
  if (isAdmin) {
    req.userSession = null;
    authCache.set(cacheKey, {
      ok: true,
      session: null,
      expiresAt: now + AUTH_CACHE_TTL_MS,
    });
    attachAuthCookie(res, { user: 'admin', session: null }, secret);
    return true;
  }

  // Authentication failed, cache negative result briefly
  authCache.set(cacheKey, {
    ok: false,
    session: null,
    expiresAt: now + NEG_AUTH_CACHE_TTL_MS,
  });
  return false;
}

function handleStatic(req, res, pathname) {
  let safePath = path.normalize(pathname).replace(/^(\.\.[/\\])+/, '');
  let filePath = path.join(DIST_DIR, safePath);

  // If pointing to a directory, check for index.html
  if (fs.existsSync(filePath) && fs.statSync(filePath).isDirectory()) {
    filePath = path.join(filePath, 'index.html');
  }

  // If file doesn't exist, fallback to SPA index.html for GET navigation
  if (!fs.existsSync(filePath) || !fs.statSync(filePath).isFile()) {
    if (req.method === 'GET' && !pathname.startsWith('/api/')) {
      filePath = path.join(DIST_DIR, 'index.html');
    } else {
      res.statusCode = 404;
      res.end('Not Found');
      return;
    }
  }

  if (!fs.existsSync(filePath)) {
    res.statusCode = 500;
    res.end('Build artifacts not found. Please run "npm run build" first.');
    return;
  }

  const ext = path.extname(filePath).toLowerCase();
  const contentType = MIME_TYPES[ext] || 'application/octet-stream';

  // Serve SPA index.html with runtime environment and user session injected dynamically
  if (path.basename(filePath) === 'index.html') {
    try {
      const html = fs.readFileSync(filePath, 'utf8');
      const config = loadServerConfig(process.cwd());
      const envPayload = {
        VITE_TMDB_API_KEY: config.tmdbApiKey || '',
        VITE_STREAM_API_CONFIGURED: config.streamApiBaseUrl ? 'true' : '',
      };
      const sessionPayload = req.userSession || null;
      const envScript = `<script>window.__ENV__=${JSON.stringify(envPayload)};window.__INITIAL_SESSION__=${JSON.stringify(sessionPayload)};</script>`;
      const outputHtml = html.includes('</head>')
        ? html.replace('</head>', `${envScript}</head>`)
        : `${envScript}${html}`;

      res.statusCode = 200;
      res.setHeader('Content-Type', 'text/html; charset=utf-8');
      res.setHeader('Cache-Control', 'no-cache');
      res.end(outputHtml);
      return;
    } catch {
      // Fallback to streaming if reading fails
    }
  }

  res.statusCode = 200;
  res.setHeader('Content-Type', contentType);

  // Cache static assets aggressively, keep index.html fresh
  if (filePath.includes('/assets/')) {
    res.setHeader('Cache-Control', 'public, max-age=31536000, immutable');
  } else {
    res.setHeader('Cache-Control', 'no-cache');
  }

  const stream = fs.createReadStream(filePath);
  stream.pipe(res);
}

const server = http.createServer(async (req, res) => {
  // Allow OPTIONS preflights immediately
  if (req.method === 'OPTIONS') {
    res.statusCode = 204;
    res.setHeader('Access-Control-Allow-Origin', '*');
    res.setHeader('Access-Control-Allow-Methods', 'GET, POST, HEAD, OPTIONS');
    res.setHeader('Access-Control-Allow-Headers', '*');
    res.end();
    return;
  }

  const config = loadServerConfig(process.cwd());

  // Enforce HTTP Router Authentication
  const isAuthed = await authenticateRequest(req, res, config);
  if (!isAuthed) {
    res.statusCode = 401;
    res.setHeader('WWW-Authenticate', 'Basic realm="Tiwi Web Authenticated Router"');
    res.setHeader('Content-Type', 'text/plain; charset=utf-8');
    res.end('401 Unauthorized: Authentication required');
    return;
  }

  const url = new URL(req.url || '/', `http://${req.headers.host || 'localhost'}`);
  const pathname = url.pathname;

  // Logout endpoint to clear persistent authentication cookie
  if (pathname === '/api/auth/logout' || pathname === '/logout') {
    res.statusCode = 200;
    res.setHeader(
      'Set-Cookie',
      `${COOKIE_NAME}=; Path=/; Expires=Thu, 01 Jan 1970 00:00:00 GMT; Max-Age=0; HttpOnly; SameSite=Lax`
    );
    if (pathname === '/logout') {
      res.setHeader('Content-Type', 'text/html; charset=utf-8');
      res.end('<!DOCTYPE html><html><body style="background:#000;color:#fff;font-family:sans-serif;text-align:center;padding:50px;"><h2>Logged out</h2><p><a href="/" style="color:#3b82f6;">Log in again</a></p></body></html>');
    } else {
      sendJson(res, 200, { success: true, message: 'Logged out successfully' });
    }
    return;
  }

  // Runtime environment config for frontend SPA
  if (pathname === '/env.js') {
    const envPayload = {
      VITE_TMDB_API_KEY: config.tmdbApiKey || '',
      VITE_STREAM_API_CONFIGURED: config.streamApiBaseUrl ? 'true' : '',
    };
    const sessionPayload = req.userSession || null;
    res.statusCode = 200;
    res.setHeader('Content-Type', 'application/javascript; charset=utf-8');
    res.setHeader('Cache-Control', 'no-cache');
    res.end(
      `window.__ENV__ = ${JSON.stringify(envPayload)};\nwindow.__INITIAL_SESSION__ = ${JSON.stringify(sessionPayload)};`
    );
    return;
  }

  // Health probe endpoint for monitoring
  if (pathname === '/healthz' || pathname === '/health') {
    sendJson(res, 200, { status: 'ok', service: 'tiwi-web-router' });
    return;
  }

  // API routing
  if (pathname === '/api/stream' || pathname.startsWith('/api/stream/')) {
    return handleStreamRequest(req, res);
  }
  if (pathname === '/api/proxy' || pathname.startsWith('/api/proxy/')) {
    return handleProxyRequest(req, res);
  }
  if (pathname === '/api/sync' || pathname.startsWith('/api/sync/')) {
    return handleSyncRequest(req, res);
  }
  if (pathname === '/api/subtitles' || pathname.startsWith('/api/subtitles/')) {
    return handleSubtitlesRequest(req, res);
  }
  if (pathname === '/api/tts' || pathname.startsWith('/api/tts/')) {
    return handleTtsRequest(req, res);
  }

  // Static files & SPA routing
  handleStatic(req, res, pathname);
});

const PORT = parseInt(process.env.PORT || '3000', 10);
const HOST = process.env.HOST || '0.0.0.0';

server.listen(PORT, HOST, () => {
  const config = loadServerConfig(process.cwd());
  console.log(`[Tiwi Web] Authenticated router running on http://${HOST}:${PORT}`);
  console.log(`[Tiwi Web] Stream API Target: ${config.streamApiBaseUrl || 'NOT CONFIGURED'}`);
  if (process.env.ROUTER_AUTH_USER) {
    console.log(`[Tiwi Web] Basic Auth: ENABLED (User: ${process.env.ROUTER_AUTH_USER})`);
  }
  if (process.env.ROUTER_AUTH_TOKEN) {
    console.log(`[Tiwi Web] Token Auth: ENABLED`);
  }
});
