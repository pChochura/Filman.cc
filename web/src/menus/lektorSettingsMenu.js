import { t } from '../i18n/index.js';
import {
  LEKTOR_VOICES,
  LektorDucking,
  LektorEdgeVoiceOptions,
  LektorTiming,
  getLektorVoiceById,
  makeSystemVoiceId,
  systemVoiceNameFromId,
} from '../data/models/lektorModels.js';
import { updateSetting } from '../data/local/settingsStore.js';
import { testLektorVoice } from '../player/lektor/lektorVoiceTester.js';

const RATE_LABELS = {
  '-20%': 'player_lektor_edge_rate_slower_20',
  '-10%': 'player_lektor_edge_rate_slower_10',
  '+0%': 'player_lektor_edge_rate_normal',
  '+5%': 'player_lektor_edge_rate_faster_5',
  '+10%': 'player_lektor_edge_rate_faster_10',
  '+15%': 'player_lektor_edge_rate_faster_15',
  '+25%': 'player_lektor_edge_rate_faster_25',
  '+35%': 'player_lektor_edge_rate_faster_35',
};

const PITCH_LABELS = {
  '-20Hz': 'player_lektor_edge_pitch_deep_20',
  '-10Hz': 'player_lektor_edge_pitch_deep_10',
  '-6Hz': 'player_lektor_edge_pitch_deep_6',
  '-3Hz': 'player_lektor_edge_pitch_deep_3',
  '+0Hz': 'player_lektor_edge_pitch_normal',
  '+3Hz': 'player_lektor_edge_pitch_high_3',
  '+6Hz': 'player_lektor_edge_pitch_high_6',
  '+10Hz': 'player_lektor_edge_pitch_high_10',
  '+20Hz': 'player_lektor_edge_pitch_high_20',
};

const VOLUME_LABELS = {
  '-50%': 'player_lektor_edge_volume_very_quiet',
  '-35%': 'player_lektor_edge_volume_much_quieter',
  '-25%': 'player_lektor_edge_volume_quieter',
  '-15%': 'player_lektor_edge_volume_toned',
  '+0%': 'player_lektor_edge_volume_default',
  '+15%': 'player_lektor_edge_volume_boosted',
  '+30%': 'player_lektor_edge_volume_max',
};

const TIMING_LABELS = {
  '-1000': 'player_lektor_timing_earlier_1000',
  '-500': 'player_lektor_timing_earlier_500',
  0: 'player_lektor_timing_on_time',
  500: 'player_lektor_timing_later_500',
  1000: 'player_lektor_timing_later_1000',
};

const DUCKING_LABELS = {
  1.0: 'player_lektor_ducking_none',
  0.8: 'player_lektor_ducking_low',
  0.7: 'player_lektor_ducking_medium',
  0.6: 'player_lektor_ducking_high',
};

function buildEdgeOptionsItems(settings, onToast) {
  return [
    {
      id: 'lektor_edge_desc',
      type: 'header',
      label: t('player_lektor_edge_options_desc'),
    },
    {
      id: 'menu_lektor_edge_rate',
      type: 'nested',
      label: t('player_lektor_edge_rate'),
      value: settings.lektorEdgeRate,
      items: LektorEdgeVoiceOptions.rates.map((r) => ({
        id: `rate_${r}`,
        type: 'option',
        label: t(RATE_LABELS[r] || r),
        isSelected: settings.lektorEdgeRate === r,
        onClick: ({ popBack }) => {
          updateSetting('lektorEdgeRate', r);
          popBack();
        },
      })),
    },
    {
      id: 'menu_lektor_edge_pitch',
      type: 'nested',
      label: t('player_lektor_edge_pitch'),
      value: settings.lektorEdgePitch,
      items: LektorEdgeVoiceOptions.pitches.map((p) => ({
        id: `pitch_${p}`,
        type: 'option',
        label: t(PITCH_LABELS[p] || p),
        isSelected: settings.lektorEdgePitch === p,
        onClick: ({ popBack }) => {
          updateSetting('lektorEdgePitch', p);
          popBack();
        },
      })),
    },
    {
      id: 'menu_lektor_edge_volume',
      type: 'nested',
      label: t('player_lektor_edge_volume'),
      value: settings.lektorEdgeVolume,
      items: LektorEdgeVoiceOptions.volumes.map((v) => ({
        id: `vol_${v}`,
        type: 'option',
        label: t(VOLUME_LABELS[v] || v),
        isSelected: settings.lektorEdgeVolume === v,
        onClick: ({ popBack }) => {
          updateSetting('lektorEdgeVolume', v);
          popBack();
        },
      })),
    },
    {
      id: 'menu_lektor_edge_calm_mode',
      type: 'nested',
      label: t('player_lektor_edge_calm_mode'),
      value: settings.lektorEdgeCalmMode
        ? t('player_lektor_edge_calm_mode_on')
        : t('player_lektor_edge_calm_mode_off'),
      items: [
        {
          id: 'calm_desc',
          type: 'header',
          label: t('player_lektor_edge_calm_mode_desc'),
        },
        {
          id: 'calm_on',
          type: 'option',
          label: t('player_lektor_edge_calm_mode_on'),
          isSelected: Boolean(settings.lektorEdgeCalmMode),
          onClick: ({ popBack }) => {
            updateSetting('lektorEdgeCalmMode', true);
            popBack();
          },
        },
        {
          id: 'calm_off',
          type: 'option',
          label: t('player_lektor_edge_calm_mode_off'),
          isSelected: !settings.lektorEdgeCalmMode,
          onClick: ({ popBack }) => {
            updateSetting('lektorEdgeCalmMode', false);
            popBack();
          },
        },
      ],
    },
    {
      id: 'lektor_edge_test_voice',
      type: 'button',
      label: t('player_lektor_test_voice'),
      onClick: () => testLektorVoice(settings.lektorVoice, onToast),
    },
  ];
}

