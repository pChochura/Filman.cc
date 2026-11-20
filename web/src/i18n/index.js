import { isPolish, languageTag } from './locale.js';
import { STRINGS_EN } from './strings.en.js';
import { STRINGS_PL } from './strings.pl.js';

function activeTable() {
  return isPolish() ? STRINGS_PL : STRINGS_EN;
}

function formatString(template, args) {
  if (!template) return '';
  return template.replace(/%(\d+)\$[sd]/g, (match, indexStr) => {
    const idx = Number.parseInt(indexStr, 10) - 1;
    return idx >= 0 && idx < args.length && args[idx] !== undefined && args[idx] !== null
      ? String(args[idx])
      : match;
  });
}

export function t(key, ...args) {
  const table = activeTable();
  const raw = table[key] ?? STRINGS_EN[key] ?? key;
  if (typeof raw !== 'string') return key;
  return formatString(raw, args);
}

let cachedPluralRules = null;
function selectPluralCategory(count) {
  const lang = isPolish() ? 'pl' : 'en';
  if (!cachedPluralRules || cachedPluralRules.lang !== lang) {
    cachedPluralRules = { lang, rules: new Intl.PluralRules(lang) };
  }
  return cachedPluralRules.rules.select(count);
}

export function tp(key, count, ...args) {
  const table = activeTable();
  const entry = table[key] ?? STRINGS_EN[key];
  if (!entry || typeof entry !== 'object') {
    return t(key, count, ...args);
  }
  const category = selectPluralCategory(count);
  const template = entry[category] ?? entry.other ?? '';
  return formatString(template, [count, ...args]);
}

export function formatDuration(minutes) {
  const mins = Number(minutes);
  if (!Number.isFinite(mins) || mins <= 0) return null;
  const h = Math.floor(mins / 60);
  const m = Math.round(mins % 60);
  if (h > 0 && m > 0) return `${h}h ${m}m`;
  if (h > 0) return `${h}h`;
  return `${m}m`;
}

export function formatWatchTime(ms) {
  const totalMs = Number(ms);
  if (!Number.isFinite(totalMs) || totalMs <= 0) return null;
  const totalMinutes = Math.max(1, Math.floor(totalMs / 60_000));
  const days = Math.floor(totalMinutes / (24 * 60));
  const hours = Math.floor((totalMinutes % (24 * 60)) / 60);
  const minutes = totalMinutes % 60;
  if (days > 0 && hours > 0) {
    return t('watch_history_time_format_d_h_m', days, hours, minutes);
  }
  if (days > 0) {
    return t('watch_history_time_format_d_m', days, minutes);
  }
  if (hours > 0) {
    return t('watch_history_time_format_h_m', hours, minutes);
  }
  return t('watch_history_time_format_m', minutes);
}

export function formatTimeMs(ms) {
  const safeMs = Math.max(0, Math.floor(Number(ms) || 0));
  const totalSeconds = Math.floor(safeMs / 1000);
  const hours = Math.floor(totalSeconds / 3600);
  const minutes = Math.floor((totalSeconds % 3600) / 60);
  const seconds = totalSeconds % 60;
  const pad = (n) => String(n).padStart(2, '0');
  if (hours > 0) {
    return `${hours}:${pad(minutes)}:${pad(seconds)}`;
  }
  return `${pad(minutes)}:${pad(seconds)}`;
}

export function formatWatchDate(timestampMs) {
  try {
    return new Intl.DateTimeFormat(languageTag(), {
      day: '2-digit',
      month: 'long',
      year: 'numeric',
    }).format(new Date(timestampMs));
  } catch {
    return new Date(timestampMs).toISOString().slice(0, 10);
  }
}
