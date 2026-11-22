import http from 'node:http';
import fs from 'node:fs';
import path from 'node:path';
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

function checkAuth(req) {
  const authUser = process.env.ROUTER_AUTH_USER;
  const authPass = process.env.ROUTER_AUTH_PASS;
  const authToken = process.env.ROUTER_AUTH_TOKEN;

  // If no auth is configured, allow all traffic
  if (!authUser && !authPass && !authToken) {
    return true;
  }

  const authHeader = req.headers['authorization'] || '';

  // Check Bearer / Token Auth
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

  // Check Basic Auth
  if (authUser && authPass) {
    if (authHeader.startsWith('Basic ')) {
      const b64 = authHeader.slice(6).trim();
      try {
        const decoded = Buffer.from(b64, 'base64').toString('utf8');
        const colonIdx = decoded.indexOf(':');
        if (colonIdx > 0) {
          const user = decoded.slice(0, colonIdx);
          const pass = decoded.slice(colonIdx + 1);
          if (user === authUser && pass === authPass) {
            return true;
          }
        }
      } catch {
        return false;
      }
    }
  }

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

  // Enforce HTTP Router Authentication
  if (!checkAuth(req)) {
    res.statusCode = 401;
    res.setHeader('WWW-Authenticate', 'Basic realm="Tiwi Web Authenticated Router"');
    res.setHeader('Content-Type', 'text/plain; charset=utf-8');
    res.end('401 Unauthorized: Authentication required');
    return;
  }

  const url = new URL(req.url || '/', `http://${req.headers.host || 'localhost'}`);
  const pathname = url.pathname;

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
