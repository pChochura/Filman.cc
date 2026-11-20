function isVisibleFocusable(el) {
  if (!el || el.disabled) return false;
  if (el.getAttribute('aria-disabled') === 'true') return false;
  if (el.offsetParent === null && getComputedStyle(el).position !== 'fixed') return false;
  const rect = el.getBoundingClientRect();
  return rect.width > 0 && rect.height > 0;
}

function getScopedContainer(activeEl) {
  const overlay = document.querySelector('.overlay-drawer');
  if (overlay) return overlay;
  const screensaver = document.querySelector('.screensaver-overlay');
  if (screensaver) return screensaver;
  const player = document.querySelector('.player-root');
  if (player) return player;
  if (activeEl?.closest?.('.login-screen')) return activeEl.closest('.login-screen');
  return document;
}

function getFocusables(container) {
  return Array.from(container.querySelectorAll('[data-focusable="true"]')).filter(isVisibleFocusable);
}

function findNextInDirection(activeEl, direction, container) {
  const focusables = getFocusables(container);
  if (focusables.length === 0) return null;

  if (!activeEl || !container.contains(activeEl)) {
    return focusables[0];
  }

  // Handle explicit zone transitions (navbar <-> content)
  const zone = activeEl.getAttribute('data-focus-zone');
  if (zone === 'navbar' && direction === 'down') {
    const contentTarget = focusables.find((el) => el.getAttribute('data-focus-zone') === 'content');
    if (contentTarget) return contentTarget;
  }

  // Handle circular wrap-around within horizontal rows
  const group = activeEl.getAttribute('data-focus-group');
  if (group && (direction === 'left' || direction === 'right')) {
    const groupItems = focusables.filter((el) => el.getAttribute('data-focus-group') === group);
    if (groupItems.length > 0) {
      const idx = groupItems.indexOf(activeEl);
      if (idx !== -1) {
        if (direction === 'right') {
          if (idx + 1 < groupItems.length) return groupItems[idx + 1];
          if (activeEl.getAttribute('data-focus-wrap') === 'true') return groupItems[0];
          return null;
        }
        if (direction === 'left') {
          if (idx - 1 >= 0) return groupItems[idx - 1];
          if (activeEl.getAttribute('data-focus-wrap') === 'true') {
            return groupItems[groupItems.length - 1];
          }
          return null;
        }
      }
    }
  }

  const rectA = activeEl.getBoundingClientRect();
  const cxA = rectA.left + rectA.width / 2;
  const cyA = rectA.top + rectA.height / 2;

  let best = null;
  let bestScore = Infinity;

  const sameZoneOnly = container === document && zone === 'navbar';

  for (const el of focusables) {
    if (el === activeEl) continue;
    if (sameZoneOnly && (direction === 'left' || direction === 'right')) {
      if (el.getAttribute('data-focus-zone') !== 'navbar') continue;
    }

    const rectB = el.getBoundingClientRect();
    const cxB = rectB.left + rectB.width / 2;
    const cyB = rectB.top + rectB.height / 2;
    const dx = cxB - cxA;
    const dy = cyB - cyA;

    let valid = false;
    let primary = 0;
    let secondary = 0;

    if (direction === 'right') {
      valid = dx > 6;
      primary = dx;
      secondary = Math.abs(dy);
    } else if (direction === 'left') {
      valid = dx < -6;
      primary = -dx;
      secondary = Math.abs(dy);
    } else if (direction === 'down') {
      valid = dy > 6;
      primary = dy;
      secondary = Math.abs(dx);
    } else if (direction === 'up') {
      valid = dy < -6;
      primary = -dy;
      secondary = Math.abs(dx);
    }

    if (!valid) continue;

    // Bonus for overlap along perpendicular axis
    const perpOverlap =
      direction === 'left' || direction === 'right'
        ? Math.max(0, Math.min(rectA.bottom, rectB.bottom) - Math.max(rectA.top, rectB.top)) > 0
        : Math.max(0, Math.min(rectA.right, rectB.right) - Math.max(rectA.left, rectB.left)) > 0;

    const score = primary + secondary * (perpOverlap ? 1.5 : 4.0);
    if (score < bestScore) {
      bestScore = score;
      best = el;
    }
  }

  if (!best && zone === 'content' && direction === 'up') {
    const selectedNav =
      document.querySelector('[data-focus-zone="navbar"][data-selected="true"]') ||
      document.querySelector('[data-focus-zone="navbar"][data-focusable="true"]');
    if (selectedNav && isVisibleFocusable(selectedNav)) {
      return selectedNav;
    }
  }

  return best;
}

