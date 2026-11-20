import { settingsStore } from '../local/settingsStore.js';
import { parseMediaRef } from '../models/mediaRef.js';
import {
  getCachedLinks,
  linkKey,
  startExtraction,
  subscribeStreamKey,
  wakeUpStreamApi,
} from './streamApiClient.js';

function qualityRank(quality, version, preferred) {
  const pref = String(preferred || 'auto').toLowerCase();
  if (
    pref === 'auto' ||
    String(quality || '').toLowerCase().includes(pref) ||
    String(version || '').toLowerCase().includes(pref)
  ) {
    return 0;
  }
  return 1;
}

export function sortVideos(videos, preferredQuality = settingsStore.get().preferredQuality) {
  if (!Array.isArray(videos)) return [];
  return [...videos].sort(
    (a, b) =>
      qualityRank(a.quality, a.version, preferredQuality) -
      qualityRank(b.quality, b.version, preferredQuality),
  );
}

export function findBestSource(sources, targetSettings = null) {
  if (!Array.isArray(sources) || sources.length === 0) return null;

  if (targetSettings) {
    const targetServer = (targetSettings.serverName || '').toLowerCase();
    const targetSite = (targetSettings.sourceWebsite || '').toLowerCase();

    const fullMatch = sources.find((video) => {
      const vServer = (video.serverName || '').toLowerCase();
      const vSite = (video.sourceWebsite || '').toLowerCase();
      const serverMatches =
        !targetServer || vServer.includes(targetServer) || targetServer.includes(vServer);
      const siteMatches = !targetSite || vSite === targetSite;
      return serverMatches && siteMatches;
    });
    if (fullMatch) return fullMatch;

    if (targetSite) {
      const siteMatch = sources.find(
        (video) => (video.sourceWebsite || '').toLowerCase() === targetSite,
      );
      if (siteMatch) return siteMatch;
    }
  }

  return sources[0] || null;
}

export function isEmbedUrl(url) {
  if (!url) return false;
  const lower = String(url).toLowerCase();
  return (
    lower.includes('ebd.cda.pl') ||
    lower.includes('cda.pl/video') ||
    lower.includes('/embed/') ||
    lower.includes('/v/')
  );
}

export function extractYoutubeVideoId(url) {
  if (!url) return null;
  try {
    const u = new URL(url);
    const host = u.hostname.toLowerCase();
    if (host.includes('youtu.be')) {
      return u.pathname.replace(/^\/+/, '').split('/')[0] || null;
    }
    if (host.includes('youtube.com') || host.includes('youtube-nocookie.com')) {
      if (u.searchParams.get('v')) return u.searchParams.get('v');
      const m = /\/(?:embed|shorts|v)\/([^/?]+)/.exec(u.pathname);
      if (m) return m[1];
    }
  } catch {
    // ignore
  }
  return null;
}

export function prefetchStreamsForMediaUrl(mediaUrl) {
  const ref = parseMediaRef(mediaUrl);
  if (!ref) return;
  wakeUpStreamApi();
  startExtraction({
    tmdbId: ref.tmdbId,
    isTvShow: ref.isTvShow,
    season: ref.season,
    episode: ref.episode,
  });
}

export function resolveStreamsWithUpdates({
  mediaUrl,
  onSourcesUpdate,
  onSettled,
}) {
  const ref = parseMediaRef(mediaUrl);
  if (!ref) {
    onSettled?.({ status: 'ERROR', links: [], message: 'Invalid media reference' });
    return () => {};
  }

  const key = linkKey(ref.tmdbId, ref.isTvShow ? ref.season : null, ref.isTvShow ? ref.episode : null);
  let active = true;

  const handleResponse = (resp) => {
    if (!active || !resp) return;
    const sorted = sortVideos(resp.links || []);
    onSourcesUpdate?.(sorted, resp.status, resp.message || null);
    if (resp.status === 'READY' || resp.status === 'ERROR') {
      onSettled?.({ ...resp, links: sorted });
    }
  };

  const unsubscribe = subscribeStreamKey(key, handleResponse);
  const initial = startExtraction({
    tmdbId: ref.tmdbId,
    isTvShow: ref.isTvShow,
    season: ref.season,
    episode: ref.episode,
  });

  if (initial.links?.length > 0 || initial.status === 'READY' || initial.status === 'ERROR') {
    handleResponse(initial);
  }

  return () => {
    active = false;
    unsubscribe();
  };
}

export function getSortedCachedSources(mediaUrl) {
  const ref = parseMediaRef(mediaUrl);
  if (!ref) return [];
  const key = linkKey(ref.tmdbId, ref.isTvShow ? ref.season : null, ref.isTvShow ? ref.episode : null);
  const cached = getCachedLinks(key);
  return sortVideos(cached?.links || []);
}
