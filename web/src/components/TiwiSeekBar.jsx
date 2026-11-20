import React, { useEffect, useRef, useState } from 'react';
import { formatTimeMs } from '../i18n/index.js';

export default function TiwiSeekBar({
  progressMs = 0,
  durationMs = 0,
  seekTargetMs = null,
  isBuffering = false,
  onSeekCommit,
  onScrubStart,
  onCancelScrub,
  seekBarRef,
}) {
  const [scrubMs, setScrubMs] = useState(null);
  const [originMs, setOriginMs] = useState(null);
  const [isFocused, setIsFocused] = useState(false);
  const trackRef = useRef(null);
  const holdStartRef = useRef(0);
  const lastDirRef = useRef(0);

  const safeDuration = Math.max(0, durationMs);
  const displayMs = scrubMs !== null ? scrubMs : progressMs;
  const fraction = safeDuration > 0 ? Math.max(0, Math.min(1, displayMs / safeDuration)) : 0;
  const targetFraction =
    seekTargetMs !== null && safeDuration > 0
      ? Math.max(0, Math.min(1, seekTargetMs / safeDuration))
      : null;
  const originFraction =
    originMs !== null && safeDuration > 0
      ? Math.max(0, Math.min(1, originMs / safeDuration))
      : null;

  useEffect(() => {
    if (safeDuration <= 0) {
      setScrubMs(null);
      setOriginMs(null);
    }
  }, [safeDuration]);

  const computeStepMs = (dir) => {
    const now = Date.now();
    if (lastDirRef.current !== dir || holdStartRef.current === 0) {
      lastDirRef.current = dir;
      holdStartRef.current = now;
    }
    const elapsed = now - holdStartRef.current;
    if (elapsed > 5000) return 300_000;
    if (elapsed > 2500) return 60_000;
    if (elapsed > 1000) return 30_000;
    return 10_000;
  };

  const handleKeyDown = (e) => {
    if (safeDuration <= 0) return;
    if (e.key === 'ArrowLeft' || e.key === 'ArrowRight') {
      e.preventDefault();
      e.stopPropagation();
      const dir = e.key === 'ArrowRight' ? 1 : -1;
      const step = computeStepMs(dir);
      setOriginMs((prev) => (prev === null ? progressMs : prev));
      setScrubMs((prev) => {
        const base = prev !== null ? prev : progressMs;
        return Math.max(0, Math.min(safeDuration, base + dir * step));
      });
      onScrubStart?.();
    } else if (e.key === 'Escape' || e.key === 'Backspace') {
      if (scrubMs !== null) {
        e.preventDefault();
        e.stopPropagation();
        setScrubMs(null);
        setOriginMs(null);
        onCancelScrub?.();
      }
    } else if (e.key === 'Enter' || e.key === ' ') {
      if (scrubMs !== null) {
        e.preventDefault();
        e.stopPropagation();
        const committed = scrubMs;
        setScrubMs(null);
        setOriginMs(null);
        onSeekCommit?.(committed);
      }
    }
  };

  const handleKeyUp = (e) => {
    if (e.key === 'ArrowLeft' || e.key === 'ArrowRight') {
      lastDirRef.current = 0;
      holdStartRef.current = 0;
    }
  };

  const seekFromPointer = (clientX) => {
    if (!trackRef.current || safeDuration <= 0) return null;
    const rect = trackRef.current.getBoundingClientRect();
    const ratio = Math.max(0, Math.min(1, (clientX - rect.left) / rect.width));
    return Math.round(ratio * safeDuration);
  };

  const handlePointerDown = (e) => {
    if (safeDuration <= 0) return;
    e.currentTarget.focus();
    const startVal = seekFromPointer(e.clientX);
    if (startVal === null) return;
    setOriginMs(progressMs);
    setScrubMs(startVal);
    onScrubStart?.();

    const onMove = (moveEvent) => {
      const val = seekFromPointer(moveEvent.clientX);
      if (val !== null) setScrubMs(val);
    };
    const onUp = (upEvent) => {
      window.removeEventListener('pointermove', onMove);
      window.removeEventListener('pointerup', onUp);
      const finalVal = seekFromPointer(upEvent.clientX) ?? startVal;
      setScrubMs(null);
      setOriginMs(null);
      onSeekCommit?.(finalVal);
    };
    window.addEventListener('pointermove', onMove);
    window.addEventListener('pointerup', onUp);
  };

  const clampedPopupPct = Math.max(6, Math.min(94, fraction * 100));

  return (
    <div className="seekbar-container">
      <div
        ref={(node) => {
          trackRef.current = node;
          if (typeof seekBarRef === 'function') seekBarRef(node);
          else if (seekBarRef) seekBarRef.current = node;
        }}
        role="slider"
        tabIndex={0}
        aria-valuemin={0}
        aria-valuemax={safeDuration}
        aria-valuenow={displayMs}
        data-focusable="true"
        data-focus-zone="player"
        data-seekbar-capture="true"
        className="seekbar-track-wrap selectable-pulse"
        style={{ borderRadius: '9999px', paddingInline: '4px' }}
        onFocus={() => setIsFocused(true)}
        onBlur={() => {
          setIsFocused(false);
          lastDirRef.current = 0;
          holdStartRef.current = 0;
        }}
        onKeyDown={handleKeyDown}
        onKeyUp={handleKeyUp}
        onPointerDown={handlePointerDown}
      >
        <div className="seekbar-track">
          <div
            className="seekbar-fill"
            style={{ width: `${(fraction * 100).toFixed(2)}%` }}
          />
          {targetFraction !== null && isBuffering && (
            <div
              className="seekbar-preview"
              style={{
                left: `${(Math.min(fraction, targetFraction) * 100).toFixed(2)}%`,
                width: `${(Math.abs(targetFraction - fraction) * 100).toFixed(2)}%`,
              }}
            />
          )}
          {originFraction !== null && (
            <div
              className="seekbar-origin-mark"
              style={{ left: `${(originFraction * 100).toFixed(2)}%` }}
            />
          )}
          {(isFocused || scrubMs !== null) && (
            <div
              className="seekbar-thumb"
              style={{ left: `${(fraction * 100).toFixed(2)}%` }}
            />
          )}
        </div>

        {(isFocused || scrubMs !== null) && safeDuration > 0 && (
          <div
            className="seekbar-time-popup type-label-medium"
            style={{ left: `${clampedPopupPct.toFixed(2)}%` }}
          >
            {formatTimeMs(displayMs)}
          </div>
        )}
      </div>

      <div className="seekbar-times-row type-label-medium">
        <span>{formatTimeMs(displayMs)}</span>
        <span>{formatTimeMs(safeDuration)}</span>
      </div>
    </div>
  );
}
