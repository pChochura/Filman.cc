import {
  cleanFilename,
  jaccardSimilarity,
  tokenizeSignature,
} from './videoSignatureExtractor.js';

const KNOWN_GROUPS = new Set([
  'flux', 'yts', 'yify', 'cmrg', 'evo', 'sparks', 'ion10', 'ntb', 'tepes',
  'glhf', 'smurf', 'kings', 'kogi', 'geckos', 'rovers', 'amiable', 'fgt',
  'rarbg', 'psa', 'galaxyrg', 'etrg', 'megusta', 'ctrlhd', 'don', 'ebp',
  'framestor', 'qxr', 'bwb', 'as68', 'tgx', 'pahe', 'vxt', 'galaxy',
]);

const NON_GROUP_TOKENS = new Set([
  'x264', 'x265', 'hevc', 'h264', 'h265', 'aac', 'ddp5', '1080p', '720p',
  '2160p', '4k', 'dl', 'rip', 'web', 'sub', 'srt',
]);

export function extractReleaseGroup(name) {
  const clean = String(name || '').replace(/\.[^.]+$/, '').trim();
  const groupMatch = /-([a-zA-Z0-9]+)(?:\[.*?\])?$/.exec(clean);
  const group = groupMatch?.[1]?.toLowerCase();
  if (group && group.length >= 2 && !NON_GROUP_TOKENS.has(group)) {
    return group;
  }
  const tokens = clean.split(/[^a-zA-Z0-9]+/).map((s) => s.toLowerCase());
  for (let i = tokens.length - 1; i >= 0; i--) {
    if (KNOWN_GROUPS.has(tokens[i])) return tokens[i];
  }
  return null;
}

export function detectSourceType(name) {
  const lower = String(name || '').toLowerCase();
  if (/\b(web-?dl|web-?rip|web)\b/.test(lower)) return 'WEB';
  if (/\b(bluray|blu-ray|bdrip|brrip|bd-rip)\b/.test(lower)) return 'BLURAY';
  if (/\b(hdtv|pdtv|dsr)\b/.test(lower)) return 'HDTV';
  if (/\b(dvdrip|dvd-rip|dvd)\b/.test(lower)) return 'DVDRIP';
  if (/\b(cam|camrip|telesync|hdts|ts|hdcam)\b/.test(lower)) return 'CAM';
  return null;
}

function detectResolution(name) {
  const match = /\b(2160p|4k|1080p|720p|480p)\b/i.exec(name);
  return match ? match[0].toLowerCase().replace('4k', '2160p') : null;
}

function detectCodec(name) {
  const lower = String(name || '').toLowerCase();
  if (/\b(x264|h264|avc)\b/.test(lower)) return 'h264';
  if (/\b(x265|h265|hevc)\b/.test(lower)) return 'hevc';
  if (/\b(av1)\b/.test(lower)) return 'av1';
  if (/\b(xvid)\b/.test(lower)) return 'xvid';
  return null;
}

function normalizedBase(name) {
  return String(name || '')
    .split('/')
    .pop()
    .replace(/\.(srt|vtt|mkv|mp4|avi|webm)$/i, '')
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '');
}

function detectStreamingService(name) {
  const lower = String(name || '').toLowerCase();
  if (/\b(amzn|amazon)\b/.test(lower)) return 'amzn';
  if (/\b(nf|netflix)\b/.test(lower)) return 'netflix';
  if (/\b(dsnp|disney|disney\+)\b/.test(lower)) return 'disney';
  if (/\b(atvp|apple|apple\s*tv)\b/.test(lower)) return 'appletv';
  if (/\b(hbo|max|hmax)\b/.test(lower)) return 'max';
  if (/\b(hulu)\b/.test(lower)) return 'hulu';
  if (/\b(pmtp|paramount|paramount\+)\b/.test(lower)) return 'paramount';
  if (/\b(pcok|peacock)\b/.test(lower)) return 'peacock';
  if (/\b(sky)\b/.test(lower)) return 'sky';
  return null;
}

function detectCutEdition(name) {
  const lower = String(name || '').toLowerCase();
  if (/\b(extended|ext)\b/.test(lower)) return 'extended';
  if (/\b(directors\.?cut|dc)\b/.test(lower)) return 'directors_cut';
  if (/\b(unrated)\b/.test(lower)) return 'unrated';
  if (/\b(theatrical)\b/.test(lower)) return 'theatrical';
  if (/\b(imax)\b/.test(lower)) return 'imax';
  return null;
}

