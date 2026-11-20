import React from 'react';

export default function EmptySection({ message }) {
  return (
    <div className="empty-section">
      <p className="type-title-medium">{message}</p>
    </div>
  );
}
