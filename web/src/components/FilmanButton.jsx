import React from 'react';

export default function FilmanButton({
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
      ? 'filman-btn--secondary'
      : variant === 'primaryFocus'
        ? 'filman-btn--primary-focus'
        : variant === 'danger'
          ? 'filman-btn--danger'
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
      className={`filman-btn selectable-pulse ${variantClass} ${fullWidth ? 'filman-btn--full' : ''} ${className}`}
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
