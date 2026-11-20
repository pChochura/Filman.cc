import { getImdbId } from './tmdbClient.js';
import { extractVideoSignature } from './videoSignatureExtractor.js';
import { rankOpenSubtitleCandidates, rankWyziePolishCandidates } from './subtitleRanking.js';
import { diagnoseSubtitle, isLowQualityPolishSubtitle } from './subtitleDiagnostics.js';

export function isPolishSubtitle(sub) {
  if (!sub) return false;
  const lang = String(sub.language || '').toLowerCase();
  const label = String(sub.label || '').toLowerCase();
  return (
    lang === 'pl' ||
    lang === 'pol' ||
    lang.includes('pol') ||
    label.includes('polsk') ||
    label.includes('polish') ||
    label === 'pl' ||
    label.startsWith('pl ') ||
    label.startsWith('pl-') ||
    label.endsWith('(pl)') ||
    label.endsWith('pl')
  );
}

export function findMatchingSubtitle(subtitles, settings) {
  if (!settings?.subtitlesEnabled || !Array.isArray(subtitles) || subtitles.length === 0) {
    return null;
  }
  const lang = settings.subtitleLanguage;
  const label = settings.subtitleLabel;

  if (lang && label) {
    const match = subtitles.find(
      (s) =>
        String(s.language || '').toLowerCase() === lang.toLowerCase() &&
        String(s.label || '').toLowerCase() === label.toLowerCase(),
    );
    if (match) return match.url;
  }
  if (lang) {
    const match = subtitles.find(
      (s) => String(s.language || '').toLowerCase() === lang.toLowerCase(),
    );
    if (match) return match.url;
  }
  if (label) {
    const match = subtitles.find(
      (s) => String(s.label || '').toLowerCase() === label.toLowerCase(),
    );
    if (match) return match.url;
  }
  return subtitles[0]?.url || null;
}

export function subtitleUrlWithFormat(url, format = 'srt') {
  const f = String(format || 'srt').toLowerCase();
  const ext = f === 'srt' || f === 'vtt' || f === 'ttml' ? f : 'srt';
  return url.includes('?') ? `${url}&ext=.${ext}` : `${url}?ext=.${ext}`;
}

async function probeVideo(videoUrl, videoHeaders = {}) {
  if (!videoUrl || /\.m3u8/i.test(videoUrl) || /\.mpd/i.test(videoUrl)) {
    return null;
  }
  try {
    const res = await fetch('/api/subtitles?action=probe', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ url: videoUrl, headers: videoHeaders }),
    });
    if (!res.ok) return null;
    return await res.json();
  } catch {
    return null;
  }
}

async function searchOpenSubtitlesRaw({
  query,
  language,
  season,
  episode,
  tmdbId,
  imdbId,
  videoFileName,
  movieHash,
}) {
  const candidates = [];

  const flattenResponse = (json) => {
    const out = [];
    for (const item of json?.data || []) {
      const attrs = item?.attributes || {};
      for (const file of attrs.files || []) {
        out.push({
          fileId: file.file_id,
          fileName: file.file_name,
          language: attrs.language || language,
          release: attrs.release || null,
          downloadCount: attrs.download_count || 0,
          rating: attrs.ratings ?? null,
          votes: attrs.votes ?? null,
          fps: attrs.fps ?? null,
          fromTrusted: Boolean(attrs.from_trusted),
          aiTranslated: Boolean(attrs.ai_translated || attrs.machine_translated),
          foreignPartsOnly: Boolean(attrs.foreign_parts_only),
          hearingImpaired: Boolean(attrs.hearing_impaired),
          moviehashMatch: Boolean(attrs.moviehash_match),
        });
      }
    }
    return out;
  };

  if (movieHash) {
    try {
      const hp = new URLSearchParams({
        provider: 'opensubtitles',
        languages: language,
        moviehash: movieHash,
      });
      const res = await fetch(`/api/subtitles?${hp.toString()}`);
      if (res.ok) {
        candidates.push(...flattenResponse(await res.json()));
      }
    } catch {
      // ignore hash query failures
    }
  }

  const params = new URLSearchParams({
    provider: 'opensubtitles',
    languages: language,
  });

  if (tmdbId && tmdbId > 0) {
    params.set(season != null && episode != null ? 'parent_tmdb_id' : 'tmdb_id', String(tmdbId));
  } else if (imdbId) {
    const numeric = String(imdbId).replace(/^tt/, '');
    if (/^\d+$/.test(numeric)) {
      params.set(season != null && episode != null ? 'parent_imdb_id' : 'imdb_id', numeric);
    } else if (query) {
      params.set('query', query);
    }
  } else if (query) {
    params.set('query', query);
  }

  if (season != null && episode != null) {
    params.set('season_number', String(season));
    params.set('episode_number', String(episode));
  }

  const res = await fetch(`/api/subtitles?${params.toString()}`);
  if (!res.ok) {
    throw new Error(`OpenSubtitles search failed: HTTP ${res.status}`);
  }
  candidates.push(...flattenResponse(await res.json()));

  const deduped = [];
  const seen = new Set();
  for (const c of candidates) {
    if (!seen.has(c.fileId)) {
      seen.add(c.fileId);
      deduped.push(c);
    }
  }

  return rankOpenSubtitleCandidates(deduped, videoFileName);
}

