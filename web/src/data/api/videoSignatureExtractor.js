const GENERIC_NAMES = new Set([
  'index', 'master', 'playlist', 'video', 'stream', 'play', 'hls', 'output',
  'manifest', 'chunk', 'segment', 'track', 'default', 'source', 'download',
  'embed', 'player', 'movie', 'tv', 'media', 'file',
]);

const VIDEO_EXTENSIONS = ['mp4', 'mkv', 'avi', 'webm', 'm4v', 'mov', 'ts', 'm3u8', 'mpd'];

const STOP_WORDS = new Set([
  'the', 'a', 'an', 'and', 'or', 'in', 'of', 'to', 'for', 'with',
  'pl', 'pol', 'polish', 'polski', 'napisy', 'srt', 'sub', 'vtt',
  'lektor', 'dubbing', 'audio', 'video', 'multisubs', 'multi', '264', '265',
]);

const RELEASE_TAG_REGEX =
  /(1080p|720p|2160p|4k|480p|web-?dl|web-?rip|bluray|bdrip|brrip|hdtv|dvdrip|x264|x265|hevc|h264|h265|ddp5\.1|aac|yts|flux|cmrg|evo|sparks|rarbg)/i;

export function tokenizeSignature(name) {
  const tokens = String(name || '')
    .split(/[^a-zA-Z0-9]+/)
    .map((s) => s.toLowerCase())
    .filter((s) => s.length >= 2 && !STOP_WORDS.has(s));
  return new Set(tokens);
}

export function jaccardSimilarity(a, b) {
  if (!a || !b || a.size === 0 || b.size === 0) return 0;
  let intersection = 0;
  for (const v of a) {
    if (b.has(v)) intersection++;
  }
  const union = a.size + b.size - intersection;
  return union === 0 ? 0 : intersection / union;
}

