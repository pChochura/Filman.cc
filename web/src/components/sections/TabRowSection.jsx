import React, { useRef } from 'react';

export default function TabRowSection({
  tabs = [],
  selectedId,
  onSelectTab,
  focusZone = 'content',
  className = '',
}) {
  const pointerDownRef = useRef(false);
  if (!tabs || tabs.length === 0) return null;

  return (
    <div className={`tab-row ${className}`.trim()} role="tablist">
      {tabs.map((tab) => {
        const isSelected = tab.id === selectedId;
        return (
          <button
            key={tab.id}
            type="button"
            role="tab"
            aria-selected={isSelected}
            data-focusable="true"
            data-focus-zone={focusZone}
            data-focus-group="tab_row"
            className={`tab-item type-title-medium ${isSelected ? 'is-selected' : ''}`}
            onPointerDown={() => {
              pointerDownRef.current = true;
            }}
            onFocus={() => {
              if (pointerDownRef.current) {
                pointerDownRef.current = false;
                return;
              }
              if (!isSelected) onSelectTab?.(tab.id);
            }}
            onClick={() => onSelectTab?.(tab.id)}
          >
            {tab.label}
          </button>
        );
      })}
    </div>
  );
}
