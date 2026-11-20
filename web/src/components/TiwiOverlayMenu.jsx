import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { t } from '../i18n/index.js';
import TiwiButton from './TiwiButton.jsx';
import TiwiIconButton from './TiwiIconButton.jsx';
import { IconBack, IconCheck, IconDelete, IconMoreVert, IconSwap } from './Icons.jsx';

function findMenuPath(items, targetId) {
  if (!targetId || !Array.isArray(items)) return null;
  for (const item of items) {
    if (item.type === 'nested') {
      if (item.id === targetId) return [item];
      const sub = findMenuPath(item.items, targetId);
      if (sub) return [item, ...sub];
    } else if (item.type === 'reorderable' && Array.isArray(item.trailingButtons)) {
      for (const tb of item.trailingButtons) {
        if (tb.type === 'nested') {
          if (tb.id === targetId) return [{ id: tb.id, title: item.label, items: tb.items }];
          const sub = findMenuPath(tb.items, targetId);
          if (sub) return [{ id: tb.id, title: item.label, items: tb.items }, ...sub];
        }
      }
    }
  }
  return null;
}

function findNestedContainer(items, id, label) {
  if (!Array.isArray(items)) return null;
  for (const item of items) {
    if (item.type === 'nested' && ((id && item.id === id) || item.label === label)) {
      return { id: item.id, title: item.label, items: item.items };
    }
    if (item.type === 'reorderable' && Array.isArray(item.trailingButtons)) {
      for (const tb of item.trailingButtons) {
        if (tb.type === 'nested' && ((id && tb.id === id) || item.label === label)) {
          return { id: tb.id, title: item.label, items: tb.items };
        }
      }
    }
  }
  return null;
}

