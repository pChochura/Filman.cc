const CACHE_TTL_MS = 5 * 60 * 1000;
const ERROR_CACHE_TTL_MS = 5 * 1000;
const MAX_CACHE_SIZE = 100;
const SESSION_PREFIX = 'filman_stream_cache_v1_';

const memoryCache = new Map();
const inFlightControllers = new Map();
const subscribers = new Map();
let generation = 0;

export const isStreamApiConfigured = Boolean(
  typeof import.meta !== 'undefined' && import.meta.env
    ? import.meta.env.VITE_STREAM_API_CONFIGURED
    : false,
);

export function linkKey(tmdbId, season = null, episode = null) {
  if (season !== null && season !== undefined && episode !== null && episode !== undefined) {
    return `show:${tmdbId}:${season}:${episode}`;
  }
  return `movie:${tmdbId}`;
}

export function wakeUpStreamApi() {
  if (!isStreamApiConfigured) return;
  fetch('/api/stream?health=1').catch(() => {});
}

function toExtractedVideo(stream) {
  if (!stream || typeof stream.url !== 'string' || !/^https?:\/\//i.test(stream.url)) {
    return null;
  }
  const subtitles = (Array.isArray(stream.subtitles) ? stream.subtitles : [])
    .filter((sub) => sub && typeof sub.url === 'string' && /^https?:\/\//i.test(sub.url))
    .map((sub) => ({
      url: sub.url,
      label: sub.label || sub.lang || '',
      language: sub.lang || '',
    }));

  const serverName = stream.name || stream.provider || '';
  return {
    url: stream.url,
    serverName: serverName || 'Stream API',
    version: '',
    sourceWebsite: 'stream_api',
    quality: stream.quality || '',
    headers: stream.headers && typeof stream.headers === 'object' ? stream.headers : {},
    subtitles,
  };
}

function readDiskCache(key) {
  try {
    if (typeof window === 'undefined' || !window.sessionStorage) return null;
    const raw = window.sessionStorage.getItem(`${SESSION_PREFIX}${key}`);
    if (!raw) return null;
    const parsed = JSON.parse(raw);
    const now = Date.now();
    if (!parsed || !parsed.timestamp || now < parsed.timestamp || now - parsed.timestamp > CACHE_TTL_MS) {
      window.sessionStorage.removeItem(`${SESSION_PREFIX}${key}`);
      return null;
    }
    return parsed.response;
  } catch {
    return null;
  }
}

function writeDiskCache(key, response) {
  try {
    if (typeof window === 'undefined' || !window.sessionStorage) return;
    window.sessionStorage.setItem(
      `${SESSION_PREFIX}${key}`,
      JSON.stringify({ timestamp: Date.now(), response }),
    );
  } catch {
    // ignore storage quota errors
  }
}

function removeDiskCache(key) {
  try {
    if (typeof window !== 'undefined' && window.sessionStorage) {
      window.sessionStorage.removeItem(`${SESSION_PREFIX}${key}`);
    }
  } catch {
    // ignore
  }
}

export function getCachedLinks(key) {
  const entry = memoryCache.get(key);
  if (!entry) {
    const fromDisk = readDiskCache(key);
    if (fromDisk) {
      memoryCache.set(key, { timestamp: Date.now(), response: fromDisk });
      return fromDisk;
    }
    return null;
  }
  const ttl = entry.response.status === 'ERROR' ? ERROR_CACHE_TTL_MS : CACHE_TTL_MS;
  const now = Date.now();
  if ((now < entry.timestamp || now - entry.timestamp > ttl) && !inFlightControllers.has(key)) {
    memoryCache.delete(key);
    return null;
  }
  return entry.response;
}

function notifySubscribers(key, response) {
  const subs = subscribers.get(key);
  if (!subs) return;
  for (const cb of subs) {
    try {
      cb(response);
    } catch {
      // ignore subscriber errors
    }
  }
}

export function subscribeStreamKey(key, callback) {
  if (!subscribers.has(key)) subscribers.set(key, new Set());
  subscribers.get(key).add(callback);
  return () => {
    const set = subscribers.get(key);
    if (set) {
      set.delete(callback);
      if (set.size === 0) subscribers.delete(key);
    }
  };
}

function evictIfNeeded() {
  if (memoryCache.size < MAX_CACHE_SIZE) return;
  let oldestKey = null;
  let oldestTs = Infinity;
  for (const [k, v] of memoryCache.entries()) {
    if (!inFlightControllers.has(k) && v.timestamp < oldestTs) {
      oldestTs = v.timestamp;
      oldestKey = k;
    }
  }
  if (oldestKey) memoryCache.delete(oldestKey);
}

export function startExtraction({ tmdbId, isTvShow = false, season = null, episode = null, force = false }) {
  const key = linkKey(tmdbId, isTvShow ? season : null, isTvShow ? episode : null);
  if (!isStreamApiConfigured) {
    return { status: 'ERROR', links: [], message: 'STREAM_API_BASE_URL not configured' };
  }

  if (!force) {
    const existing = getCachedLinks(key);
    if (existing) return existing;
  } else {
    removeDiskCache(key);
    memoryCache.delete(key);
  }

  if (inFlightControllers.has(key)) {
    return memoryCache.get(key)?.response || { status: 'PENDING', links: [] };
  }

  evictIfNeeded();
  const reqGen = generation;
  const controller = new AbortController();
  inFlightControllers.set(key, controller);

  const pending = { status: 'PENDING', links: [] };
  memoryCache.set(key, { timestamp: Date.now(), response: pending });

  const streamPath = isTvShow
    ? `series/${tmdbId}?season=${season}&episode=${episode}&stream=1`
    : `movie/${tmdbId}?stream=1`;
  const jsonPath = isTvShow
    ? `series/${tmdbId}?season=${season}&episode=${episode}`
    : `movie/${tmdbId}`;

  const sseEndpoint = `/api/stream?path=${encodeURIComponent(streamPath)}`;
  const fallbackEndpoint = `/api/stream?path=${encodeURIComponent(jsonPath)}`;

  (async () => {
    const links = [];
    const updatePartial = (nextLinks) => {
      if (reqGen !== generation) return;
      const resp = { status: 'PARTIAL', links: [...nextLinks] };
      memoryCache.set(key, { timestamp: Date.now(), response: resp });
      notifySubscribers(key, resp);
    };

    try {
      const res = await fetch(sseEndpoint, {
        headers: { Accept: 'text/event-stream' },
        signal: controller.signal,
      });
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      const contentType = res.headers.get('content-type') || '';

      if (contentType.includes('application/json')) {
        const json = await res.json();
        const mapped = (json.streams || []).map(toExtractedVideo).filter(Boolean);
        links.push(...mapped);
      } else if (res.body) {
        const reader = res.body.getReader();
        const decoder = new TextDecoder();
        let buffer = '';
        let eventName = '';
        let dataLines = [];
        let complete = false;

        const dispatchEvent = () => {
          if (dataLines.length === 0) {
            eventName = '';
            return;
          }
          const payload = dataLines.join('\n');
          dataLines = [];
          const ev = eventName;
          eventName = '';
          try {
            const parsed = JSON.parse(payload);
            if ((ev === 'provider' || !ev) && Array.isArray(parsed.streams)) {
              if (parsed.success !== false) {
                const mapped = parsed.streams.map(toExtractedVideo).filter(Boolean);
                if (mapped.length > 0) {
                  links.push(...mapped);
                  updatePartial(links);
                }
              }
            } else if (ev === 'complete') {
              complete = true;
            } else if (ev === 'error') {
              throw new Error(parsed.error || 'Stream API error');
            }
          } catch (parseErr) {
            if (ev === 'error') throw parseErr;
          }
        };

        while (!complete) {
          const { done, value } = await reader.read();
          if (done) {
            if (buffer.trim()) {
              for (const rem of buffer.split(/\r?\n/)) {
                if (rem.startsWith('event:')) eventName = rem.slice(6).trim();
                else if (rem.startsWith('data:')) dataLines.push(rem.slice(5).trimStart());
              }
            }
            dispatchEvent();
            break;
          }
          buffer += decoder.decode(value, { stream: true });
          const lines = buffer.split(/\r?\n/);
          buffer = lines.pop() ?? '';
          for (const line of lines) {
            if (line === '') {
              dispatchEvent();
            } else if (!line.startsWith(':')) {
              if (line.startsWith('event:')) {
                eventName = line.slice(6).trim();
              } else if (line.startsWith('data:')) {
                dataLines.push(line.slice(5).trimStart());
              }
            }
          }
        }
      }

      if (reqGen !== generation) return;
      const finalResp = {
        status: links.length === 0 ? 'ERROR' : 'READY',
        links,
        message: links.length === 0 ? 'Stream API returned no streams' : null,
      };
      if (finalResp.status === 'READY') {
        writeDiskCache(key, finalResp);
      }
      memoryCache.set(key, { timestamp: Date.now(), response: finalResp });
      notifySubscribers(key, finalResp);
    } catch (err) {
      if (err?.name === 'AbortError' || reqGen !== generation) return;

      // Fallback to non-streaming JSON endpoint if SSE fails before yielding streams
      if (links.length === 0) {
        try {
          const fbRes = await fetch(fallbackEndpoint, { signal: controller.signal });
          if (fbRes.ok) {
            const json = await fbRes.json();
            const mapped = (json.streams || []).map(toExtractedVideo).filter(Boolean);
            if (mapped.length > 0) {
              const fbResp = { status: 'READY', links: mapped, message: null };
              writeDiskCache(key, fbResp);
              memoryCache.set(key, { timestamp: Date.now(), response: fbResp });
              notifySubscribers(key, fbResp);
              return;
            }
          }
        } catch {
          // fall through to error
        }
      }

      const errResp = {
        status: 'ERROR',
        links,
        message: err?.message || 'Stream API failed',
      };
      memoryCache.set(key, { timestamp: Date.now(), response: errResp });
      notifySubscribers(key, errResp);
    } finally {
      inFlightControllers.delete(key);
    }
  })();

  return pending;
}

export function clearStreamCache() {
  generation++;
  for (const ctrl of inFlightControllers.values()) {
    ctrl.abort();
  }
  inFlightControllers.clear();
  memoryCache.clear();
  try {
    if (typeof window !== 'undefined' && window.sessionStorage) {
      const toRemove = [];
      for (let i = 0; i < window.sessionStorage.length; i++) {
        const k = window.sessionStorage.key(i);
        if (k && k.startsWith(SESSION_PREFIX)) toRemove.push(k);
      }
      for (const k of toRemove) window.sessionStorage.removeItem(k);
    }
  } catch {
    // ignore
  }
}
