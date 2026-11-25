/**
 * Returns true if the client environment is mobile / touch-centric / small viewport.
 */
export function isMobileDevice() {
  if (typeof window === 'undefined') return false;
  if (typeof window.innerWidth === 'number' && window.innerWidth <= 768) return true;
  return Boolean(
    window.matchMedia?.('(hover: none) and (pointer: coarse)')?.matches ||
      window.matchMedia?.('(max-width: 768px)')?.matches,
  );
}
