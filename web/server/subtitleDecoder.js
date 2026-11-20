const CP1250_CONTROL_MARKERS = new Set([0x8c, 0x8f, 0x9c, 0x9f]);
const CP1250_EXTRA_MARKERS = new Set([0xa5, 0xb9]);
const ISO_8859_2_MARKERS = new Set([0xa1, 0xa6, 0xac, 0xb1, 0xb6, 0xbc]);

function toUint8Array(input) {
  if (input instanceof Uint8Array) return input;
  if (input instanceof ArrayBuffer) return new Uint8Array(input);
  if (ArrayBuffer.isView(input)) {
    return new Uint8Array(input.buffer, input.byteOffset, input.byteLength);
  }
  return new Uint8Array(input || []);
}

function decodeBom(bytes) {
  if (
    bytes.length >= 3 &&
    bytes[0] === 0xef &&
    bytes[1] === 0xbb &&
    bytes[2] === 0xbf
  ) {
    return new TextDecoder('utf-8').decode(bytes.subarray(3));
  }
  if (bytes.length >= 2) {
    if (bytes[0] === 0xff && bytes[1] === 0xfe) {
      return new TextDecoder('utf-16le').decode(bytes.subarray(2));
    }
    if (bytes[0] === 0xfe && bytes[1] === 0xff) {
      return new TextDecoder('utf-16be').decode(bytes.subarray(2));
    }
  }
  return null;
}

function decodeStrict(bytes, encoding) {
  try {
    return new TextDecoder(encoding, { fatal: true }).decode(bytes);
  } catch {
    return null;
  }
}

export function detectCentralEuropeanCharset(bytes) {
  let cp1250ControlMarkers = 0;
  let cp1250AllMarkers = 0;
  let iso88592Markers = 0;

  for (let i = 0; i < bytes.length; i++) {
    const value = bytes[i] & 0xff;
    if (CP1250_CONTROL_MARKERS.has(value)) {
      cp1250ControlMarkers++;
      cp1250AllMarkers++;
    } else if (CP1250_EXTRA_MARKERS.has(value)) {
      cp1250AllMarkers++;
    } else if (ISO_8859_2_MARKERS.has(value)) {
      iso88592Markers++;
    }
  }

  return cp1250ControlMarkers === 0 && iso88592Markers > cp1250AllMarkers
    ? 'iso-8859-2'
    : 'windows-1250';
}

export function decodeSubtitleBytes(input, declaredCharset = null) {
  const bytes = toUint8Array(input);
  if (bytes.length === 0) return '';

  const bomDecoded = decodeBom(bytes);
  if (bomDecoded !== null) return bomDecoded;

  if (declaredCharset) {
    const normalized = String(declaredCharset).trim().toLowerCase();
    if (normalized && normalized !== 'utf-8' && normalized !== 'utf8') {
      const strictDeclared = decodeStrict(bytes, normalized);
      if (strictDeclared !== null) return strictDeclared;
    }
  }

  const strictUtf8 = decodeStrict(bytes, 'utf-8');
  if (strictUtf8 !== null) return strictUtf8;

  const detected = detectCentralEuropeanCharset(bytes);
  try {
    return new TextDecoder(detected).decode(bytes);
  } catch {
    return new TextDecoder('utf-8').decode(bytes);
  }
}
