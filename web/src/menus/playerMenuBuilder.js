import React from 'react';
import { t } from '../i18n/index.js';
import {
  AspectRatio,
  MENU_SOURCES_ID,
  MENU_QUALITY_ID,
  PLAYBACK_SPEEDS,
  SUBTITLE_BG_COLORS,
  SUBTITLE_EDGE_TYPES,
  SUBTITLE_FONT_SIZES,
  SUBTITLE_TEXT_COLORS,
  SUBTITLE_VERTICAL_OFFSETS,
} from '../data/models/playerConstants.js';
import { updateSubtitleStyle } from '../data/local/settingsStore.js';
import { buildLektorSettingsMenu } from './lektorSettingsMenu.js';
import { parseQualityResolution, selectVideoMatchingQuality } from '../data/api/videoUrlResolver.js';

const SUBTITLE_LANGUAGES = ['pl', 'en', 'es', 'fr', 'de', 'it'];
const OFFSET_PRESETS = [-5000, -2500, -1000, -500, -200, 0, 200, 500, 1000, 2500, 5000];
const FPS_OPTIONS = [
  { ratio: 1.0, labelKey: 'player_subtitle_fps_scale_normal' },
  { ratio: 25.0 / 23.976, labelKey: 'player_subtitle_fps_25_to_23976' },
  { ratio: 23.976 / 25.0, labelKey: 'player_subtitle_fps_23976_to_25' },
];

function formatWebsiteHeader(sourceWebsite) {
  if (!sourceWebsite) return t('player_unknown_source');
  if (sourceWebsite === 'stream_api') return t('player_source_stream_api');
  return sourceWebsite.charAt(0).toUpperCase() + sourceWebsite.slice(1);
}

function formatOffsetLabel(offsetMs) {
  if (offsetMs === 0) return t('player_subtitle_timing_on_time');
  const sec = (Math.abs(offsetMs) / 1000).toFixed(1);
  return offsetMs < 0
    ? t('player_subtitle_timing_offset_earlier', sec)
    : t('player_subtitle_timing_offset_later', sec);
}

function buildSourceItems({
  alternativeSources,
  isLoadingSources = false,
  videoUrl,
  preferredQuality = 'auto',
  onSelectSource,
  onClose,
}) {
  const groups = new Map();
  for (const src of alternativeSources) {
    const key = src.sourceWebsite || '';
    if (!groups.has(key)) groups.set(key, []);
    groups.get(key).push(src);
  }

  const items = [];
  for (const [site, sources] of groups.entries()) {
    items.push({
      id: `src_hdr_${site || 'unknown'}`,
      type: 'header',
      label: formatWebsiteHeader(site),
    });

    const serverGroups = new Map();
    for (const src of sources) {
      const serverKey = `${(src.serverName || '').trim().toLowerCase()}|||${(src.version || '').trim().toLowerCase()}`;
      if (!serverGroups.has(serverKey)) serverGroups.set(serverKey, []);
      serverGroups.get(serverKey).push(src);
    }

    let i = 0;
    for (const groupVideos of serverGroups.values()) {
      const rep = groupVideos[0];
      const label = [rep.serverName, rep.version].filter(Boolean).join(' • ');
      const isSelected = groupVideos.some((s) => s.url === videoUrl);
      items.push({
        id: `src_${rep.url}_${i++}`,
        type: 'option',
        label: label || `Source #${i}`,
        isSelected,
        onClick: () => {
          onClose?.();
          const chosen = selectVideoMatchingQuality(groupVideos, preferredQuality) || rep;
          onSelectSource?.(chosen);
        },
      });
    }
  }

  if (isLoadingSources) {
    items.push({
      id: 'src_loading_indicator',
      type: 'custom',
      render: () =>
        React.createElement(
          'div',
          {
            className: 'overlay-item overlay-item--loader',
            style: {
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              padding: '16px 0',
              gap: '10px',
            },
          },
          React.createElement('span', { className: 'spinner spinner--sm' }),
          React.createElement(
            'span',
            { className: 'type-body-small', style: { opacity: 0.7 } },
            t('player_searching_sources', 'Searching for more sources…')
          )
        ),
    });
  } else if (items.length === 0) {
    items.push({
      id: 'no_sources',
      type: 'footer',
      label: t('common_no_alternative_sources', 'No alternative sources found'),
    });
  }

  return items;
}