export function installTvFocusManager() {
  if (typeof window === 'undefined') return () => {};

  let enterDownTime = 0;
  let enterLongFired = false;
  let enterTarget = null;
  let enterTimer = null;

  const onKeyDown = (e) => {
    const activeEl = document.activeElement;
    const isTextInput =
      activeEl &&
      (activeEl.tagName === 'INPUT' || activeEl.tagName === 'TEXTAREA') &&
      activeEl.type !== 'button' &&
      activeEl.type !== 'submit';

    // Long-press Enter detection for cards/buttons with context menus
    if (e.key === 'Enter' && !isTextInput && activeEl?.getAttribute?.('data-has-context-menu') === 'true') {
      if (!e.repeat) {
        enterDownTime = Date.now();
        enterLongFired = false;
        enterTarget = activeEl;
        clearTimeout(enterTimer);
        enterTimer = setTimeout(() => {
          if (enterTarget === document.activeElement) {
            enterLongFired = true;
            enterTarget.dispatchEvent(new CustomEvent('tv-long-press', { bubbles: true }));
          }
        }, 520);
      } else {
        e.preventDefault();
      }
      return;
    }

    // ContextMenu key or 'm' shortcut
    if (
      (e.key === 'ContextMenu' || (e.key === 'm' && !isTextInput && !e.metaKey && !e.ctrlKey)) &&
      activeEl?.getAttribute?.('data-has-context-menu') === 'true'
    ) {
      e.preventDefault();
      activeEl.dispatchEvent(new CustomEvent('tv-long-press', { bubbles: true }));
      return;
    }

    const dirMap = {
      ArrowUp: 'up',
      ArrowDown: 'down',
      ArrowLeft: 'left',
      ArrowRight: 'right',
    };
    const direction = dirMap[e.key];
    if (!direction) return;

    if (e.defaultPrevented) return;
    if (isTextInput && (direction === 'left' || direction === 'right')) return;

    // Let active screensaver or hidden-HUD player handle directional keys themselves
    if (document.querySelector('.screensaver-overlay')) return;
    if (document.querySelector('.player-root[data-hud-visible="false"]')) return;
    if (activeEl?.getAttribute?.('data-seekbar-capture') === 'true' && (direction === 'left' || direction === 'right')) {
      return;
    }

    const container = getScopedContainer(activeEl);
    const next = findNextInDirection(activeEl, direction, container);
    if (next) {
      e.preventDefault();
      next.focus();
      next.scrollIntoView?.({ block: 'nearest', inline: 'nearest', behavior: 'smooth' });
    } else if (container !== document && (direction === 'left' || direction === 'right')) {
      // Trap left/right inside overlay drawer
      e.preventDefault();
    }
  };

  const onKeyUp = (e) => {
    if (e.key === 'Enter') {
      clearTimeout(enterTimer);
      if (enterLongFired) {
        e.preventDefault();
        e.stopPropagation();
        enterLongFired = false;
        enterTarget = null;
        return;
      }
      enterDownTime = 0;
      enterTarget = null;
    }
  };

  window.addEventListener('keydown', onKeyDown);
  window.addEventListener('keyup', onKeyUp, true);

  return () => {
    clearTimeout(enterTimer);
    window.removeEventListener('keydown', onKeyDown);
    window.removeEventListener('keyup', onKeyUp, true);
  };
}
