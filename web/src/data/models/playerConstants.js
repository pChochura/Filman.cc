export const PLAYBACK_SPEEDS = [0.5, 0.75, 1.0, 1.25, 1.5, 2.0];

export const AspectRatio = {
  FIT: 0,
  CROP: 1,
  STRETCH: 2,
};

export const MENU_SOURCES_ID = 'sources_menu';
export const MENU_QUALITY_ID = 'menu_quality';

export const NextEpisodeAppearance = {
  HIDE: 'HIDE',
  SHOW: 'SHOW',
  SHOW_IN_OVERLAY: 'SHOW_IN_OVERLAY',
  SHOW_WITH_TIMER: 'SHOW_WITH_TIMER',
};

export const QUALITY_OPTIONS = ['auto', '1080p', '720p', '480p'];

export const EdgeType = {
  NONE: 0,
  OUTLINE: 1,
  SHADOW: 2,
};

export const DEFAULT_SUBTITLE_STYLE = {
  fontSizeFraction: 0.0533,
  textColorHex: '#FFFFFF',
  backgroundColorHex: 'transparent',
  edgeType: EdgeType.SHADOW,
  edgeColorHex: '#000000',
  verticalPaddingFraction: 0.05,
};

export const SUBTITLE_FONT_SIZES = [0.03, 0.0533, 0.08, 0.1, 0.12, 0.15];
export const SUBTITLE_VERTICAL_OFFSETS = [0.01, 0.05, 0.1, 0.15, 0.2];
export const SUBTITLE_TEXT_COLORS = [
  { hex: '#FFFFFF', labelKey: 'player_color_white', argb: 0xffffffff | 0 },
  { hex: '#FFFF00', labelKey: 'player_color_yellow', argb: 0xffffff00 | 0 },
];
export const SUBTITLE_BG_COLORS = [
  { hex: 'transparent', labelKey: 'player_color_transparent', argb: 0x00000000 },
  { hex: 'rgba(0, 0, 0, 0.5)', labelKey: 'player_color_black', argb: 0x80000000 | 0 },
];
export const SUBTITLE_EDGE_TYPES = [
  { value: EdgeType.NONE, labelKey: 'player_edge_none' },
  { value: EdgeType.OUTLINE, labelKey: 'player_edge_outline' },
  { value: EdgeType.SHADOW, labelKey: 'player_edge_shadow' },
];

export function argbIntToCss(argb, fallback = '#FFFFFF') {
  if (typeof argb !== 'number') return fallback;
  const u = argb >>> 0;
  const a = ((u >>> 24) & 0xff) / 255;
  const r = (u >>> 16) & 0xff;
  const g = (u >>> 8) & 0xff;
  const b = u & 0xff;
  if (a === 0) return 'transparent';
  if (a === 1) {
    return `#${[r, g, b].map((x) => x.toString(16).padStart(2, '0')).join('')}`;
  }
  return `rgba(${r}, ${g}, ${b}, ${Number(a.toFixed(2))})`;
}

export function cssToArgbInt(css, fallback = 0xffffffff | 0) {
  if (!css || css === 'transparent') return 0x00000000;
  if (css.startsWith('#') && (css.length === 7 || css.length === 9)) {
    if (css.length === 7) {
      const rgb = Number.parseInt(css.slice(1), 16);
      return (0xff000000 | rgb) | 0;
    }
    return Number.parseInt(css.slice(1), 16) | 0;
  }
  const match = /rgba?\((\d+),\s*(\d+),\s*(\d+)(?:,\s*([\d.]+))?\)/i.exec(css);
  if (match) {
    const r = Number.parseInt(match[1], 10) & 0xff;
    const g = Number.parseInt(match[2], 10) & 0xff;
    const b = Number.parseInt(match[3], 10) & 0xff;
    const a = match[4] !== undefined ? Math.round(Number.parseFloat(match[4]) * 255) & 0xff : 255;
    return ((a << 24) | (r << 16) | (g << 8) | b) | 0;
  }
  return fallback;
}