function buildQualityItems({ alternativeSources, videoUrl, onSelectSource, onClose }) {
  const activeSource = alternativeSources.find((s) => s.url === videoUrl);
  if (!activeSource) return [];

  const activeServer = (activeSource.serverName || '').trim().toLowerCase();
  const activeVersion = (activeSource.version || '').trim().toLowerCase();
  const activeWebsite = (activeSource.sourceWebsite || '').trim().toLowerCase();

  const groupVideos = alternativeSources.filter((s) => {
    return (
      (s.sourceWebsite || '').trim().toLowerCase() === activeWebsite &&
      (s.serverName || '').trim().toLowerCase() === activeServer &&
      (s.version || '').trim().toLowerCase() === activeVersion
    );
  });

  const seenQualities = new Set();
  const distinct = [];
  for (const v of groupVideos) {
    const q = (v.quality || '').trim().toLowerCase();
    if (q && !seenQualities.has(q)) {
      seenQualities.add(q);
      distinct.push(v);
    }
  }
  distinct.sort((a, b) => parseQualityResolution(b.quality) - parseQualityResolution(a.quality));

  if (distinct.length === 0 && !activeSource.quality) {
    return [];
  }

  if (distinct.length === 0 && activeSource.quality) {
    return [
      {
        id: `quality_${activeSource.quality.trim().toLowerCase()}`,
        type: 'option',
        label: activeSource.quality,
        isSelected: true,
        onClick: () => {
          onClose?.();
        },
      },
    ];
  }

  return distinct.map((v) => ({
    id: `quality_${(v.quality || '').trim().toLowerCase()}`,
    type: 'option',
    label: v.quality,
    isSelected: v.url === videoUrl || (activeSource.quality && v.quality.toLowerCase() === activeSource.quality.toLowerCase()),
    onClick: () => {
      onClose?.();
      onSelectSource?.(v);
    },
  }));
}