export function buildLektorSettingsMenu({
  settings,
  systemVoices = [],
  onRefreshSystemVoices,
  onToggleEnabled,
  onToast,
}) {
  const selectedVoice = getLektorVoiceById(settings.lektorVoice);
  const selectedSystemName = systemVoiceNameFromId(settings.lektorVoice);
  const currentVoiceDisplay =
    selectedVoice.isSystem && selectedSystemName
      ? selectedSystemName
      : selectedVoice.displayName;

  const isQuickPick = selectedVoice.id === 'edge_marek' || selectedVoice.id === 'edge_zofia';
  const onlineVoices = LEKTOR_VOICES.filter((v) => v.isOnline);
  const edgeOptionsItems = buildEdgeOptionsItems(settings, onToast);

  const systemVoiceItems = [
    {
      id: 'sys_voices_hdr',
      type: 'header',
      label:
        systemVoices.length > 0
          ? t('player_lektor_system_voices')
          : t('player_lektor_system_voices_empty'),
    },
    ...systemVoices.map((sv) => {
      const id = makeSystemVoiceId(sv.name);
      return {
        id: `sys_${sv.name}`,
        type: 'option',
        label: sv.name,
        value: sv.lang,
        isSelected: settings.lektorVoice === id,
        onClick: () => updateSetting('lektorVoice', id),
      };
    }),
  ];
  if (systemVoices.length > 0) {
    systemVoiceItems.push({
      id: 'sys_preview',
      type: 'button',
      label: t('player_lektor_system_preview'),
      onClick: () => {
        const targetId = selectedSystemName
          ? settings.lektorVoice
          : makeSystemVoiceId(systemVoices[0].name);
        testLektorVoice(targetId, onToast);
      },
    });
  }
  systemVoiceItems.push({
    id: 'sys_refresh',
    type: 'button',
    label: t('player_lektor_system_refresh'),
    onClick: () => onRefreshSystemVoices?.(),
  });

  const advancedItems = [];
  if (selectedVoice.isOnline) {
    advancedItems.push({
      id: 'menu_lektor_edge_options',
      type: 'nested',
      label: t('player_lektor_edge_options'),
      value: `${settings.lektorEdgeRate}, ${settings.lektorEdgePitch}`,
      items: edgeOptionsItems,
    });
  }

  advancedItems.push({
    id: 'menu_lektor_timing',
    type: 'nested',
    label: t('player_lektor_timing'),
    value: t(TIMING_LABELS[settings.lektorTimingOffsetMs] || 'player_lektor_timing_earlier_500'),
    items: LektorTiming.offsetsMs.map((offset) => ({
      id: `timing_${offset}`,
      type: 'option',
      label: t(TIMING_LABELS[offset]),
      isSelected: settings.lektorTimingOffsetMs === offset,
      onClick: ({ popBack }) => {
        updateSetting('lektorTimingOffsetMs', offset);
        popBack();
      },
    })),
  });

  advancedItems.push({
    id: 'menu_lektor_ducking',
    type: 'nested',
    label: t('player_lektor_ducking'),
    value: t(DUCKING_LABELS[settings.lektorDuckingVolume] || 'player_lektor_ducking_none'),
    items: LektorDucking.levels.map((level) => ({
      id: `duck_${level}`,
      type: 'option',
      label: t(DUCKING_LABELS[level]),
      isSelected: Math.abs(settings.lektorDuckingVolume - level) < 0.01,
      onClick: ({ popBack }) => {
        updateSetting('lektorDuckingVolume', level);
        popBack();
      },
    })),
  });

  return {
    id: 'menu_lektor',
    type: 'nested',
    label: t('player_lektor_title'),
    value: settings.lektorEnabled ? t('player_lektor_enabled') : t('player_lektor_disabled'),
    items: [
      {
        id: 'lektor_desc',
        type: 'header',
        label: t('player_lektor_description'),
      },
      {
        id: 'lektor_off',
        type: 'option',
        label: t('player_lektor_disabled'),
        isSelected: !settings.lektorEnabled,
        onClick: () => {
          updateSetting('lektorEnabled', false);
          onToggleEnabled?.(false);
        },
      },
      {
        id: 'lektor_on',
        type: 'option',
        label: t('player_lektor_enabled'),
        isSelected: Boolean(settings.lektorEnabled),
        onClick: () => {
          updateSetting('lektorEnabled', true);
          onToggleEnabled?.(true);
        },
      },
      {
        id: 'lektor_voice_header',
        type: 'header',
        label: t('player_lektor_voice'),
      },
      {
        id: 'voice_edge_marek',
        type: 'option',
        label: 'Marek Neural (online)',
        isSelected: settings.lektorVoice === 'edge_marek',
        onClick: () => updateSetting('lektorVoice', 'edge_marek'),
      },
      {
        id: 'voice_edge_zofia',
        type: 'option',
        label: 'Zofia Neural (online)',
        isSelected: settings.lektorVoice === 'edge_zofia',
        onClick: () => updateSetting('lektorVoice', 'edge_zofia'),
      },
      {
        id: 'menu_lektor_other_voices',
        type: 'nested',
        label: t('player_lektor_other_voices'),
        value: isQuickPick ? null : currentVoiceDisplay,
        items: [
          {
            id: 'menu_lektor_online_models',
            type: 'nested',
            label: t('player_lektor_online_models'),
            items: [
              {
                id: 'online_notice',
                type: 'header',
                label: t('player_lektor_online_notice'),
              },
              ...onlineVoices.map((v) => ({
                id: `voice_${v.id}`,
                type: 'option',
                label: v.displayName,
                isSelected: settings.lektorVoice === v.id,
                onClick: () => updateSetting('lektorVoice', v.id),
              })),
              {
                id: 'online_test_btn',
                type: 'button',
                label: t('player_lektor_test_voice'),
                onClick: () => testLektorVoice(settings.lektorVoice, onToast),
              },
              {
                id: 'edge_opts_hdr',
                type: 'header',
                label: t('player_lektor_edge_options'),
              },
              ...edgeOptionsItems.slice(1, 5),
            ],
          },
          {
            id: 'menu_lektor_system_voices',
            type: 'nested',
            label: t('player_lektor_system_voice'),
            items: systemVoiceItems,
          },
        ],
      },
      {
        id: 'lektor_sub_visibility_hdr',
        type: 'header',
        label: t('player_lektor_subtitle_visibility'),
      },
      {
        id: 'lektor_show_subtitles',
        type: 'option',
        label: t('player_lektor_show_subtitles'),
        isSelected: !settings.lektorHideSubtitles,
        onClick: () => updateSetting('lektorHideSubtitles', false),
      },
      {
        id: 'lektor_hide_subtitles',
        type: 'option',
        label: t('player_lektor_hide_subtitles'),
        isSelected: Boolean(settings.lektorHideSubtitles),
        onClick: () => updateSetting('lektorHideSubtitles', true),
      },
      {
        id: 'lektor_test_voice',
        type: 'button',
        label: t('player_lektor_test_voice'),
        value: currentVoiceDisplay,
        onClick: () => testLektorVoice(settings.lektorVoice, onToast),
      },
      {
        id: 'menu_lektor_advanced',
        type: 'nested',
        label: t('player_lektor_advanced_settings'),
        items: advancedItems,
      },
    ],
  };
}
