import { settingsStore } from '../data/local/settingsStore.js';

const KIDS_CERTIFICATIONS = {
  US: ['US', 'PG'],
  PL: ['PL', '12'],
  GB: ['GB', 'PG'],
  DE: ['DE', '12'],
  FR: ['FR', '12'],
  ES: ['ES', '12'],
  IT: ['IT', 'T'],
  CA: ['CA', 'PG'],
  AU: ['AU', 'PG'],
  BR: ['BR', '12'],
  NL: ['NL', '12'],
  SE: ['SE', '11'],
  NO: ['NO', '12'],
  DK: ['DK', '11'],
  FI: ['FI', 'K-12'],
  CZ: ['CZ', '12'],
  SK: ['SK', '12'],
  HU: ['HU', '12'],
  PT: ['PT', 'M/12'],
  IN: ['IN', 'U'],
};

function getBrowserLocaleTag() {
  if (typeof navigator === 'undefined') return 'en-US';
  const raw = navigator.language || (Array.isArray(navigator.languages) && navigator.languages[0]) || 'en-US';
  if (!raw || raw === 'und') return 'en-US';
  return raw;
}

export function getAppLanguage() {
  const custom = settingsStore?.get?.()?.appLanguage;
  if (custom === 'pl' || custom === 'en') {
    return custom;
  }
  return null;
}

export function languageTag() {
  const custom = getAppLanguage();
  if (custom === 'pl') return 'pl-PL';
  if (custom === 'en') return 'en-US';
  return getBrowserLocaleTag();
}

export function languageCode() {
  const custom = getAppLanguage();
  if (custom) return custom;
  const tag = getBrowserLocaleTag();
  const code = tag.split(/[-_]/)[0]?.toLowerCase();
  return code && code !== 'und' ? code : 'en';
}

export function countryCode() {
  const custom = getAppLanguage();
  if (custom === 'pl') return 'PL';
  if (custom === 'en') return 'US';
  const tag = getBrowserLocaleTag();
  const parts = tag.split(/[-_]/);
  for (let i = 1; i < parts.length; i++) {
    if (/^[a-zA-Z]{2}$/.test(parts[i])) {
      return parts[i].toUpperCase();
    }
  }
  const lang = parts[0]?.toLowerCase();
  if (lang === 'pl') return 'PL';
  return 'US';
}

export function isPolish() {
  return languageCode() === 'pl';
}

export function getKidsCertification(country = countryCode()) {
  const key = String(country || 'US').toUpperCase();
  const pair = KIDS_CERTIFICATIONS[key] || KIDS_CERTIFICATIONS.US;
  return { country: pair[0], certificationLte: pair[1] };
}

export function mapMovieGenreId(genreId) {
  switch (Number(genreId)) {
    case 10759:
      return '28|12';
    case 10765:
      return '878|14';
    case 10768:
      return '10752';
    case 10762:
      return '10751|16';
    default:
      return String(genreId);
  }
}

export function mapTvGenreId(genreId) {
  switch (Number(genreId)) {
    case 28:
    case 12:
      return '10759';
    case 878:
    case 14:
      return '10765';
    case 10752:
      return '10768';
    default:
      return String(genreId);
  }
}

export function mapGenreIdsForDiscover(genreIds, isTvShow) {
  if (!Array.isArray(genreIds) || genreIds.length === 0) return '';
  if (isTvShow) {
    return [...new Set(genreIds.map(mapTvGenreId))].join(',');
  }
  if (genreIds.length === 1) {
    return mapMovieGenreId(genreIds[0]);
  }
  return [...new Set(genreIds.map((id) => mapMovieGenreId(id).split('|')[0]))].join(',');
}

export function matchesGenre(itemGenreIds, selectedGenreId, isTvShow) {
  if (!Array.isArray(itemGenreIds)) return false;
  if (itemGenreIds.includes(selectedGenreId)) return true;
  const mapped = isTvShow ? mapTvGenreId(selectedGenreId) : mapMovieGenreId(selectedGenreId);
  return mapped
    .split('|')
    .map((s) => Number.parseInt(s, 10))
    .filter((n) => !Number.isNaN(n))
    .some((id) => itemGenreIds.includes(id));
}
