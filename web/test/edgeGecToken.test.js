import test from 'node:test';
import assert from 'node:assert/strict';
import { TRUSTED_CLIENT_TOKEN, generateGecToken } from '../server/edgeGecToken.js';
import {
  buildSsmlMessage,
  parseAudioFrame,
  parseWordBoundaryRangeMs,
} from '../server/edgeTtsClient.js';
import { rewriteHlsPlaylist } from '../server/proxyHandler.js';

test('generateGecToken matches exact Edge GEC v2 test vectors from Android suite', () => {
  assert.equal(
    generateGecToken(1_700_000_000, TRUSTED_CLIENT_TOKEN),
    '2CD23A2CA0B2595A-F3C846C370E05F9CC83ACC5F995C5F5D87A4D0F14CE8936A0F902E64099E38A2',
  );
  assert.equal(
    generateGecToken(1_700_000_100, TRUSTED_CLIENT_TOKEN),
    '0919602B5750900A-EC9DD394F23CB5635CF62A0E8E24136DCD32B26D10C543B10082E482D7516397',
  );
  assert.equal(
    generateGecToken(1_794_859_592, TRUSTED_CLIENT_TOKEN),
    'CF8CB0C21D74BFB9-B90BC2515A710BB13F37D3AA698CCB444A9F6F4AB07FF812E00FA908BF0C8DB1',
  );
});

test('buildSsmlMessage escapes XML entities and validates allowed voices', () => {
  const ssml = buildSsmlMessage({
    requestId: 'req123',
    timestamp: '2026-01-01T00:00:00.000Z',
    text: 'Tom & Jerry <"test"> \'ok\'',
    voiceName: 'pl-PL-MarekNeural',
    rate: '+10%',
    pitch: '+0Hz',
    volume: '+0%',
  });

  assert.ok(ssml.includes('Path:ssml'));
  assert.ok(ssml.includes('Tom &amp; Jerry &lt;&quot;test&quot;&gt; &apos;ok&apos;'));

  assert.throws(() => {
    buildSsmlMessage({
      requestId: 'req124',
      timestamp: '2026-01-01T00:00:00.000Z',
      text: 'Hello',
      voiceName: 'en-GB-InvalidVoice',
    });
  }, /Voice must be an Edge neural voice/);
});

test('parseAudioFrame parses 2-byte big-endian header and extracts MP3 payload', () => {
  const headerStr = 'X-RequestId:abc\r\nContent-Type:audio/mpeg\r\nPath:audio\r\n\r\n';
  const headerBuf = Buffer.from(headerStr, 'utf8');
  const payload = Buffer.from([0xff, 0xf3, 0x44, 0xc4, 0x01, 0x02]);
  const frame = Buffer.alloc(2 + headerBuf.length + payload.length);
  frame.writeUInt16BE(headerBuf.length, 0);
  headerBuf.copy(frame, 2);
  payload.copy(frame, 2 + headerBuf.length);

  const extracted = parseAudioFrame(frame);
  assert.deepEqual(extracted, payload);
});

test('parseWordBoundaryRangeMs extracts millisecond bounds from Edge metadata ticks', () => {
  const metadata = JSON.stringify({
    Metadata: [
      { Type: 'WordBoundary', Data: { Offset: 1_000_000, Duration: 2_500_000 } },
      { Type: 'WordBoundary', Data: { Offset: 3_500_000, Duration: 1_500_000 } },
    ],
  });
  const range = parseWordBoundaryRangeMs(metadata);
  assert.deepEqual(range, { speechStartMs: 100, speechEndMs: 500 });
});

test('rewriteHlsPlaylist rewrites segment URLs and key URIs through /api/proxy', () => {
  const m3u8 = [
    '#EXTM3U',
    '#EXT-X-KEY:METHOD=AES-128,URI="/keys/k1.key"',
    '#EXTINF:6.0,',
    'seg_001.ts?token=abc',
  ].join('\n');

  const headersParam = JSON.stringify({ Referer: 'https://example.com/' });
  const out = rewriteHlsPlaylist(m3u8, 'https://cdn.example.com/hls/master.m3u8', headersParam);

  assert.ok(out.includes('URI="/api/proxy?url=https%3A%2F%2Fcdn.example.com%2Fkeys%2Fk1.key&headers='));
  assert.ok(out.includes('/api/proxy?url=https%3A%2F%2Fcdn.example.com%2Fhls%2Fseg_001.ts%3Ftoken%3Dabc&headers='));
});