export function calculateCandidateScore(
  candidate,
  videoFileName,
  preferredGroup = null,
  preferredSource = null,
) {
  let score = 0;

  if (candidate.moviehashMatch) score += 5000;
  if (candidate.foreignPartsOnly) score -= 2000;
  if (candidate.aiTranslated) score -= 100;
  if (candidate.fromTrusted) score += 50;
  if (candidate.hearingImpaired) score -= 35;

  score += Math.log(1 + (candidate.downloadCount || 0)) * 1.5;
  if (candidate.rating && candidate.rating > 0) {
    const voteWeight = (candidate.votes || 0) >= 5 ? 2 : 1;
    score += candidate.rating * voteWeight;
  }

  if (!videoFileName || !String(videoFileName).trim()) {
    return score;
  }

  const videoClean = cleanFilename(videoFileName).toLowerCase();
  const videoBase = normalizedBase(videoFileName);
  const candidateNames = [candidate.release, candidate.fileName].filter(Boolean);

  if (preferredGroup) {
    const prefLower = preferredGroup.toLowerCase();
    const matchesPrefGroup = candidateNames.some((name) => {
      const candClean = cleanFilename(name).toLowerCase();
      const candGroup = extractReleaseGroup(candClean);
      return (
        (candGroup && candGroup === prefLower) ||
        candClean.includes(`-${prefLower}`) ||
        candClean.endsWith(prefLower)
      );
    });
    if (matchesPrefGroup) score += 250;
  }

  if (preferredSource) {
    let candSource = null;
    for (const n of candidateNames) {
      candSource = detectSourceType(n.toLowerCase());
      if (candSource) break;
    }
    if (candSource && candSource.toLowerCase() === preferredSource.toLowerCase()) {
      score += 150;
    }
  }

  const hasExactMatch = candidateNames.some((name) => {
    const norm = normalizedBase(name);
    return norm.length >= 8 && norm === videoBase;
  });
  if (hasExactMatch) score += 300;

  const videoGroup = extractReleaseGroup(videoClean);
  if (videoGroup) {
    const matchesGroup = candidateNames.some((name) => {
      const candClean = cleanFilename(name).toLowerCase();
      const candGroup = extractReleaseGroup(candClean);
      return (
        (candGroup && candGroup === videoGroup) ||
        candClean.includes(`-${videoGroup}`) ||
        candClean.endsWith(videoGroup)
      );
    });
    if (matchesGroup) score += 200;
  }

  const videoService = detectStreamingService(videoClean);
  let candidateService = null;
  for (const n of candidateNames) {
    candidateService = detectStreamingService(n.toLowerCase());
    if (candidateService) break;
  }
  if (videoService && candidateService) {
    score += videoService === candidateService ? 150 : -50;
  }

  const videoCut = detectCutEdition(videoClean);
  let candidateCut = null;
  for (const n of candidateNames) {
    candidateCut = detectCutEdition(n.toLowerCase());
    if (candidateCut) break;
  }
  if (videoCut || candidateCut) {
    if (videoCut && candidateCut && videoCut === candidateCut) score += 200;
    else if (videoCut !== candidateCut) score -= 400;
  }

  const videoSource = detectSourceType(videoClean);
  let candidateSource = null;
  for (const n of candidateNames) {
    candidateSource = detectSourceType(n.toLowerCase());
    if (candidateSource) break;
  }
  if (videoSource && candidateSource) {
    score += videoSource === candidateSource ? 120 : -60;
  }

  const videoRes = detectResolution(videoClean);
  let candidateRes = null;
  for (const n of candidateNames) {
    candidateRes = detectResolution(n.toLowerCase());
    if (candidateRes) break;
  }
  if (videoRes && candidateRes && videoRes === candidateRes) score += 30;

  const videoCodec = detectCodec(videoClean);
  let candidateCodec = null;
  for (const n of candidateNames) {
    candidateCodec = detectCodec(n.toLowerCase());
    if (candidateCodec) break;
  }
  if (videoCodec && candidateCodec && videoCodec === candidateCodec) score += 20;

  const videoTokens = tokenizeSignature(videoClean);
  if (videoTokens.size > 0) {
    let maxJaccard = 0;
    for (const name of candidateNames) {
      const sim = jaccardSimilarity(videoTokens, tokenizeSignature(name.toLowerCase()));
      if (sim > maxJaccard) maxJaccard = sim;
    }
    score += maxJaccard * 60;
  }

  return score;
}

export function rankOpenSubtitleCandidates(
  candidates,
  videoFileName,
  preferredGroup = null,
  preferredSource = null,
) {
  return [...(candidates || [])].sort(
    (a, b) =>
      calculateCandidateScore(b, videoFileName, preferredGroup, preferredSource) -
      calculateCandidateScore(a, videoFileName, preferredGroup, preferredSource),
  );
}

export function rankWyziePolishCandidates(candidates, videoFileName) {
  if (!Array.isArray(candidates) || candidates.length <= 1) return candidates || [];
  const videoTokens =
    videoFileName && String(videoFileName).trim()
      ? tokenizeSignature(cleanFilename(videoFileName))
      : new Set();

  const scoreWyzie = (sub) => {
    let score = 0;
    if (sub.ai) score -= 100;
    if (sub.isHearingImpaired) score -= 35;
    if (sub.downloadCount && sub.downloadCount > 0) {
      score += Math.log(1 + sub.downloadCount) * 1.5;
    }
    if (videoTokens.size > 0) {
      const candName = sub.fileName || sub.release || sub.display || '';
      score += jaccardSimilarity(videoTokens, tokenizeSignature(candName)) * 60;
    }
    return score;
  };

  return [...candidates].sort((a, b) => scoreWyzie(b) - scoreWyzie(a));
}