export default function TiwiOverlayMenu({ menuData, onClose, onResetInactivity }) {
  const [stack, setStack] = useState([]);
  const [slideDir, setSlideDir] = useState(1);
  const [reorderingId, setReorderingId] = useState(null);
  const listRef = useRef(null);
  const prevTitleRef = useRef(null);

  // Initialize or reconcile stack whenever menuData changes
  useEffect(() => {
    if (!menuData) {
      setStack([]);
      prevTitleRef.current = null;
      return;
    }

    setStack((prevStack) => {
      const rootEntry = {
        id: 'root',
        title: menuData.title,
        items: menuData.items || [],
      };

      // First open or root menu title changed
      if (prevStack.length === 0 || prevTitleRef.current !== menuData.title) {
        prevTitleRef.current = menuData.title;
        if (menuData.initialMenuId) {
          const path = findMenuPath(menuData.items || [], menuData.initialMenuId);
          if (path && path.length > 0) {
            return [
              rootEntry,
              ...path.map((node) => ({
                id: node.id,
                title: node.title || node.label,
                items: node.items || [],
              })),
            ];
          }
        }
        return [rootEntry];
      }

      // Live reconciliation of open stack levels
      const nextStack = [rootEntry];
      let currentItems = rootEntry.items;
      for (let i = 1; i < prevStack.length; i++) {
        const level = prevStack[i];
        const found = findNestedContainer(currentItems, level.id, level.title);
        if (!found) break;
        nextStack.push(found);
        currentItems = found.items;
      }
      return nextStack;
    });
  }, [menuData]);

  const currentLevel = stack[stack.length - 1] || null;
  const isRootMenu = stack.length <= 1;

  const popBack = useCallback(() => {
    setReorderingId(null);
    if (stack.length <= 1) {
      onClose?.();
      return;
    }
    setSlideDir(-1);
    setStack((prev) => prev.slice(0, -1));
  }, [stack.length, onClose]);

  const pushSubmenu = useCallback((id, title, items) => {
    setReorderingId(null);
    setSlideDir(1);
    setStack((prev) => [...prev, { id, title, items: items || [] }]);
  }, []);

  // Auto-focus first clickable item on open / push / pop
  useEffect(() => {
    if (!currentLevel) return undefined;
    const timer = setTimeout(() => {
      const first = listRef.current?.querySelector('[data-focusable="true"]');
      first?.focus();
    }, 60);
    return () => clearTimeout(timer);
  }, [stack.length, currentLevel?.id, currentLevel?.title]);

  // Handle Escape / Backspace inside drawer
  useEffect(() => {
    if (!menuData) return undefined;
    const onKeyDown = (e) => {
      onResetInactivity?.();
      if (e.key === 'Escape' || (e.key === 'Backspace' && document.activeElement?.tagName !== 'INPUT')) {
        e.preventDefault();
        e.stopPropagation();
        if (reorderingId) {
          setReorderingId(null);
        } else {
          popBack();
        }
      }
    };
    window.addEventListener('keydown', onKeyDown, true);
    return () => window.removeEventListener('keydown', onKeyDown, true);
  }, [menuData, popBack, reorderingId, onResetInactivity]);

  const scope = useMemo(() => ({ popBack }), [popBack]);

  if (!menuData || !currentLevel) return null;

  return (
    <div
      className="overlay-backdrop"
      onClick={(e) => {
        onResetInactivity?.();
        if (e.target === e.currentTarget) onClose?.();
      }}
      onPointerMove={() => onResetInactivity?.()}
    >
      <aside className="overlay-drawer" role="dialog" aria-modal="true" aria-label={currentLevel.title}>
        <div className="overlay-title-bar gradient-background-invert">
          {!isRootMenu && (
            <TiwiButton
              text={t('overlay_menu_back')}
              icon={<IconBack size={18} />}
              onClick={popBack}
              focusZone="overlay"
            />
          )}
          <h2 className="overlay-title-bar__text type-title-large">{currentLevel.title}</h2>
        </div>

        <div
          key={`${stack.length}_${currentLevel.id}_${currentLevel.title}`}
          ref={listRef}
          className={`overlay-list ${
            slideDir >= 0 ? 'overlay-list--slide-forward' : 'overlay-list--slide-back'
          }`}
        >
          {(currentLevel.items || []).map((item, idx) => {
            const key = item.id || `${item.type}_${idx}`;

            if (item.type === 'header') {
              return (
                <div key={key} className="overlay-header type-title-small">
                  {item.label}
                </div>
              );
            }

            if (item.type === 'footer') {
              return (
                <div key={key} className="overlay-footer type-body-small">
                  {item.label}
                </div>
              );
            }

            if (item.type === 'custom') {
              return <React.Fragment key={key}>{item.render()}</React.Fragment>;
            }

            if (item.type === 'button') {
              return (
                <button
                  key={key}
                  type="button"
                  data-focusable="true"
                  data-focus-zone="overlay"
                  className="overlay-item selectable-pulse"
                  onClick={() => item.onClick?.(scope)}
                >
                  <div className="overlay-item__text">
                    <div className="overlay-item__label type-body-large">{item.label}</div>
                    {item.value && (
                      <div className="overlay-item__sub type-body-small">{item.value}</div>
                    )}
                  </div>
                </button>
              );
            }

            if (item.type === 'option') {
              return (
                <button
                  key={key}
                  type="button"
                  role="radio"
                  aria-checked={Boolean(item.isSelected)}
                  data-focusable="true"
                  data-focus-zone="overlay"
                  className={`overlay-item selectable-pulse ${item.isSelected ? 'is-selected' : ''}`}
                  onClick={() => item.onClick?.(scope)}
                >
                  <div className="overlay-item__text">
                    <div className="overlay-item__label type-body-large">{item.label}</div>
                    {item.value && (
                      <div className="overlay-item__sub type-body-small">{item.value}</div>
                    )}
                  </div>
                  <span className={`overlay-radio ${item.isSelected ? 'is-checked' : ''}`} />
                </button>
              );
            }

            if (item.type === 'nested') {
              return (
                <button
                  key={key}
                  type="button"
                  data-focusable="true"
                  data-focus-zone="overlay"
                  className="overlay-item selectable-pulse"
                  onClick={() => pushSubmenu(item.id, item.label, item.items)}
                >
                  <div className="overlay-item__text">
                    <div className="overlay-item__label type-body-large">{item.label}</div>
                  </div>
                  {item.value && (
                    <span className="overlay-item__trailing type-body-medium">{item.value}</span>
                  )}
                </button>
              );
            }

            if (item.type === 'reorderable') {
              const isReordering = reorderingId === key;
              return (
                <div key={key} className="overlay-reorder-row">
                  <button
                    type="button"
                    data-focusable="true"
                    data-focus-zone="overlay"
                    className={`overlay-item selectable-pulse ${isReordering ? 'is-selected' : ''}`}
                    style={{ flex: 1 }}
                    onClick={() => setReorderingId(isReordering ? null : key)}
                    onKeyDown={(e) => {
                      if (!isReordering) return;
                      if (e.key === 'ArrowUp') {
                        e.preventDefault();
                        e.stopPropagation();
                        item.onMoveUp?.();
                      } else if (e.key === 'ArrowDown') {
                        e.preventDefault();
                        e.stopPropagation();
                        item.onMoveDown?.();
                      }
                    }}
                  >
                    <div className="overlay-item__text">
                      <div className="overlay-item__label type-body-large">{item.label}</div>
                      {item.value && (
                        <div className="overlay-item__sub type-body-small">{item.value}</div>
                      )}
                    </div>
                    {isReordering && (
                      <span className="overlay-item__trailing">
                        <IconSwap size={18} />
                      </span>
                    )}
                  </button>
                  {(item.trailingButtons || []).map((tb, tbIdx) => (
                    <TiwiIconButton
                      key={tb.id || tbIdx}
                      variant="transparent"
                      tooltip={tb.contentDescription}
                      focusZone="overlay"
                      icon={
                        tb.type === 'toggle' ? (
                          tb.isEnabled ? <IconCheck size={20} /> : <IconDelete size={20} />
                        ) : (
                          <IconMoreVert size={20} />
                        )
                      }
                      onClick={() => {
                        if (tb.type === 'toggle') tb.onToggle?.();
                        else pushSubmenu(tb.id, item.label, tb.items);
                      }}
                    />
                  ))}
                </div>
              );
            }

            return null;
          })}
        </div>
      </aside>
    </div>
  );
}