function buildSubtitleSyncMenu({
  allSubtitleTracks,
  selectedSubtitleUrl,
  subtitleOffsetMs,
  subtitleFpsScale,
  isAutoAligning,
  onAutoAlign,
  onAlignWithTrack,
  onAdjustOffset,
  onSetOffset,
  onSetFpsScale,
}) {
  const otherTracks = allSubtitleTracks.filter((s) => s.url !== selectedSubtitleUrl);
  const fpsMatchesKnown = FPS_OPTIONS.some((o) => Math.abs(o.ratio - subtitleFpsScale) < 0.001);
  const fpsValue = fpsMatchesKnown
    ? t(FPS_OPTIONS.find((o) => Math.abs(o.ratio - subtitleFpsScale) < 0.001).labelKey)
    : t('player_subtitle_custom');

  const items = [
    {
      id: 'btn_auto_align',
      type: 'button',
      label: isAutoAligning
        ? t('player_subtitle_auto_align_running')
        : t('player_subtitle_auto_align_button'),
      value: t('player_subtitle_auto_align_desc'),
      onClick: () => {
        if (!isAutoAligning) onAutoAlign?.();
      },
    },
  ];

  if (otherTracks.length > 0) {
    items.push({
      id: 'menu_align_specific',
      type: 'nested',
      label: t('player_subtitle_auto_align'),
      items: otherTracks.map((sub, idx) => ({
        id: `align_${sub.url}_${idx}`,
        type: 'button',
        label: sub.label || sub.language || `Track ${idx + 1}`,
        onClick: ({ popBack }) => {
          onAlignWithTrack?.(sub.url);
          popBack();
        },
      })),
    });
  }

  items.push(
    {
      id: 'hdr_quick_shift',
      type: 'header',
      label: t('player_subtitle_quick_shift'),
    },
    {
      id: 'btn_shift_earlier_500',
      type: 'button',
      label: t('player_subtitle_shift_earlier_by', '0.5'),
      onClick: () => onAdjustOffset?.(-500),
    },
    {
      id: 'btn_shift_later_500',
      type: 'button',
      label: t('player_subtitle_shift_later_by', '0.5'),
      onClick: () => onAdjustOffset?.(500),
    },
    {
      id: 'btn_reset_sync',
      type: 'button',
      label: t('player_subtitle_reset_sync'),
      onClick: () => {
        onSetOffset?.(0);
        onSetFpsScale?.(1.0);
      },
    },
    {
      id: 'hdr_current_offset',
      type: 'header',
      label: t('player_subtitle_current_offset', (subtitleOffsetMs / 1000).toFixed(1)),
    },
  );

  if (!OFFSET_PRESETS.includes(subtitleOffsetMs)) {
    items.push({
      id: 'sub_offset_current',
      type: 'option',
      label: formatOffsetLabel(subtitleOffsetMs),
      isSelected: true,
      onClick: ({ popBack }) => popBack(),
    });
  }

  for (const ms of OFFSET_PRESETS) {
    items.push({
      id: `sub_offset_${ms}`,
      type: 'option',
      label: formatOffsetLabel(ms),
      isSelected: subtitleOffsetMs === ms,
      onClick: ({ popBack }) => {
        onSetOffset?.(ms);
        popBack();
      },
    });
  }

  items.push({
    id: 'menu_subtitle_fps',
    type: 'nested',
    label: t('player_subtitle_fps_scale'),
    value: fpsValue,
    items: FPS_OPTIONS.map((opt) => ({
      id: `fps_${opt.labelKey}`,
      type: 'option',
      label: t(opt.labelKey),
      isSelected: Math.abs(subtitleFpsScale - opt.ratio) < 0.001,
      onClick: ({ popBack }) => {
        onSetFpsScale?.(opt.ratio);
        popBack();
      },
    })),
  });

  return {
    id: 'menu_subtitle_sync',
    type: 'nested',
    label: t('player_subtitle_fix_sync'),
    value: formatOffsetLabel(subtitleOffsetMs),
    items,
  };
}

