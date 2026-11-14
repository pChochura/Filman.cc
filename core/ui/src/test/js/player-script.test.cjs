const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const source = fs.readFileSync(path.join(__dirname, '../../main/java/com/pointlessapps/filman/ui/core/WebViewScripts.kt'), 'utf8');
const policySource = fs.readFileSync(path.join(__dirname, '../../main/java/com/pointlessapps/filman/ui/core/PlayerAdPolicy.kt'), 'utf8');
const hostBlock = policySource.match(/playerAdHosts = setOf\(([\s\S]*?)\)/);
assert.ok(hostBlock);
const adHosts = [...hostBlock[1].matchAll(/"([^"]+)"/g)].map(match => match[1]);
const start = source.indexOf('    function isAdVideo(video)');
const end = source.indexOf('    function tryPlay(video)', start);
assert.ok(start !== -1 && end > start);
const playerFunctions = source.slice(start, end);

test('managed challenge remains visible until its page is replaced', () => {
    const begin = source.indexOf('    function isManagedCloudflareChallenge()');
    const end = source.indexOf('    function removePlayerStyle()', begin);
    assert.ok(begin !== -1 && end > begin);
    let token = null;
    let script = false;
    const context = {
        window: { _cf_chl_opt: {} },
        document: {
            title: 'Przeprowadzanie weryfikacji zabezpieczeń',
            querySelector: selector => {
                if (selector.startsWith('input[')) return token;
                if (selector.includes('/cdn-cgi/challenge-platform/')) return script ? {} : null;
                return null;
            },
            getElementById: () => null,
        },
    };
    vm.createContext(context);
    vm.runInContext(source.slice(begin, end), context);
    assert.equal(context.checkIsCloudflare(), true);
    token = { value: 'verified' };
    assert.equal(context.checkIsCloudflare(), true);
    context.window._cf_chl_opt = null;
    script = true;
    assert.equal(context.checkIsCloudflare(), true);
    script = false;
    assert.equal(context.checkIsCloudflare(), false);
});

test('active challenges do not trigger automatic checkbox clicks or repeated scrolling', () => {
    const begin = source.indexOf('    var captchaInterval = setInterval(function()');
    const end = source.indexOf('    }, 600);', begin);
    assert.ok(begin !== -1 && end > begin);
    let callback;
    let clicks = 0;
    let scrolls = 0;
    const frame = {
        scrollIntoView: () => scrolls++,
        getBoundingClientRect: () => ({ left: 0, top: 0, width: 300, height: 65 }),
    };
    const context = {
        window: { _hasCaptchaFlag: true },
        document: { querySelector: selector => selector.startsWith('iframe') ? frame : null },
        location: { href: 'https://provider.example/' },
        AndroidBridge: { onCaptchaFound: () => clicks++ },
        checkIsCloudflare: () => true,
        isManagedCloudflareChallenge: () => true,
        setInterval: fn => { callback = fn; },
    };
    vm.runInNewContext(source.slice(begin, end + '    }, 600);'.length), context);
    callback();
    callback();
    assert.equal(clicks, 0);
    assert.equal(scrolls, 0);
});

function fixture() {
    const calls = [];
    const videos = [];
    const context = {
        URL,
        filmanAdHosts: adHosts,
        location: { href: 'https://player.example/watch' },
        window: {},
        document: { querySelectorAll: () => videos.filter(video => video.classes.has('filman-content-video')) },
        AndroidBridge: {
            onVideoReady: url => calls.push(['ready', url]),
            onAdDetected: url => calls.push(['ad', url]),
            onTimeUpdate: () => calls.push(['time']),
            onPlayStateChanged: () => {},
            onBufferingChanged: () => {},
        },
        checkIsCloudflare: () => false,
        uncoverVideo: video => video.style.setProperty('visibility', video._filmanReady ? 'visible' : 'hidden'),
        ensureUnmuted: () => {},
        tryPlay: () => {},
    };
    vm.createContext(context);
    vm.runInContext(playerFunctions, context);
    function video() {
        const listeners = {};
        const classes = new Set();
        const properties = {};
        const item = {
            currentSrc: 'https://cdn.example/content.mp4',
            paused: false, readyState: 2, videoWidth: 1280, videoHeight: 720, currentTime: 0,
            duration: 7200, muted: false, volume: 1, classes, properties,
            classList: { add: value => classes.add(value), remove: value => classes.delete(value) },
            style: { setProperty: (key, value) => { properties[key] = value; } },
            closest: () => null,
            removeAttribute: () => {},
            addEventListener: (name, callback) => { listeners[name] = callback; },
            emit: name => listeners[name](),
        };
        videos.push(item);
        context.hookVideo(item);
        return item;
    }
    return { context, calls, video };
}

test('only advancing, decoded playback reveals a video', () => {
    const { calls, video } = fixture();
    const item = video();
    item.emit('playing');
    item.emit('timeupdate');
    item.currentTime = 1;
    item.readyState = 1;
    item.emit('timeupdate');
    item.readyState = 2;
    item.videoWidth = 0;
    item.emit('timeupdate');
    assert.equal(calls.filter(call => call[0] === 'ready').length, 0);
    item.videoWidth = 1280;
    item.emit('timeupdate');
    assert.equal(calls.filter(call => call[0] === 'ready').length, 1);
    assert.equal(item.properties.visibility, 'visible');
});

