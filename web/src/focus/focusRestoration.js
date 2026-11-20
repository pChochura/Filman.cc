export const SectionFocusPrefix = {
  FEATURED: 'featured_',
  EPISODES: 'episodes_',
  CREW: 'crew_',
  CONTINUE_WATCHING: 'continue_',
  JUMP_BACK_IN: 'jump_back_in_',
  RECOMMENDED: 'recommended_',
  FILTER_BAR: 'filter_bar_',
  moviesRowPrefix: (title) => `movies_row_${title}_`,
};

const focusStack = [];

export function pushFocusKey(key) {
  if (!key) return;
  focusStack.push(key);
  if (focusStack.length > 30) {
    focusStack.shift();
  }
}

export function popFocusKey() {
  return focusStack.pop() || null;
}

export function peekFocusKey() {
  return focusStack.length > 0 ? focusStack[focusStack.length - 1] : null;
}

export function clearFocusStack() {
  focusStack.length = 0;
}

export function focusElementByKey(key) {
  if (typeof document === 'undefined' || !key) return false;
  const nodes = document.querySelectorAll('[data-focus-key]');
  for (const el of nodes) {
    if (el.getAttribute('data-focus-key') === key && el.offsetParent !== null) {
      el.focus({ preventScroll: false });
      return true;
    }
  }
  return false;
}

export function restoreSavedFocus(fallbackSelector = '[data-focus-zone="content"] [data-focusable="true"]') {
  const key = popFocusKey();
  if (key && focusElementByKey(key)) {
    return true;
  }
  if (typeof document !== 'undefined' && fallbackSelector) {
    const el = document.querySelector(fallbackSelector);
    if (el && typeof el.focus === 'function') {
      el.focus();
      return true;
    }
  }
  return false;
}

export function computeNeighborFallbackKey(items, removedUrl, prefix, fallbackPrefixKey = null) {
  const idx = items.findIndex((it) => it.url === removedUrl);
  if (idx === -1) return fallbackPrefixKey;
  if (items.length <= 1) return fallbackPrefixKey;
  const neighbor = items[idx + 1] || items[idx - 1];
  return neighbor ? `${prefix}${neighbor.url}` : fallbackPrefixKey;
}