function buildSubtitleStyleItems(subtitleStyle) {
  const fontLabel = (f) => `${Math.round(f * 100)}%`;
  const textColorEntry =
    SUBTITLE_TEXT_COLORS.find((c) => c.hex === subtitleStyle.textColorHex) ||
    SUBTITLE_TEXT_COLORS[0];
  const bgColorEntry =
    SUBTITLE_BG_COLORS.find((c) => c.hex === subtitleStyle.backgroundColorHex) ||
    SUBTITLE_BG_COLORS[0];
  const edgeEntry =
    SUBTITLE_EDGE_TYPES.find((e) => e.value === subtitleStyle.edgeType) ||
    SUBTITLE_EDGE_TYPES[2];

  const previewTextShadow =
    subtitleStyle.edgeType === 1
      ? '-1px -1px 0 #000, 1px -1px 0 #000, -1px 1px 0 #000, 1px 1px 0 #000'
      : subtitleStyle.edgeType === 2
        ? '2px 2px 4px rgba(0,0,0,0.9)'
        : 'none';

  return [
    {
      id: 'sub_style_preview_hdr',
      type: 'header',
      label: t('common_subtitle_style_preview'),
    },
    {
      id: 'sub_style_preview_box',
      type: 'custom',
      render: () =>
        React.createElement(
          'div',
          { className: 'subtitle-preview-box' },
          React.createElement(
            'span',
            {
              className: 'player-subtitle-cue',
              style: {
                color: subtitleStyle.textColorHex,
                backgroundColor: subtitleStyle.backgroundColorHex,
                textShadow: previewTextShadow,
                fontSize: `${Math.max(13, Math.round(subtitleStyle.fontSizeFraction * 260))}px`,
              },
            },
            t('common_subtitle_style_sample'),
          ),
        ),
    },
    {
      id: 'style_font_size',
      type: 'nested',
      label: t('player_subtitle_style_font_size'),
      value: fontLabel(subtitleStyle.fontSizeFraction),
      items: SUBTITLE_FONT_SIZES.map((f) => ({
        id: `fs_${f}`,
        type: 'option',
        label: fontLabel(f),
        isSelected: Math.abs(subtitleStyle.fontSizeFraction - f) < 0.002,
        onClick: ({ popBack }) => {
          updateSubtitleStyle({ fontSizeFraction: f });
          popBack();
        },
      })),
    },
    {
      id: 'style_text_color',
      type: 'nested',
      label: t('player_subtitle_style_text_color'),
      value: t(textColorEntry.labelKey),
      items: SUBTITLE_TEXT_COLORS.map((c) => ({
        id: `tc_${c.hex}`,
        type: 'option',
        label: t(c.labelKey),
        isSelected: subtitleStyle.textColorHex === c.hex,
        onClick: ({ popBack }) => {
          updateSubtitleStyle({ textColorHex: c.hex });
          popBack();
        },
      })),
    },
    {
      id: 'style_background',
      type: 'nested',
      label: t('player_subtitle_style_background'),
      value: t(bgColorEntry.labelKey),
      items: SUBTITLE_BG_COLORS.map((c) => ({
        id: `bg_${c.hex}`,
        type: 'option',
        label: t(c.labelKey),
        isSelected: subtitleStyle.backgroundColorHex === c.hex,
        onClick: ({ popBack }) => {
          updateSubtitleStyle({ backgroundColorHex: c.hex });
          popBack();
        },
      })),
    },
    {
      id: 'style_edge_type',
      type: 'nested',
      label: t('player_subtitle_style_edge_type'),
      value: t(edgeEntry.labelKey),
      items: SUBTITLE_EDGE_TYPES.map((e) => ({
        id: `edge_${e.value}`,
        type: 'option',
        label: t(e.labelKey),
        isSelected: subtitleStyle.edgeType === e.value,
        onClick: ({ popBack }) => {
          updateSubtitleStyle({ edgeType: e.value });
          popBack();
        },
      })),
    },
    {
      id: 'style_vertical_offset',
      type: 'nested',
      label: t('player_subtitle_style_vertical_offset'),
      value: fontLabel(subtitleStyle.verticalPaddingFraction),
      items: SUBTITLE_VERTICAL_OFFSETS.map((v) => ({
        id: `vo_${v}`,
        type: 'option',
        label: fontLabel(v),
        isSelected: Math.abs(subtitleStyle.verticalPaddingFraction - v) < 0.002,
        onClick: ({ popBack }) => {
          updateSubtitleStyle({ verticalPaddingFraction: v });
          popBack();
        },
      })),
    },
  ];
}

