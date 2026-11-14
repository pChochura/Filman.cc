const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const source = fs.readFileSync(
    path.join(__dirname, '../../main/java/com/pointlessapps/filman/FilmanApp.kt'),
    'utf8',
);
const match = source.match(/private suspend fun WebView\.centerChallengeControl\(\): Boolean[\s\S]*?evaluateJavascript\(\s*"""([\s\S]*?)"""/);
assert.ok(match, 'challenge centering script must exist');
const script = match[1];

function run(controls) {
    let selector;
    const result = vm.runInNewContext(script, {
        document: { querySelectorAll: value => { selector = value; return controls; } },
        window: { getComputedStyle: control => control.style },
    });
    return { result, selector };
}

function control({ display = 'block', visibility = 'visible', width = 300, height = 65 } = {}) {
    const scrolls = [];
    return {
        style: { display, visibility },
        getBoundingClientRect: () => ({ width, height }),
        scrollIntoView: options => scrolls.push({ ...options }),
        scrolls,
    };
}

test('centers a visible Cloudflare control without moving it or changing the page', () => {
    const hidden = control({ display: 'none' });
    const visible = control();
    const { result, selector } = run([hidden, visible]);
    assert.equal(result, true);
    assert.match(selector, /iframe\[src\*="challenges\.cloudflare\.com"\]/);
    assert.deepEqual(hidden.scrolls, []);
    assert.deepEqual(visible.scrolls, [{ block: 'center', inline: 'center' }]);
});

test('leaves the page unchanged when no usable control exists', () => {
    const hidden = control({ visibility: 'hidden' });
    const empty = control({ width: 0 });
    assert.equal(run([hidden, empty]).result, false);
    assert.deepEqual(hidden.scrolls, []);
    assert.deepEqual(empty.scrolls, []);
    assert.equal(run([]).result, false);
});

test('verification recognizes localized managed challenges even without a visible iframe', () => {
    const match = source.match(/private suspend fun WebView\.awaitChallengeStatus\(\): String[\s\S]*?evaluateJavascript\(\s*"""([\s\S]*?)"""/);
    assert.ok(match);
    const document = {
        title: 'Przeprowadzanie weryfikacji zabezpieczeń',
        readyState: 'complete',
        body: { innerText: 'Ta witryna sprawdza, czy nie jesteś botem.' },
        querySelector: () => null,
        querySelectorAll: () => [],
    };
    const context = { document, window: { _cf_chl_opt: {} } };
    assert.equal(vm.runInNewContext(match[1], context), 'challenge');
    context.window = {};
    context.document.querySelector = selector => selector.startsWith('#challenge-stage,') ? {} : null;
    assert.equal(vm.runInNewContext(match[1], context), 'challenge');
    context.document.querySelector = selector => selector.includes('/cdn-cgi/challenge-platform/') ? {} : null;
    assert.equal(vm.runInNewContext(match[1], context), 'clear');
});

test('only visible buttons on managed challenge pages require interaction', () => {
    const match = source.match(/private suspend fun WebView\.awaitChallengeStatus\(\): String[\s\S]*?evaluateJavascript\(\s*"""([\s\S]*?)"""/);
    assert.ok(match);
    const button = control();
    const document = {
        title: 'Przeprowadzanie weryfikacji zabezpieczeń',
        readyState: 'complete',
        body: { innerText: 'Verification' },
        querySelector: () => null,
        querySelectorAll: () => [control({ display: 'none' }), button],
    };
    const context = {
        document,
        window: { _cf_chl_opt: {}, getComputedStyle: element => element.style },
    };
    assert.equal(vm.runInNewContext(match[1], context), 'interactive');
    context.window._cf_chl_opt = null;
    assert.equal(vm.runInNewContext(match[1], context), 'clear');
});