export function cleanFilename(name) {
  let result = String(name || '').split('?')[0].replace(/^["'\s]+|["'\s]+$/g, '');
  const lower = result.toLowerCase();
  for (const ext of VIDEO_EXTENSIONS) {
    if (lower.endsWith(`.${ext}`)) {
      result = result.slice(0, result.length - ext.length - 1);
      break;
    }
  }
  return result.replace(/^["'\s]+|["'\s]+$/g, '');
}

function isGenericName(name) {
  const base = cleanFilename(name).toLowerCase();
  return (
    GENERIC_NAMES.has(base) ||
    /^[0-9a-f]{16,}$/.test(base) ||
    /^[0-9a-f-]{32,}$/.test(base) ||
    /^[0-9]+$/.test(base)
  );
}

function safeDecode(val) {
  try {
    return decodeURIComponent(val);
  } catch {
    return val;
  }
}

function extractQueryParam(url, keyToFind) {
  const qIdx = url.indexOf('?');
  if (qIdx === -1) return null;
  const query = url.slice(qIdx + 1);
  for (const param of query.split('&')) {
    const eq = param.indexOf('=');
    const k = (eq === -1 ? param : param.slice(0, eq)).toLowerCase();
    if (k === keyToFind.toLowerCase()) {
      return eq === -1 ? '' : param.slice(eq + 1);
    }
  }
  return null;
}

function unwrapProxyUrl(url) {
  if (url.includes('/m3u8-proxy') || url.includes('/ts-proxy') || url.includes('/api/proxy')) {
    const nested = extractQueryParam(url, 'url');
    if (nested && nested.trim()) {
      return safeDecode(nested);
    }
  }
  return url;
}

function extractFilenameFromContentDisposition(header) {
  const match = /filename\*?=['"]?(?:UTF-\d['"]*)?([^'";\n]+)['"]?/i.exec(header);
  return match ? match[1].replace(/^["'\s]+|["'\s]+$/g, '') : null;
}

function extractFilenameFromQueryParams(url) {
  const qIdx = url.indexOf('?');
  if (qIdx === -1) return null;
  const query = url.slice(qIdx + 1);
  const targetKeys = new Set(['title', 'filename', 'file', 'name', 'release']);
  for (const param of query.split('&')) {
    const eq = param.indexOf('=');
    const key = (eq === -1 ? param : param.slice(0, eq)).toLowerCase();
    const value = eq === -1 ? '' : param.slice(eq + 1);
    if (targetKeys.has(key) && value.trim()) {
      const decoded = safeDecode(value);
      if (decoded.length >= 6) return decoded;
    }
  }
  return null;
}

const EPISODE_REGEX = /[sS](\d{1,2})[eE](\d{1,2})|(\d{1,2})x(\d{1,2})/;
const YEAR_REGEX = /\b(19\d\d|20\d\d)\b/;

export function extractSeasonEpisode(text) {
  if (!text) return null;
  const match = String(text).match(EPISODE_REGEX);
  if (!match) return null;
  const s = parseInt(match[1] || match[3], 10);
  const e = parseInt(match[2] || match[4], 10);
  if (isNaN(s) || isNaN(e)) return null;
  return { season: s, episode: e };
}

export function extractYear(text) {
  if (!text) return null;
  const match = String(text).match(YEAR_REGEX);
  if (!match) return null;
  const y = parseInt(match[1], 10);
  return isNaN(y) ? null : y;
}

export function isEpisodeMismatched(text, expectedSeason, expectedEpisode) {
  if (expectedSeason == null && expectedEpisode == null) return false;
  const se = extractSeasonEpisode(text);
  if (!se) return false;
  if (expectedSeason != null && se.season !== expectedSeason) return true;
  if (expectedEpisode != null && se.episode !== expectedEpisode) return true;
  return false;
}

export function computeConfidence(text, expectedTitle, expectedYear) {
  if (!text || !String(text).trim()) return 0;
  const year = extractYear(text);
  if (expectedYear != null && year != null && Math.abs(expectedYear - year) > 1) {
    return -50;
  }
  if (expectedTitle && String(expectedTitle).trim()) {
    const candTokens = tokenizeSignature(text);
    const targetTokens = tokenizeSignature(expectedTitle);
    if (targetTokens.size > 0 && candTokens.size > 0) {
      const sim = jaccardSimilarity(candTokens, targetTokens);
      if (sim >= 0.35) return 10;
      if (sim < 0.1 && candTokens.size >= 2) return -20;
    }
  }
  return 0;
}

export function extractVideoSignature({
  videoUrl,
  headers = {},
  serverName = null,
  quality = null,
  filename = null,
}) {
  if (filename && String(filename).trim()) {
    const clean = cleanFilename(filename);
    if (clean && !isGenericName(clean) && clean.length >= 8) {
      return clean;
    }
  }

  if (!videoUrl || !String(videoUrl).trim()) return null;
  const targetUrl = unwrapProxyUrl(videoUrl);

  const cdEntry = Object.entries(headers || {}).find(
    ([k]) => k.toLowerCase() === 'content-disposition',
  );
  if (cdEntry && cdEntry[1]) {
    const fn = extractFilenameFromContentDisposition(cdEntry[1]);
    if (fn && !isGenericName(fn)) return cleanFilename(fn);
  }

  const queryFn = extractFilenameFromQueryParams(targetUrl);
  if (queryFn && !isGenericName(queryFn)) return cleanFilename(queryFn);

  let path = targetUrl;
  const schemeIdx = targetUrl.indexOf('://');
  if (schemeIdx !== -1) {
    const afterScheme = targetUrl.slice(schemeIdx + 3);
    const slashIdx = afterScheme.indexOf('/');
    path = slashIdx === -1 ? '' : afterScheme.slice(slashIdx + 1).split('?')[0];
  } else {
    path = targetUrl.split('?')[0];
  }

  const segments = path.split('/').filter(Boolean);
  for (let i = segments.length - 1; i >= 0; i--) {
    const cleaned = cleanFilename(safeDecode(segments[i]));
    if (cleaned && !isGenericName(cleaned) && RELEASE_TAG_REGEX.test(cleaned) && cleaned.length >= 8) {
      return cleaned;
    }
  }

  for (let i = segments.length - 1; i >= 0; i--) {
    const cleaned = cleanFilename(safeDecode(segments[i]));
    if (cleaned && !isGenericName(cleaned) && cleaned.length >= 8) {
      return cleaned;
    }
  }

  if (serverName && String(serverName).trim()) {
    if (serverName.includes('\n')) {
      for (const line of serverName.split('\n')) {
        const cleanLine = cleanFilename(line);
        if (cleanLine && !isGenericName(cleanLine) && cleanLine.length >= 8) {
          if (RELEASE_TAG_REGEX.test(cleanLine)) {
            return cleanLine;
          }
        }
      }
    }
    if (quality && RELEASE_TAG_REGEX.test(serverName)) {
      return `${serverName}.${quality}`;
    }
    if (RELEASE_TAG_REGEX.test(serverName)) {
      return serverName;
    }
  }

  return null;
}