async function downloadOpenSubtitle(fileId) {
  const res = await fetch('/api/subtitles?provider=opensubtitles&action=download', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ file_id: fileId }),
  });
  if (!res.ok) {
    throw new Error(`OpenSubtitles download failed: HTTP ${res.status}`);
  }
  const data = await res.json();
  if (!data?.link) {
    throw new Error(data?.error || 'OpenSubtitles download returned no link');
  }
  return {
    link: data.link,
    fileName: data.file_name || 'subtitle.srt',
  };
}

async function searchWyzieRaw({ tmdbId, imdbId, season, episode, language }) {
  const isTv = season != null && episode != null;
  let resolvedId = imdbId || null;
  if (!resolvedId && tmdbId > 0) {
    resolvedId = (await getImdbId({ tmdbId, isTvShow: isTv })) || String(tmdbId);
  }
  if (!resolvedId) return [];

  const params = new URLSearchParams({
    provider: 'wyzie',
    id: resolvedId,
  });
  if (isTv) {
    params.set('season', String(season));
    params.set('episode', String(episode));
  }
  if (language) params.set('language', language.toLowerCase());

  try {
    const res = await fetch(`/api/subtitles?${params.toString()}`);
    if (!res.ok) return [];
    const list = await res.json();
    return Array.isArray(list) ? list : [];
  } catch {
    return [];
  }
}

export async function fetchRawSubtitleContent({
  url,
  isDownloaded = false,
  videoHeaders = {},
}) {
  if (!url) return { ok: false, notifyDownloadError: isDownloaded, body: null };
  try {
    const params = new URLSearchParams({
      action: 'fetch',
      url,
      downloaded: isDownloaded ? '1' : '0',
    });
    if (!isDownloaded && videoHeaders && Object.keys(videoHeaders).length > 0) {
      params.set('headers', JSON.stringify(videoHeaders));
    }
    const res = await fetch(`/api/subtitles?${params.toString()}`);
    if (!res.ok) {
      return { ok: false, notifyDownloadError: isDownloaded, body: null };
    }
    const json = await res.json();
    if (typeof json?.text !== 'string') {
      return { ok: false, notifyDownloadError: isDownloaded, body: null };
    }
    return { ok: true, notifyDownloadError: false, body: json.text };
  } catch {
    return { ok: false, notifyDownloadError: isDownloaded, body: null };
  }
}

