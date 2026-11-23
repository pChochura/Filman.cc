import { settingsStore } from '../local/settingsStore.js';
import { parseMediaRef } from '../models/mediaRef.js';
import { computeConfidence, isEpisodeMismatched } from './videoSignatureExtractor.js';
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

export function sortVideos(
  videos,
  preferredQuality = settingsStore.get().preferredQuality,
  metadata = null,
) {
  if (!Array.isArray(videos)) return [];

  const expectedTitle = metadata?.titleEn || metadata?.titlePl || metadata?.title || null;
  const expectedYear = metadata?.year || null;
  const isTv = Boolean(metadata?.isTvShow || (metadata?.season != null && metadata?.episode != null));
  const season = metadata?.season != null ? Number(metadata.season) : null;
  const episode = metadata?.episode != null ? Number(metadata.episode) : null;

  const compatible = isTv
    ? videos.filter((video) => {
        const text = video.filename || video.serverName || '';
        return !isEpisodeMismatched(text, season, episode);
      })
    : videos;

  return [...compatible].sort((a, b) => {
    const textA = a.filename || a.serverName || '';
    const textB = b.filename || b.serverName || '';
    const confA = computeConfidence(textA, expectedTitle, expectedYear);
    const confB = computeConfidence(textB, expectedTitle, expectedYear);
    if (confA !== confB) {
      return confB - confA;
    }
    return (
      qualityRank(a.quality, a.version, preferredQuality) -
      qualityRank(b.quality, b.version, preferredQuality)
    );
  });
}

export function findBestSource(sources, targetSettings = null) {
  if (!Array.isArray(sources) || sources.length === 0) return null;

  if (targetSettings) {
    const targetServer = (targetSettings.serverName || '').toLowerCase();
    const targetSite = (targetSettings.sourceWebsite || '').toLowerCase();
    const targetVersion = (targetSettings.version || '').toLowerCase();

    if (targetVersion) {
      const fullWithVersion = sources.find((video) => {
        const vServer = (video.serverName || '').toLowerCase();
        const vSite = (video.sourceWebsite || '').toLowerCase();
        const vVersion = (video.version || '').toLowerCase();
        const serverMatches =
          !targetServer || vServer.includes(targetServer) || targetServer.includes(vServer);
        const siteMatches = !targetSite || vSite === targetSite;
        const versionMatches = vVersion.includes(targetVersion);
        return serverMatches && siteMatches && versionMatches;
      });
      if (fullWithVersion) return fullWithVersion;

      const versionOnly = sources.find((video) =>
        (video.version || '').toLowerCase().includes(targetVersion),
      );
      if (versionOnly) return versionOnly;
    }

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
    const sorted = sortVideos(resp.links || [], undefined, {
      isTvShow: ref.isTvShow,
      season: ref.season,
      episode: ref.episode,
    });
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

export function getSortedCachedSources(mediaUrl, metadata = null) {
  const ref = parseMediaRef(mediaUrl);
  if (!ref) return [];
  const key = linkKey(ref.tmdbId, ref.isTvShow ? ref.season : null, ref.isTvShow ? ref.episode : null);
  const cached = getCachedLinks(key);
  const meta = metadata || {
    isTvShow: ref.isTvShow,
    season: ref.season,
    episode: ref.episode,
  };
  return sortVideos(cached?.links || [], undefined, meta);
}
