/*
 * Derived from edge-tts src/edge_tts/drm.py, copyright edge-tts contributors.
 * https://github.com/rany2/edge-tts/blob/360fa8e1de354e1dda74c09bd695887d6a44df74/src/edge_tts/drm.py
 * This file is distributed under the GNU LGPL version 3.
 */

export const TRUSTED_CLIENT_TOKEN = '6A5AA1D4EAFF4E9FB37E23D68491D6F4';
export const GEC_VERSION = '2-143.0.3650.75';

const words = (...values) => Int32Array.from(values, (v) => v | 0);

const INITIAL = words(
  0x6A09E667, 0xBB67AE85, 0x3C6EF372, 0xA54FF53A,
  0x510E527F, 0x9B05688C, 0x1F83D9AB, 0x5BE0CD19,
);

const PARTIAL_STATE = words(
  0xFD9FBEA2, 0x91243CE7, 0xBE499094, 0xCD2E1E0A,
  0x6C006F49, 0x04BD35AB, 0xFEB0A7E4, 0x03EFFBD2,
);

const PARTIAL_SCHEDULE = words(
  0xEB24BF9E, 0x1558691F, 0xA0C09EE2, 0x0CFA32BA,
  0xA622EE8C, 0xD1DD9491, 0xC0DD25A0, 0xF9E70AB6,
  0xF0C31573, 0x7058CE4E, 0x7188E606, 0x00E5AB33,
  0xD258B060, 0x72766564,
);

const ROUND_CONSTANTS = words(
  0x428A2F98, 0x71374491, 0xB5C0FBCF, 0xE9B5DBA5, 0x3956C25B, 0x59F111F1, 0x923F82A4, 0xAB1C5ED5,
  0xD807AA98, 0x12835B01, 0x243185BE, 0x550C7DC3, 0x72BE5D74, 0x80DEB1FE, 0x9BDC06A7, 0xC19BF174,
  0xE49B69C1, 0xEFBE4786, 0x0FC19DC6, 0x240CA1CC, 0x2DE92C6F, 0x4A7484AA, 0x5CB0A9DC, 0x76F988DA,
  0x983E5152, 0xA831C66D, 0xB00327C8, 0xBF597FC7, 0xC6E00BF3, 0xD5A79147, 0x06CA6351, 0x14292967,
  0x27B70A85, 0x2E1B2138, 0x4D2C6DFC, 0x53380D13, 0x650A7354, 0x766A0ABB, 0x81C2C92E, 0x92722C85,
  0xA2BFE8A1, 0xA81A664B, 0xC24B8B70, 0xC76C51A3, 0xD192E819, 0xD6990624, 0xF40E3585, 0x106AA070,
  0x19A4C116, 0x1E376C08, 0x2748774C, 0x34B0BCB5, 0x391C0CB3, 0x4ED8AA4A, 0x5B9CCA4F, 0x682E6FF3,
  0x748F82EE, 0x78A5636F, 0x84C87814, 0x8CC70208, 0x90BEFFFA, 0xA4506CEB, 0xBEF9A3F7, 0xC67178F2,
);

const COEFFICIENTS = [
  words(0xE82E91B1, 0xB32700D0), words(0x44DE24C6, 0x88DFF51D),
  words(0x3BEB65BC, 0x29514038), words(0xC89C5933, 0xB328CBDF),
  words(0x8D1EC211, 0xD405B4D5), words(0x314EF5D5, 0x26E55F3C),
  words(0xE92491B1, 0x607801C6), words(0xF0A5B9D5, 0x82B2C1FB),
  words(0x06064CF3, 0xFA51A3DA), words(0xDA79FA60, 0x207CC464),
  words(0xBED20560, 0x3C3C74F3), words(0x89F9C9F8, 0xD2D14B61),
  words(0x35FA38C6, 0xBB04A2C1), words(0xCB49E3C6, 0xC5626167),
];

const ADJUSTMENT = 0xC9DC06A7 | 0;
const FACTORS_A = words(0xCF98B111, 0x87085B9F, 0x12CEB96D, 0x257E1D83);
const FACTORS_B = words(0xA27416F5, 0xD38396FF, 0x7C932B89, 0xBFA49F69);

