import React from 'react';

export default function SectionHeader({ title, action = null }) {
  if (!title && !action) return null;
  return (
    <div className="section-header-row">
      {title && <h2 className="section-header type-title-large">{title}</h2>}
      {action && <div className="section-header-action">{action}</div>}
    </div>
  );
}
