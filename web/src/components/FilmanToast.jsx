import React, { useEffect } from 'react';

export default function FilmanToast({ message, onDismiss, durationMs = 3000 }) {
  useEffect(() => {
    if (!message) return undefined;
    const timer = setTimeout(() => {
      onDismiss?.();
    }, durationMs);
    return () => clearTimeout(timer);
  }, [message, durationMs, onDismiss]);

  if (!message) return null;

  return (
    <div className="filman-toast type-body-medium" role="status" aria-live="polite">
      {message}
    </div>
  );
}
