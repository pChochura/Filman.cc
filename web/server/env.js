import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const WEB_ROOT = path.resolve(__dirname, '..');
const REPO_ROOT = path.resolve(WEB_ROOT, '..');

function parseKeyValueFile(filePath) {
  const result = {};
  if (!fs.existsSync(filePath)) return result;
  try {
    const content = fs.readFileSync(filePath, 'utf8');
    for (const rawLine of content.split(/\r?\n/)) {
      const line = rawLine.trim();
      if (!line || line.startsWith('#') || line.startsWith('!')) continue;
      const eqIndex = line.indexOf('=');
      if (eqIndex <= 0) continue;
      const key = line.slice(0, eqIndex).trim();
      let value = line.slice(eqIndex + 1).trim();
      if (
        (value.startsWith('"') && value.endsWith('"')) ||
        (value.startsWith("'") && value.endsWith("'"))
      ) {
        value = value.slice(1, -1);
      }
      // Handle Java .properties backslash escapes like http\://
      value = value.replace(/\\:/g, ':').replace(/\\\\/g, '\\');
      result[key] = value;
    }
  } catch {
    // Ignore unreadable property files
  }
  return result;
}

let cachedLocalFiles = null;

function getLocalFileVars(cwd = WEB_ROOT) {
  if (process.env.VERCEL) return {};
  if (cachedLocalFiles) return cachedLocalFiles;
  const localProps = parseKeyValueFile(path.join(REPO_ROOT, 'local.properties'));
  const rootEnv = parseKeyValueFile(path.join(cwd, '.env'));
  const webEnv = parseKeyValueFile(path.join(WEB_ROOT, '.env'));
  const webEnvLocal = parseKeyValueFile(path.join(WEB_ROOT, '.env.local'));
  cachedLocalFiles = { ...localProps, ...rootEnv, ...webEnv, ...webEnvLocal };
  return cachedLocalFiles;
}

export function loadServerConfig(cwd = WEB_ROOT, overrides = {}) {
  const local = getLocalFileVars(cwd);
  const pick = (...keys) => {
    for (const key of keys) {
      const val = overrides[key] ?? process.env[key] ?? local[key];
      if (typeof val === 'string' && val.trim().length > 0) {
        return val.trim();
      }
    }
    return '';
  };

  const streamApiBaseUrl = pick('VITE_STREAM_API_BASE_URL', 'STREAM_API_BASE_URL').replace(/\/+$/, '');
  const streamApiKey = pick('STREAM_API_KEY', 'VITE_STREAM_API_KEY');
  const tmdbApiKey = pick('VITE_TMDB_API_KEY', 'TMDB_API_KEY');
  const openSubtitlesApiKey = pick('OPEN_SUBTITLES_API_KEY', 'VITE_OPEN_SUBTITLES_API_KEY');
  const wyzieSubsApiKey = pick('WYZIE_SUBS_API_KEY', 'VITE_WYZIE_SUBS_API_KEY');
  const routerAuthUser = pick('ROUTER_AUTH_USER');
  const routerAuthPass = pick('ROUTER_AUTH_PASS');
  const routerAuthToken = pick('ROUTER_AUTH_TOKEN');

  return {
    streamApiBaseUrl,
    streamApiKey,
    tmdbApiKey,
    openSubtitlesApiKey,
    wyzieSubsApiKey,
    routerAuthUser,
    routerAuthPass,
    routerAuthToken,
  };
}

export function readRequestBody(req) {
  if (req.body !== undefined && req.body !== null) {
    if (typeof req.body === 'string') return Promise.resolve(req.body);
    if (Buffer.isBuffer(req.body)) return Promise.resolve(req.body.toString('utf8'));
    return Promise.resolve(JSON.stringify(req.body));
  }
  return new Promise((resolve, reject) => {
    const chunks = [];
    req.on('data', (chunk) => chunks.push(chunk));
    req.on('end', () => resolve(Buffer.concat(chunks).toString('utf8')));
    req.on('error', reject);
  });
}

export function sendJson(res, statusCode, payload) {
  res.statusCode = statusCode;
  res.setHeader('Content-Type', 'application/json; charset=utf-8');
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.end(JSON.stringify(payload));
}