export async function searchSubtitleCandidates({
  detailedMedia,
  language,
  videoUrl,
  videoHeaders = {},
  serverName = null,
  quality = null,
}) {
  const title = detailedMedia?.baseItem?.titleEn || detailedMedia?.baseItem?.titlePl || '';
  const season = detailedMedia?.baseItem?.seasonNumber ?? null;
  const episode = detailedMedia?.baseItem?.episodeNumber ?? null;

  const probe = await probeVideo(videoUrl, videoHeaders);
  const videoFileName = extractVideoSignature({
    videoUrl: probe?.finalUrl || videoUrl,
    headers: probe?.contentDisposition
      ? { ...videoHeaders, 'Content-Disposition': probe.contentDisposition }
      : videoHeaders,
    serverName,
    quality,
  });

  const [openSubs, wyzieRaw] = await Promise.all([
    searchOpenSubtitlesRaw({
      query: title,
      language,
      season,
      episode,
      tmdbId: detailedMedia?.tmdbId,
      imdbId: detailedMedia?.imdbId,
      videoFileName,
      movieHash: probe?.movieHash || null,
    }).catch(() => []),
    searchWyzieRaw({
      tmdbId: detailedMedia?.tmdbId,
      imdbId: detailedMedia?.imdbId,
      season,
      episode,
      language,
    }),
  ]);

  const wyzieSubs = rankWyziePolishCandidates(
    wyzieRaw.filter((w) => String(w.language || '').toLowerCase() === language.toLowerCase()),
    videoFileName,
  );

  const openCandidates = openSubs.map((c) => ({
    id: `open_${c.fileId}`,
    name: c.fileName,
    language: c.language,
    release: c.release,
    provider: 'OpenSubtitles',
    downloadCount: c.downloadCount,
    rating: c.rating,
    foreignPartsOnly: c.foreignPartsOnly,
    hearingImpaired: c.hearingImpaired,
    openSubtitleFileId: c.fileId,
    directUrl: null,
  }));

  const wyzieCandidates = wyzieSubs.map((sub, idx) => {
    const name = sub.fileName
      ? sub.fileName
      : sub.release
        ? sub.release
        : sub.ai
          ? `${sub.display} (AI)`
          : idx > 0
            ? `${sub.display} #${idx + 1}`
            : sub.display;
    return {
      id: `wyzie_${sub.id || idx}_${idx}`,
      name,
      language: sub.language,
      release: sub.release || null,
      provider: 'Wyzie',
      downloadCount: sub.downloadCount ?? null,
      rating: null,
      foreignPartsOnly: false,
      hearingImpaired: Boolean(sub.isHearingImpaired),
      openSubtitleFileId: null,
      directUrl: subtitleUrlWithFormat(sub.url, sub.format || 'srt'),
    };
  });

  return {
    candidates: [...openCandidates, ...wyzieCandidates],
    openSubs,
  };
}

export async function downloadSubtitleCandidate(candidate) {
  if (!candidate) return null;
  if (candidate.openSubtitleFileId != null) {
    const dl = await downloadOpenSubtitle(candidate.openSubtitleFileId);
    const ext = dl.fileName.split('.').pop() || 'srt';
    return {
      url: subtitleUrlWithFormat(dl.link, ext),
      label: `${candidate.provider} (${candidate.language}) ${candidate.name}`.trim(),
      language: candidate.language,
    };
  }
  if (candidate.directUrl) {
    return {
      url: candidate.directUrl,
      label: `${candidate.provider} (${candidate.name})`.trim(),
      language: candidate.language,
    };
  }
  return null;
}

