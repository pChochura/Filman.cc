import React from 'react';

export default function FilmanProgressBar({ progress = 0, className = '' }) {
  const clamped = Math.max(0, Math.min(1, Number(progress) || 0));
  return (
    <div className={`filman-progress-bar ${className}`}>
      <div
        className="filman-progress-bar__fill"
        style={{ width: `${(clamped * 100).toFixed(2)}%` }}
      />
    </div>
  );
}