test('recognized ads hide the video and do not publish progress', () => {
    const { calls, video } = fixture();
    const item = video();
    item.currentTime = 1;
    item.emit('timeupdate');
    item.closest = () => ({});
    const count = calls.length;
    item.emit('timeupdate');
    assert.deepEqual(calls.slice(count).map(call => call[0]), ['ad']);
    assert.equal(item.properties.visibility, 'hidden');
    item.closest = () => null;
    item.emit('timeupdate');
    assert.equal(calls.filter(call => call[0] === 'ready').length, 2);
});

test('frame URL policy rejects ad domains without rejecting lookalike domains', () => {
    const { context } = fixture();
    assert.equal(context.isPlayerFrameSource('https://x.doubleclick.net/embed'), false);
    assert.equal(context.isPlayerFrameSource('https://DOUBLECLICK.NET./embed'), false);
    assert.equal(context.isPlayerFrameSource('https://doubleclick.net.example/embed'), true);
    assert.equal(context.isPlayerFrameSource('https://challenges.cloudflare.com/frame'), false);
    assert.equal(context.isPlayerFrameSource('javascript:alert(1)'), false);
    assert.equal(context.isPlayerFrameSource('/embed/123'), true);
});

test('only player-container frames are considered and ad frames are skipped', () => {
    const { context } = fixture();
    const marked = [];
    function frame(src, isAd) {
        return {
            getAttribute: name => name === 'src' ? src : null,
            closest: () => isAd ? {} : null,
            classList: { add: () => marked.push(src) },
        };
    }
    const frames = [
        frame('https://doubleclick.net/ad', false),
        frame('https://cdn.example/ad', true),
        frame('/embed/player', false),
    ];
    context.findBestVideo = () => null;
    context.document.querySelectorAll = selector => {
        assert.match(selector, /#player iframe/);
        assert.notEqual(selector, 'iframe');
        return frames;
    };
    const start = source.indexOf('    function isolateIframePlayer()');
    const end = source.indexOf('    // Check for existing video or iframe player', start);
    assert.ok(start !== -1 && end > start);
    vm.runInContext(source.slice(start, end), context);
    context.isolateIframePlayer();
    assert.deepEqual(marked, ['/embed/player']);
});

test('play controls are clicked at most once and links are never clicked', () => {
    const { context } = fixture();
    let clicks = 0;
    const button = { closest: () => null, click: () => clicks++ };
    context.clickPlayerButton(button);
    context.clickPlayerButton(button);
    context.clickPlayerButton({ closest: () => ({}), click: () => clicks++ });
    assert.equal(clicks, 1);
});

test('all provider scripts parse together with the base script', () => {
    function script(name) {
        const match = source.match(new RegExp(`const val ${name} = """([\\s\\S]*?)"""`));
        assert.ok(match, `${name} must exist`);
        return match[1];
    }
    const base = script('PLAYER_BASE_SCRIPT');
    for (const name of [
        'EKINO_INTERMEDIATE_SCRIPT', 'DOODSTREAM_SCRIPT', 'VIDMOLY_SCRIPT',
        'STREAMSB_SCRIPT', 'GENERIC_IFRAME_SCRIPT', 'GENERIC_FALLBACK_SCRIPT',
    ]) {
        assert.doesNotThrow(() => new vm.Script(`(function() {\n${base}\n${script(name)}\n})();`), name);
    }
});

test('document-start audio gate silences loading and ads, then restores only the verified video', async () => {
    class Media {
        constructor(tag) {
            this.tagName = tag;
            this.classes = new Set();
            this.classList = { contains: value => this.classes.has(value) };
            this.volume = 1;
            this._muted = false;
        }
        get muted() { return this._muted; }
        set muted(value) { this._muted = value; }
        play() { return Promise.resolve(); }
    }
    const video = new Media('VIDEO');
    const ad = new Media('VIDEO');
    const audio = new Media('AUDIO');
    const elements = [video, ad, audio];
    class AudioContext {
        constructor() { this.state = 'running'; }
        suspend() { this.state = 'suspended'; return Promise.resolve(); }
        resume() { this.state = 'running'; return Promise.resolve(); }
    }
    const context = {
        HTMLMediaElement: Media,
        document: {
            addEventListener: () => {},
            querySelectorAll: selector => selector === 'audio, video'
                ? elements : elements.filter(el => el.classes.has('filman-content-video')),
        },
        MutationObserver: class { observe() {} },
        window: { AudioContext },
    };
    vm.createContext(context);
    const match = source.match(/const val PLAYER_AUDIO_GATE_SCRIPT = """([\s\S]*?)"""/);
    assert.ok(match);
    vm.runInContext(match[1], context);
    const audioContext = new context.window.AudioContext();
    assert.equal(audioContext.state, 'suspended');
    await audioContext.resume();
    assert.equal(audioContext.state, 'suspended');
    for (const media of elements) {
        media.muted = false;
        await media.play();
        assert.equal(media.muted, true);
    }
    video.classes.add('filman-content-video');
    context.window.__filmanUnlockAudio();
    assert.equal(video.muted, false);
    assert.equal(audioContext.state, 'running');
    ad.muted = false;
    audio.muted = false;
    assert.equal(ad.muted, true);
    assert.equal(audio.muted, true);
    context.window.__filmanLockAudio();
    assert.equal(video.muted, true);
    assert.equal(audioContext.state, 'suspended');
    video.muted = false;
    assert.equal(video.muted, true);
});
