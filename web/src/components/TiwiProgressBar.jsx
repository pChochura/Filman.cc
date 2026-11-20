import React from 'react';

export default function TiwiProgressBar({ progress = 0, className = '' }) {
  const clamped = Math.max(0, Math.min(1, Number(progress) || 0));
  return (
    <div className={`tiwi-progress-bar ${className}`}>
      <div
        className="tiwi-progress-bar__fill"
        style={{ width: `${(clamped * 100).toFixed(2)}%` }}
      />
    </div>
  );
}
