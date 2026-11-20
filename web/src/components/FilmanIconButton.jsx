import React from 'react';

export default function FilmanIconButton({
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
      ? 'filman-icon-btn--transparent'
      : variant === 'surface'
        ? 'filman-icon-btn--surface'
        : variant === 'subtle'
          ? 'filman-icon-btn--subtle'
          : '';
  const sizeClass =
    size === 'lg' ? 'filman-icon-btn--lg' : size === 'sm' ? 'filman-icon-btn--sm' : '';

  return (
    <div className="filman-icon-btn-wrap">
      <button
        ref={buttonRef}
        type="button"
        onClick={onClick}
        aria-label={tooltip}
        data-focusable="true"
        data-focus-key={focusKey}
        data-focus-zone={focusZone}
        className={`filman-icon-btn selectable-pulse ${variantClass} ${sizeClass} ${
          primaryTint ? 'filman-icon-btn--primary-tint' : ''
        } ${className}`}
        {...rest}
      >
        {icon}
      </button>
      {tooltip && (
        <span
          className={`filman-tooltip ${
            tooltipPosition === 'below' ? 'filman-tooltip--below' : 'filman-tooltip--above'
          }`}
        >
          {tooltip}
        </span>
      )}
    </div>
  );
}
