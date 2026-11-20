import React from 'react';

export default function TiwiButton({
  text,
  icon = null,
  onClick,
  onLongClick,
  enabled = true,
  isLoading = false,
  fullWidth = false,
  variant = 'default',
  focusKey,
  focusZone = 'content',
  focusGroup,
  focusWrap = false,
  className = '',
  buttonRef,
  ...rest
}) {
  const variantClass =
    variant === 'secondary'
      ? 'tiwi-btn--secondary'
      : variant === 'primaryFocus'
        ? 'tiwi-btn--primary-focus'
        : variant === 'danger'
          ? 'tiwi-btn--danger'
          : '';

  return (
    <button
      ref={buttonRef}
      type="button"
      disabled={!enabled || isLoading}
      onClick={onClick}
      onContextMenu={
        onLongClick
          ? (e) => {
              e.preventDefault();
              onLongClick();
            }
          : undefined
      }
      data-focusable="true"
      data-focus-key={focusKey}
      data-focus-zone={focusZone}
      data-focus-group={focusGroup}
      data-focus-wrap={focusWrap ? 'true' : undefined}
      data-has-context-menu={onLongClick ? 'true' : undefined}
      className={`tiwi-btn selectable-pulse ${variantClass} ${fullWidth ? 'tiwi-btn--full' : ''} ${className}`}
      {...rest}
    >
      {isLoading ? (
        <span className="spinner spinner--sm" />
      ) : (
        <>
          {icon}
          <span>{text}</span>
        </>
      )}
    </button>
  );
}