export function buildPlayerMenu({
  alternativeSources = [],
  isLoadingSources = false,
  videoUrl,
  audioTracks = [],
  selectedAudioTrackId,
  sourceSubtitles = [],
  downloadedSubtitles = [],
  selectedSubtitleUrl,
  subtitleSearchResults = [],
  isSearchingSubtitles = false,
  subtitleOffsetMs = 0,
  subtitleFpsScale = 1.0,
  isAutoAligning = false,
  playbackSpeed = 1.0,
  aspectRatioMode = AspectRatio.FIT,
  settings,
  systemVoices = [],
  initialMenuId = null,
  isLoggedIn = false,
  connectedDevices = [],
  isLoadingDevices = false,
  onCastToDevice,
  onSelectSource,
  onSelectAudioTrack,
  onSelectSubtitle,
  onSearchSubtitles,
  onDownloadSubtitleCandidate,
  onAutoAlign,
  onAlignWithTrack,
  onAdjustSubtitleOffset,
  onSetSubtitleOffset,
  onSetSubtitleFpsScale,
  onToggleLektorEnabled,
  onRefreshSystemVoices,
  onSetPlaybackSpeed,
  onSetAspectRatio,
  onClose,
  onToast,
}) {
  const items = [];

  if (alternativeSources.length > 0 || isLoadingSources) {
    items.push({
      id: MENU_SOURCES_ID,
      type: 'nested',
      label: t('player_video_source'),
      items: buildSourceItems({
        alternativeSources,
        isLoadingSources,
        videoUrl,
        preferredQuality: settings?.preferredQuality,
        onSelectSource,
        onClose,
      }),
    });
  }

  const qualityItems = buildQualityItems({
    alternativeSources,
    videoUrl,
    onSelectSource,
    onClose,
  });
  if (qualityItems.length > 0) {
    const activeSource = alternativeSources.find((s) => s.url === videoUrl);
    items.push({
      id: MENU_QUALITY_ID,
      type: 'nested',
      label: t('player_video_quality'),
      value: activeSource?.quality || null,
      items: qualityItems,
    });
  }

  if (audioTracks.length > 1) {
    items.push({
      id: 'menu_audio',
      type: 'nested',
      label: t('player_audio_track'),
      items: audioTracks.map((track) => ({
        id: `audio_${track.id}`,
        type: 'option',
        label: track.label,
        isSelected: track.id === selectedAudioTrackId,
        onClick: () => {
          onClose?.();
          onSelectAudioTrack?.(track.id);
        },
      })),
    });
  }

  const allSubs = [...sourceSubtitles, ...downloadedSubtitles];
  const subtitleMenuItems = [];
  if (selectedSubtitleUrl) {
    subtitleMenuItems.push(
      buildSubtitleSyncMenu({
        allSubtitleTracks: allSubs,
        selectedSubtitleUrl,
        subtitleOffsetMs,
        subtitleFpsScale,
        isAutoAligning,
        onAutoAlign,
        onAlignWithTrack,
        onAdjustOffset: onAdjustSubtitleOffset,
        onSetOffset: onSetSubtitleOffset,
        onSetFpsScale: onSetSubtitleFpsScale,
      }),
    );
  }
  if (allSubs.length > 0) {
    subtitleMenuItems.push({
      id: 'sub_off',
      type: 'option',
      label: t('player_subtitles_off'),
      isSelected: !selectedSubtitleUrl,
      onClick: () => {
        onClose?.();
        onSelectSubtitle?.(null);
      },
    });
  }
  for (let i = 0; i < sourceSubtitles.length; i++) {
    const sub = sourceSubtitles[i];
    subtitleMenuItems.push({
      id: `sub_src_${sub.url}_${i}`,
      type: 'option',
      label: sub.label || sub.language || `Subtitle #${i + 1}`,
      isSelected: sub.url === selectedSubtitleUrl,
      onClick: () => {
        onClose?.();
        onSelectSubtitle?.(sub.url);
      },
    });
  }
  for (let i = 0; i < downloadedSubtitles.length; i++) {
    const sub = downloadedSubtitles[i];
    subtitleMenuItems.push({
      id: `sub_dl_${sub.url}_${i}`,
      type: 'option',
      label: sub.label || sub.language || `Downloaded #${i + 1}`,
      isSelected: sub.url === selectedSubtitleUrl,
      onClick: () => {
        onClose?.();
        onSelectSubtitle?.(sub.url);
      },
    });
  }
  if (subtitleSearchResults.length > 0) {
    subtitleMenuItems.push({
      id: 'menu_subtitle_results',
      type: 'nested',
      label: t('player_subtitle_results'),
      items: subtitleSearchResults.map((cand) => {
        const downloads = cand.downloadCount ?? 0;
        let subLine = '';
        if (cand.foreignPartsOnly && cand.hearingImpaired) {
          subLine = t('player_subtitle_candidate_forced_hi', cand.language, downloads);
        } else if (cand.foreignPartsOnly) {
          subLine = t('player_subtitle_candidate_forced', cand.language, downloads);
        } else if (cand.hearingImpaired) {
          subLine = t('player_subtitle_candidate_hi', cand.language, downloads);
        } else {
          subLine = t('player_subtitle_candidate', cand.language, downloads);
        }
        if (cand.rating) {
          subLine += ` · ${t('player_subtitle_rating', Number(cand.rating).toFixed(1))}`;
        }
        const titleParts = [`[${cand.provider}]`, cand.name, cand.release].filter(Boolean);
        return {
          id: `cand_${cand.id}`,
          type: 'button',
          label: titleParts.join(' · '),
          value: subLine,
          onClick: () => {
            onClose?.();
            onDownloadSubtitleCandidate?.(cand);
          },
        };
      }),
    });
  }
  subtitleMenuItems.push({
    id: 'menu_download_subtitles',
    type: 'nested',
    label: isSearchingSubtitles ? t('common_loading') : t('player_download_subtitles'),
    items: SUBTITLE_LANGUAGES.map((lang) => ({
      id: `dl_lang_${lang}`,
      type: 'button',
      label: lang.toUpperCase(),
      onClick: () => {
        onSearchSubtitles?.(lang);
      },
    })),
  });

  items.push({
    id: 'menu_subtitles',
    type: 'nested',
    label: t('player_subtitles'),
    items: subtitleMenuItems,
  });

  items.push(
    buildLektorSettingsMenu({
      settings,
      systemVoices,
      onRefreshSystemVoices,
      onToggleEnabled: onToggleLektorEnabled,
      onToast,
    }),
  );

  items.push({
    id: 'menu_subtitle_style',
    type: 'nested',
    label: t('player_menu_subtitle_style'),
    items: buildSubtitleStyleItems(settings.subtitleStyle),
  });

  items.push({
    id: 'menu_speed',
    type: 'nested',
    label: t('player_playback_speed'),
    value: t('player_speed_format', playbackSpeed),
    items: PLAYBACK_SPEEDS.map((spd) => ({
      id: `spd_${spd}`,
      type: 'option',
      label: t('player_speed_format', spd),
      isSelected: Math.abs(playbackSpeed - spd) < 0.01,
      onClick: ({ popBack }) => {
        onSetPlaybackSpeed?.(spd);
        popBack();
      },
    })),
  });

  const aspectItems = [
    { mode: AspectRatio.FIT, label: t('player_aspect_fit') },
    { mode: AspectRatio.CROP, label: t('player_aspect_crop') },
    { mode: AspectRatio.STRETCH, label: t('player_aspect_stretch') },
  ];
  const currentAspect = aspectItems.find((a) => a.mode === aspectRatioMode) || aspectItems[0];
  items.push({
    id: 'menu_aspect_ratio',
    type: 'nested',
    label: t('player_aspect_ratio'),
    value: currentAspect.label,
    items: aspectItems.map((a) => ({
      id: `aspect_${a.mode}`,
      type: 'option',
      label: a.label,
      isSelected: aspectRatioMode === a.mode,
      onClick: ({ popBack }) => {
        onSetAspectRatio?.(a.mode);
        popBack();
      },
    })),
  });

  if (isLoggedIn) {
    const deviceItems = [];
    if (isLoadingDevices) {
      deviceItems.push({
        id: 'dev_loading',
        type: 'option',
        label: t('player_devices_loading'),
        disabled: true,
      });
    } else if (!connectedDevices || connectedDevices.length === 0) {
      deviceItems.push({
        id: 'dev_empty',
        type: 'option',
        label: t('player_devices_empty'),
        disabled: true,
      });
    } else {
      for (const dev of connectedDevices) {
        deviceItems.push({
          id: `dev_${dev.deviceId}`,
          type: 'option',
          label: dev.deviceName || 'Android TV',
          value: t('player_devices_active_now'),
          onClick: () => {
            onClose?.();
            onCastToDevice?.(dev);
          },
        });
      }
    }

    items.push({
      id: 'menu_cast_device',
      type: 'nested',
      label: t('player_menu_play_on_tv'),
      value: connectedDevices?.length ? `${connectedDevices.length}` : null,
      items: deviceItems,
    });
  }

  return {
    title: t('player_settings'),
    items,
    initialMenuId,
  };
}
