import test from 'node:test';
import assert from 'node:assert/strict';

// Setup mock window and history for node test runner
const historyEntries = [{ state: { depth: 1 } }];
let historyPointer = 0;

globalThis.window = {
  history: {
    get state() {
      return historyEntries[historyPointer]?.state || null;
    },
    pushState(state, _unused, _url) {
      historyPointer += 1;
      historyEntries.splice(historyPointer, historyEntries.length - historyPointer, { state });
    },
    go(delta) {
      historyPointer = Math.max(0, Math.min(historyEntries.length - 1, historyPointer + delta));
    },
    back() {
      this.go(-1);
    },
  },
};

const {
  pushOverlayHistoryState,
  popOverlayHistoryByUI,
  popAllOverlayHistoryByUI,
  handleOverlayPopstate,
  hasActiveOverlay,
  resetOverlayHistory,
  isHandlingPopstate,
} = await import('../src/components/overlayHistoryManager.js');

test('pushOverlayHistoryState pushes browser state and registers dismiss callback', () => {
  resetOverlayHistory();
  const initialPointer = historyPointer;

  let dismissed = false;
  pushOverlayHistoryState(() => {
    dismissed = true;
  });

  assert.equal(hasActiveOverlay(), true);
  assert.equal(historyPointer, initialPointer + 1);
  assert.equal(window.history.state?.__tiwiOverlay, true);
  assert.equal(dismissed, false);
});

test('handleOverlayPopstate consumes popstate and executes dismiss callback', () => {
  resetOverlayHistory();

  let dismissCallCount = 0;
  pushOverlayHistoryState(() => {
    dismissCallCount += 1;
  });

  assert.equal(hasActiveOverlay(), true);

  // Simulate system back button (browser already popped history entry)
  window.history.back();

  const handled = handleOverlayPopstate();
  assert.equal(handled, true);
  assert.equal(dismissCallCount, 1);
  assert.equal(hasActiveOverlay(), false);

  // Subsequent popstate should not be handled by overlay
  const nextHandled = handleOverlayPopstate();
  assert.equal(nextHandled, false);
});

test('nested submenus pop in reverse order via system back', () => {
  resetOverlayHistory();

  const order = [];
  pushOverlayHistoryState(() => order.push('root'));
  pushOverlayHistoryState(() => order.push('level1'));
  pushOverlayHistoryState(() => order.push('level2'));

  assert.equal(hasActiveOverlay(), true);

  // System back 1: pops level2
  window.history.back();
  assert.equal(handleOverlayPopstate(), true);
  assert.deepEqual(order, ['level2']);
  assert.equal(hasActiveOverlay(), true);

  // System back 2: pops level1
  window.history.back();
  assert.equal(handleOverlayPopstate(), true);
  assert.deepEqual(order, ['level2', 'level1']);
  assert.equal(hasActiveOverlay(), true);

  // System back 3: pops root
  window.history.back();
  assert.equal(handleOverlayPopstate(), true);
  assert.deepEqual(order, ['level2', 'level1', 'root']);
  assert.equal(hasActiveOverlay(), false);

  // System back 4: no overlay, passes to screen router
  assert.equal(handleOverlayPopstate(), false);
});

test('popOverlayHistoryByUI rewinds history and ignores subsequent popstate', () => {
  resetOverlayHistory();

  let dismissCalled = false;
  pushOverlayHistoryState(() => {
    dismissCalled = true;
  });

  const ptrBeforeClose = historyPointer;
  popOverlayHistoryByUI(1);

  // History pointer should have rewound
  assert.equal(historyPointer, ptrBeforeClose - 1);
  assert.equal(hasActiveOverlay(), false);

  // The popstate event fired by browser for this go(-1) should be ignored
  const handled = handleOverlayPopstate();
  assert.equal(handled, true); // ignored UI pop
  assert.equal(dismissCalled, false); // UI already closed it, don't re-dismiss

  // Next real popstate is not ignored
  assert.equal(handleOverlayPopstate(), false);
});

test('popAllOverlayHistoryByUI clears multiple levels at once cleanly', () => {
  resetOverlayHistory();

  let count = 0;
  pushOverlayHistoryState(() => { count += 1; });
  pushOverlayHistoryState(() => { count += 1; });
  pushOverlayHistoryState(() => { count += 1; });

  const ptr = historyPointer;
  popAllOverlayHistoryByUI();

  assert.equal(historyPointer, ptr - 3);
  assert.equal(hasActiveOverlay(), false);

  // All 3 pops from history.go(-3) are safely ignored
  assert.equal(handleOverlayPopstate(), true);
  assert.equal(handleOverlayPopstate(), true);
  assert.equal(handleOverlayPopstate(), true);
  assert.equal(handleOverlayPopstate(), false);
  assert.equal(count, 0);
});

test('resetOverlayHistory clears internal state during screen navigation', () => {
  resetOverlayHistory();

  pushOverlayHistoryState(() => {});
  pushOverlayHistoryState(() => {});
  assert.equal(hasActiveOverlay(), true);

  resetOverlayHistory();
  assert.equal(hasActiveOverlay(), false);
  assert.equal(handleOverlayPopstate(), false);
});
