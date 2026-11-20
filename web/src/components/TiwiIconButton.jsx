import React from 'react';

export default function TiwiIconButton({
  icon,
  tooltip,
  tooltipPosition = 'above',
  onClick,
  variant = 'default',
  size = 'md',
  primaryTint = false,
  focusKey,
  focusZone = 'content',
  buttonRef,
  className = '',
  ...rest
}) {
  const variantClass =
    variant === 'transparent'
      ? 'tiwi-icon-btn--transparent'
      : variant === 'surface'
        ? 'tiwi-icon-btn--surface'
        : variant === 'subtle'
          ? 'tiwi-icon-btn--subtle'
          : '';
  const sizeClass =
    size === 'lg' ? 'tiwi-icon-btn--lg' : size === 'sm' ? 'tiwi-icon-btn--sm' : '';

  return (
    <div className="tiwi-icon-btn-wrap">
      <button
        ref={buttonRef}
        type="button"
        onClick={onClick}
        aria-label={tooltip}
        data-focusable="true"
        data-focus-key={focusKey}
        data-focus-zone={focusZone}
        className={`tiwi-icon-btn selectable-pulse ${variantClass} ${sizeClass} ${
          primaryTint ? 'tiwi-icon-btn--primary-tint' : ''
        } ${className}`}
        {...rest}
      >
        {icon}
      </button>
      {tooltip && (
        <span
          className={`tiwi-tooltip ${
            tooltipPosition === 'below' ? 'tiwi-tooltip--below' : 'tiwi-tooltip--above'
          }`}
        >
          {tooltip}
        </span>
      )}
    </div>
  );
}