function rotR(x, n) {
  return (x >>> n) | (x << (32 - n));
}

function rotL(x, n) {
  return (x << n) | (x >>> (32 - n));
}

function sigma0(x) {
  return rotR(x, 7) ^ rotR(x, 18) ^ (x >>> 3);
}

function sigma1(x) {
  return rotR(x, 17) ^ rotR(x, 19) ^ (x >>> 10);
}

function expand(w, start) {
  for (let i = start; i < 64; i++) {
    w[i] = (sigma1(w[i - 2]) + w[i - 7] + sigma0(w[i - 15]) + w[i - 16]) | 0;
  }
}

function rounds(state, w, start) {
  let a = state[0];
  let b = state[1];
  let c = state[2];
  let d = state[3];
  let e = state[4];
  let f = state[5];
  let g = state[6];
  let h = state[7];
  for (let i = start; i < 64; i++) {
    const s1 = rotR(e, 6) ^ rotR(e, 11) ^ rotR(e, 25);
    const s0 = rotR(a, 2) ^ rotR(a, 13) ^ rotR(a, 22);
    const k = start === 14 && i === 14 ? ADJUSTMENT : ROUND_CONSTANTS[i];
    const t1 = (h + s1 + (g ^ (e & (f ^ g))) + k + w[i]) | 0;
    const t2 = (s0 + ((a & b) | (c & (a | b)))) | 0;
    h = g;
    g = f;
    f = e;
    e = (d + t1) | 0;
    d = c;
    c = b;
    b = a;
    a = (t1 + t2) | 0;
  }
  return words(a, b, c, d, e, f, g, h);
}

function digest(data) {
  if (data.length < 7) {
    throw new Error('Input data too short for Edge GEC digest');
  }
  const w = new Int32Array(64);
  const dvData = new DataView(data.buffer, data.byteOffset, data.byteLength);
  w[14] = ((data[0] & 255) << 16) | ((data[1] & 255) << 8) | (data[2] & 255);
  w[15] = dvData.getInt32(3, false);

  for (let i = 16; i < 30; i++) {
    let value = PARTIAL_SCHEDULE[i - 16];
    value = (value + (sigma1(w[i - 2]) ^ (i === 16 ? 0x000B92C0 : 0))) | 0;
    if (i - 7 >= 14) value = (value + w[i - 7]) | 0;
    if (i - 15 >= 14) value = (value + (sigma0(w[i - 15]) ^ (i === 29 ? 0x059C0B80 : 0))) | 0;
    w[i] = value;
  }
  w[30] = (sigma1(w[28]) + w[23] + sigma0(w[15]) + w[14] + ADJUSTMENT - ROUND_CONSTANTS[14]) | 0;
  expand(w, 31);

  const state = rounds(PARTIAL_STATE, w, 14);
  for (let i = 0; i < state.length; i++) {
    state[i] = (state[i] + INITIAL[i]) | 0;
  }

  const length = 57 + data.length;
  const rem = (55 - length) % 64;
  const padding = rem < 0 ? rem + 64 : rem;
  const bufLen = data.length - 7 + 1 + padding + 8;
  const buffer = new Uint8Array(bufLen);
  buffer.set(data.subarray(7), 0);
  buffer[data.length - 7] = 0x80;
  const dvBuf = new DataView(buffer.buffer);
  dvBuf.setBigUint64(bufLen - 8, BigInt(length) * 8n, false);

  for (let pos = 0; pos < bufLen; pos += 64) {
    for (let i = 0; i < 16; i++) {
      w[i] = dvBuf.getInt32(pos + i * 4, false);
    }
    expand(w, 16);
    const working = rounds(state, w, 0);
    for (let i = 0; i < state.length; i++) {
      state[i] = (state[i] + working[i]) | 0;
    }
  }

  const out = new Uint8Array(32);
  const dvOut = new DataView(out.buffer);
  for (let i = 0; i < state.length; i++) {
    dvOut.setInt32(i * 4, state[i], false);
  }
  return out;
}