export async function findBestPolishSubtitle({
  detailedMedia,
  videoUrl,
  videoHeaders = {},
  durationMs = 0,
  serverName = null,
  quality = null,
  preferredGroup = null,
  preferredSource = null,
}) {
  const title = detailedMedia?.baseItem?.titleEn || detailedMedia?.baseItem?.titlePl || '';
  const season = detailedMedia?.baseItem?.seasonNumber ?? null;
  const episode = detailedMedia?.baseItem?.episodeNumber ?? null;
  let searchFailed = false;

  const isBadCandidateBody = (body) =>
    diagnoseSubtitle(body, durationMs) !== null || isLowQualityPolishSubtitle(body);

  const probe = await probeVideo(videoUrl, videoHeaders);
  const videoFileName = extractVideoSignature({
    videoUrl: probe?.finalUrl || videoUrl,
    headers: probe?.contentDisposition
      ? { ...videoHeaders, 'Content-Disposition': probe.contentDisposition }
      : videoHeaders,
    serverName,
    quality,
  });

  let openCandidates = [];
  try {
    openCandidates = await searchOpenSubtitlesRaw({
      query: title,
      language: 'pl',
      season,
      episode,
      tmdbId: detailedMedia?.tmdbId,
      imdbId: detailedMedia?.imdbId,
      videoFileName,
      movieHash: probe?.movieHash || null,
    });
    if (
      openCandidates.length === 0 &&
      (!detailedMedia?.tmdbId || detailedMedia.tmdbId <= 0) &&
      detailedMedia?.baseItem?.titlePl &&
      detailedMedia.baseItem.titlePl !== title
    ) {
      openCandidates = await searchOpenSubtitlesRaw({
        query: detailedMedia.baseItem.titlePl,
        language: 'pl',
        season,
        episode,
        imdbId: detailedMedia?.imdbId,
        videoFileName,
        movieHash: probe?.movieHash || null,
      });
    }
  } catch {
    searchFailed = true;
  }

  const rankedOpen =
    preferredGroup || preferredSource
      ? rankOpenSubtitleCandidates(openCandidates, videoFileName, preferredGroup, preferredSource)
      : openCandidates;

  if (rankedOpen.length > 0) {
    let chosen = rankedOpen[0];
    let dl = await downloadOpenSubtitle(chosen.fileId).catch(() => {
      searchFailed = true;
      return null;
    });
    if (dl && rankedOpen.length > 1) {
      const primaryUrl = subtitleUrlWithFormat(dl.link, dl.fileName.split('.').pop() || 'srt');
      const bodyRes = await fetchRawSubtitleContent({ url: primaryUrl, isDownloaded: true });
      if (bodyRes.ok && isBadCandidateBody(bodyRes.body)) {
        const second = rankedOpen[1];
        const secondDl = await downloadOpenSubtitle(second.fileId).catch(() => null);
        if (secondDl) {
          chosen = second;
          dl = secondDl;
        }
      }
    }
    if (dl) {
      const srtUrl = subtitleUrlWithFormat(dl.link, dl.fileName.split('.').pop() || 'srt');
      return {
        found: true,
        subtitle: {
          url: srtUrl,
          label: `OpenSubtitles (pl) ${chosen.fileName}`.trim(),
          language: 'pl',
        },
      };
    }
  }

  const wyzieRaw = await searchWyzieRaw({
    tmdbId: detailedMedia?.tmdbId,
    imdbId: detailedMedia?.imdbId,
    season,
    episode,
    language: 'pl',
  });
  const wyzieRanked = rankWyziePolishCandidates(
    wyzieRaw.filter((w) => String(w.language || '').toLowerCase() === 'pl'),
    videoFileName,
  );
  if (wyzieRanked.length > 0) {
    let chosen = wyzieRanked[0];
    if (wyzieRanked.length > 1) {
      for (const cand of wyzieRanked.slice(0, 3)) {
        const url = subtitleUrlWithFormat(cand.url, cand.format || 'srt');
        const bodyRes = await fetchRawSubtitleContent({ url, isDownloaded: true });
        if (bodyRes.ok && !isBadCandidateBody(bodyRes.body)) {
          chosen = cand;
          break;
        }
      }
    }
    const labelText =
      chosen.fileName ||
      chosen.release ||
      (chosen.ai ? `${chosen.display} (AI)` : chosen.display);
    return {
      found: true,
      subtitle: {
        url: subtitleUrlWithFormat(chosen.url, chosen.format || 'srt'),
        label: `Wyzie (${labelText})`,
        language: 'pl',
      },
    };
  }

  return { found: false, searchFailed };
}

export async function findReferenceSubtitleForAlignment({
  detailedMedia,
  videoUrl,
  videoHeaders = {},
  serverName = null,
  quality = null,
}) {
  const title = detailedMedia?.baseItem?.titleEn || detailedMedia?.baseItem?.titlePl || '';
  const season = detailedMedia?.baseItem?.seasonNumber ?? null;
  const episode = detailedMedia?.baseItem?.episodeNumber ?? null;

  const probe = await probeVideo(videoUrl, videoHeaders);
  const videoFileName = extractVideoSignature({
    videoUrl: probe?.finalUrl || videoUrl,
    headers: probe?.contentDisposition
      ? { ...videoHeaders, 'Content-Disposition': probe.contentDisposition }
      : videoHeaders,
    serverName,
    quality,
  });

  const enCandidates = await searchOpenSubtitlesRaw({
    query: title,
    language: 'en',
    season,
    episode,
    tmdbId: detailedMedia?.tmdbId,
    imdbId: detailedMedia?.imdbId,
    videoFileName,
    movieHash: probe?.movieHash || null,
  }).catch(() => []);

  const top = enCandidates[0];
  if (!top) return null;
  const dl = await downloadOpenSubtitle(top.fileId).catch(() => null);
  if (!dl) return null;
  const srtUrl = subtitleUrlWithFormat(dl.link, dl.fileName.split('.').pop() || 'srt');
  const raw = await fetchRawSubtitleContent({ url: srtUrl, isDownloaded: true });
  return raw.ok ? raw.body : null;
}