function checksum(data, digestBytes) {
  const dvDigest = new DataView(digestBytes.buffer, digestBytes.byteOffset, digestBytes.byteLength);
  const a = dvDigest.getInt32(0, true) | 1;
  const b = dvDigest.getInt32(4, true) | 1;
  const factors = [Math.imul(a, 0x82893933 | 0), Math.imul(b, 0x82893933 | 0)];
  const count = Math.floor((data.length + 8) / 8) * 2;
  const terms = new Int32Array(14 + count);
  const offsets = new Int32Array(terms.length);

  for (let i = 0; i < COEFFICIENTS.length; i++) {
    terms[i] = Math.imul(COEFFICIENTS[i][0], factors[i % 2]);
    offsets[i] = COEFFICIENTS[i][1];
  }

  const input = new Uint8Array(count * 4);
  input[0] = 0;
  input.set(data, 1);
  const dvInput = new DataView(input.buffer);

  for (let i = 0; i < count; i++) {
    const word = dvInput.getInt32(i * 4, true);
    terms[14 + i] = Math.imul(word, i % 2 === 0 ? a : b);
    offsets[14 + i] = Math.imul(
      rotL(word, 16),
      i % 2 === 0 ? (0xEF0569FB | 0) : (0xC31713DB | 0),
    );
    if (i === 0) {
      terms[14] = (terms[14] + Math.imul(0xF2F90B1A | 0, factors[0])) | 0;
      offsets[14] = (offsets[14] + 0x0B1A0000) | 0;
    }
  }

  let swapAcc = 0;
  let swapLast = 0;
  let state = 0;
  let acc = 0;
  for (let i = 0; i < terms.length; i += 2) {
    const x = terms[i];
    const y = terms[i + 1];
    let q = Math.imul(rotL((offsets[i] + x) | 0, 16), 0x76F50F34 | 0);
    swapAcc = (swapAcc + Math.imul(rotL(q, 16), 0xC3EFEA97 | 0) + q) | 0;
    q = Math.imul(rotL((offsets[i + 1] + y) | 0, 16), 0x1881CE3C | 0);
    swapLast = (Math.imul(rotL(q, 16), 0x35BD1EC9 | 0) + q) | 0;
    swapAcc = (swapAcc + swapLast) | 0;

    state = (Math.imul(state, a) + x) | 0;
    for (let j = 0; j < FACTORS_A.length; j++) {
      state = Math.imul(rotL(state, 16), FACTORS_A[j]);
    }
    acc = (acc + state) | 0;

    let t = (Math.imul(state, b) + y) | 0;
    for (let j = 0; j < FACTORS_B.length; j++) {
      t = Math.imul(rotL(t, 16), FACTORS_B[j]);
    }
    state = t;
    acc = (acc + state) | 0;
  }

  const out = new Uint8Array(8);
  const dvOut = new DataView(out.buffer);
  dvOut.setInt32(0, swapLast ^ state, true);
  dvOut.setInt32(4, swapAcc ^ acc, true);
  return out;
}

function toUpperHex(bytes) {
  let out = '';
  for (let i = 0; i < bytes.length; i++) {
    out += (bytes[i] & 0xff).toString(16).padStart(2, '0').toUpperCase();
  }
  return out;
}

export function generateGecToken(unixSeconds, clientToken = TRUSTED_CLIENT_TOKEN) {
  const unix = BigInt(Math.floor(Number(unixSeconds)));
  const seconds = unix + 11644473600n;
  const mod = ((seconds % 300n) + 300n) % 300n;
  const ticks = (seconds - mod) * 10000000n;
  const str = `${ticks}${clientToken}`;
  const data = new Uint8Array(str.length);
  for (let i = 0; i < str.length; i++) {
    data[i] = str.charCodeAt(i) & 0x7f;
  }
  const digestBytes = digest(data);
  const cs64 = checksum(data, digestBytes);
  return `${toUpperHex(cs64)}-${toUpperHex(digestBytes)}`;
}
