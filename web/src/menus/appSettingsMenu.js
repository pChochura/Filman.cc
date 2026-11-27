import { t, tp } from '../i18n/index.js';
import { isPolish } from '../i18n/locale.js';
import {
  NextEpisodeAppearance,
  QUALITY_OPTIONS,
} from '../data/models/playerConstants.js';
import { updateContentRule, updateSetting } from '../data/local/settingsStore.js';
import {
  AudioPreferenceMode,
  ContentCategory,
  DEFAULT_CONTENT_RULES,
} from '../data/models/contentClassification.js';
import { logoutAccount, triggerSync } from '../data/sync/syncManager.js';
import { buildLektorSettingsMenu } from './lektorSettingsMenu.js';

const INITIAL_PERCENTAGES = [1, 2, 3, 4, 5];
const OFFSETS_SECONDS = [30, 45, 60, 75, 90, 105, 120];
const TIMER_AMOUNTS = [5, 10, 15, 20];
const INACTIVITY_MINUTES = [1, 2, 3, 4, 5];
const SLIDE_SECONDS = [5, 10, 15, 20];

function appearanceLabel(type) {
  switch (type) {
    case NextEpisodeAppearance.HIDE:
      return t('common_next_episode_appearance_dont_show');
    case NextEpisodeAppearance.SHOW:
      return t('common_next_episode_appearance_just_show');
    case NextEpisodeAppearance.SHOW_IN_OVERLAY:
      return t('common_next_episode_appearance_show_in_overlay');
    case NextEpisodeAppearance.SHOW_WITH_TIMER:
      return t('common_next_episode_appearance_show_with_timer');
    default:
      return String(type);
  }
}

function buildAutoPlayMenu(settings) {
  const initialOffsets = OFFSETS_SECONDS;
  const secondaryPercentages = INITIAL_PERCENTAGES.filter(
    (p) => p < settings.initialAppearancePercentage,
  );
  const secondaryOffsets = OFFSETS_SECONDS.filter(
    (o) => o < settings.initialAppearanceOffset,
  );

  const initialItems = [
    {
      id: 'initial_phase_desc',
      type: 'header',
      label: t('overlay_menu_next_episode_initial_phase_desc'),
    },
    {
      id: 'initial_appearance_type',
      type: 'nested',
      label: t('overlay_menu_next_episode_initial_type'),
      value: appearanceLabel(settings.initialAppearanceType),
      items: [
        NextEpisodeAppearance.SHOW,
        NextEpisodeAppearance.SHOW_IN_OVERLAY,
        NextEpisodeAppearance.HIDE,
      ].map((type) => ({
        id: `init_type_${type}`,
        type: 'option',
        label:
          type === NextEpisodeAppearance.SHOW
            ? t('common_next_episode_appearance_show')
            : appearanceLabel(type),
        isSelected: settings.initialAppearanceType === type,
        onClick: ({ popBack }) => {
          updateSetting('initialAppearanceType', type);
          popBack();
        },
      })),
    },
  ];

  if (settings.initialAppearanceType !== NextEpisodeAppearance.HIDE) {
    initialItems.push({
      id: 'initial_percentage',
      type: 'nested',
      label: t('overlay_menu_next_episode_initial_percentage'),
      value: t('common_next_episode_percentage_format', settings.initialAppearancePercentage),
      items: INITIAL_PERCENTAGES.map((pct) => ({
        id: `init_pct_${pct}`,
        type: 'option',
        label: t('common_next_episode_percentage_format', pct),
        isSelected: settings.initialAppearancePercentage === pct,
        onClick: ({ popBack }) => {
          updateSetting('initialAppearancePercentage', pct);
          popBack();
        },
      })),
    });

    initialItems.push({
      id: 'initial_appearance_offset',
      type: 'nested',
      label: t('overlay_menu_next_episode_initial_offset'),
      value: t('common_next_episode_seconds_format', settings.initialAppearanceOffset),
      items: initialOffsets.map((sec) => ({
        id: `init_off_${sec}`,
        type: 'option',
        label: t('common_next_episode_seconds_format', sec),
        isSelected: settings.initialAppearanceOffset === sec,
        onClick: ({ popBack }) => {
          updateSetting('initialAppearanceOffset', sec);
          popBack();
        },
      })),
    });
  }

  const mainItems = [
    {
      id: 'main_phase_desc',
      type: 'header',
      label: t('overlay_menu_next_episode_main_phase_desc'),
    },
    {
      id: 'secondary_appearance_type',
      type: 'nested',
      label: t('overlay_menu_next_episode_secondary_type'),
      value: appearanceLabel(settings.secondaryAppearanceType),
      items: [
        NextEpisodeAppearance.SHOW_WITH_TIMER,
        NextEpisodeAppearance.SHOW,
        NextEpisodeAppearance.SHOW_IN_OVERLAY,
        NextEpisodeAppearance.HIDE,
      ].map((type) => ({
        id: `sec_type_${type}`,
        type: 'option',
        label: appearanceLabel(type),
        isSelected: settings.secondaryAppearanceType === type,
        onClick: ({ popBack }) => {
          updateSetting('secondaryAppearanceType', type);
          popBack();
        },
      })),
    },
  ];

  if (settings.secondaryAppearanceType !== NextEpisodeAppearance.HIDE) {
    if (secondaryPercentages.length > 0) {
      mainItems.push({
        id: 'secondary_percentage',
        type: 'nested',
        label: t('overlay_menu_next_episode_secondary_percentage'),
        value: t('common_next_episode_percentage_format', settings.secondaryAppearancePercentage),
        items: secondaryPercentages.map((pct) => ({
          id: `sec_pct_${pct}`,
          type: 'option',
          label: t('common_next_episode_percentage_format', pct),
          isSelected: settings.secondaryAppearancePercentage === pct,
          onClick: ({ popBack }) => {
            updateSetting('secondaryAppearancePercentage', pct);
            popBack();
          },
        })),
      });
    }

    if (secondaryOffsets.length > 0) {
      mainItems.push({
        id: 'secondary_appearance_offset',
        type: 'nested',
        label: t('overlay_menu_next_episode_secondary_offset'),
        value: t('common_next_episode_seconds_format', settings.secondaryAppearanceOffset),
        items: secondaryOffsets.map((sec) => ({
          id: `sec_off_${sec}`,
          type: 'option',
          label: t('common_next_episode_seconds_format', sec),
          isSelected: settings.secondaryAppearanceOffset === sec,
          onClick: ({ popBack }) => {
            updateSetting('secondaryAppearanceOffset', sec);
            popBack();
          },
        })),
      });
    }

    if (settings.secondaryAppearanceType === NextEpisodeAppearance.SHOW_WITH_TIMER) {
      mainItems.push({
        id: 'secondary_timer_amount',
        type: 'nested',
        label: t('overlay_menu_next_episode_secondary_timer'),
        value: t('common_next_episode_seconds_format', settings.secondaryTimerAmount),
        items: TIMER_AMOUNTS.map((sec) => ({
          id: `sec_tmr_${sec}`,
          type: 'option',
          label: t('common_next_episode_seconds_format', sec),
          isSelected: settings.secondaryTimerAmount === sec,
          onClick: ({ popBack }) => {
            updateSetting('secondaryTimerAmount', sec);
            popBack();
          },
        })),
      });
    }
  }

  return {
    id: 'next_episode_settings',
    type: 'nested',
    label: t('overlay_menu_next_episode_settings'),
    items: [
      {
        id: 'autoplay_next_episode',
        type: 'nested',
        label: t('overlay_menu_autoplay_next'),
        value: settings.autoPlayNext
          ? t('overlay_menu_autoplay_enabled')
          : t('overlay_menu_autoplay_disabled'),
        items: [
          {
            id: 'autoplay_on',
            type: 'option',
            label: t('overlay_menu_autoplay_enabled'),
            isSelected: Boolean(settings.autoPlayNext),
            onClick: ({ popBack }) => {
              updateSetting('autoPlayNext', true);
              popBack();
            },
          },
          {
            id: 'autoplay_off',
            type: 'option',
            label: t('overlay_menu_autoplay_disabled'),
            isSelected: !settings.autoPlayNext,
            onClick: ({ popBack }) => {
              updateSetting('autoPlayNext', false);
              popBack();
            },
          },
        ],
      },
      {
        id: 'next_episode_button',
        type: 'nested',
        label: t('overlay_menu_next_episode_button'),
        items: [
          {
            id: 'initial_phase',
            type: 'nested',
            label: t('overlay_menu_next_episode_initial_phase'),
            value: appearanceLabel(settings.initialAppearanceType),
            items: initialItems,
          },
          {
            id: 'main_phase',
            type: 'nested',
            label: t('overlay_menu_next_episode_main_phase'),
            value: appearanceLabel(settings.secondaryAppearanceType),
            items: mainItems,
          },
        ],
      },
    ],
  };
}

function audioModeLabel(mode) {
  switch (mode) {
    case AudioPreferenceMode.DUBBING_PREFERRED:
      return t('audio_mode_dubbing');
    case AudioPreferenceMode.LEKTOR_PREFERRED:
      return t('audio_mode_lektor');
    case AudioPreferenceMode.ORIGINAL_SUBTITLES:
      return t('audio_mode_original_subtitles');
    case AudioPreferenceMode.ORIGINAL_ONLY:
      return t('audio_mode_original_only');
    default:
      return String(mode);
  }
}

function categoryLabel(catKey) {
  switch (catKey) {
    case ContentCategory.KIDS_FAMILY:
      return t('content_category_kids');
    case ContentCategory.ANIME:
      return t('content_category_anime');
    case ContentCategory.NATIVE_DOMESTIC:
      return t('content_category_native_pl');
    case ContentCategory.FOREIGN_MAINSTREAM:
      return t('content_category_foreign_en');
    case ContentCategory.FOREIGN_WORLD:
      return t('content_category_foreign_world');
    case ContentCategory.DOCUMENTARY:
      return t('content_category_documentary');
    default:
      return catKey;
  }
}

function buildContentRulesMenu(settings) {
  const categories = [
    ContentCategory.KIDS_FAMILY,
    ContentCategory.ANIME,
    ContentCategory.NATIVE_DOMESTIC,
    ContentCategory.FOREIGN_MAINSTREAM,
    ContentCategory.FOREIGN_WORLD,
    ContentCategory.DOCUMENTARY,
  ];

  const allowedModes = [
    AudioPreferenceMode.DUBBING_PREFERRED,
    AudioPreferenceMode.LEKTOR_PREFERRED,
    AudioPreferenceMode.ORIGINAL_SUBTITLES,
    AudioPreferenceMode.ORIGINAL_ONLY,
  ];

  return {
    id: 'content_rules_menu',
    type: 'nested',
    label: t('overlay_menu_content_rules'),
    items: categories.map((catKey) => {
      const rule = settings.contentRules?.[catKey] || DEFAULT_CONTENT_RULES[catKey];
      return {
        id: `content_rule_${catKey}`,
        type: 'nested',
        label: categoryLabel(catKey),
        value: audioModeLabel(rule?.audioMode),
        items: allowedModes.map((mode) => ({
          id: `mode_${catKey}_${mode}`,
          type: 'option',
          label: audioModeLabel(mode),
          isSelected: rule?.audioMode === mode,
          onClick: ({ popBack }) => {
            updateContentRule(catKey, { audioMode: mode });
            popBack();
          },
        })),
      };
    }),
  };
}

function buildScreensaverMenu(settings) {
  const inactivityMin = Math.max(1, Math.round(settings.screensaverInactivityTime / 60_000));
  const slideSec = Math.max(5, Math.round(settings.screensaverSlideDuration / 1000));

  const items = [
    {
      id: 'screensaver_enabled',
      type: 'nested',
      label: t('common_enabled'),
      value: settings.screensaverEnabled
        ? t('common_enabled_true')
        : t('common_enabled_false'),
      items: [
        {
          id: 'ss_on',
          type: 'option',
          label: t('common_enabled_true'),
          isSelected: Boolean(settings.screensaverEnabled),
          onClick: ({ popBack }) => {
            updateSetting('screensaverEnabled', true);
            popBack();
          },
        },
        {
          id: 'ss_off',
          type: 'option',
          label: t('common_enabled_false'),
          isSelected: !settings.screensaverEnabled,
          onClick: ({ popBack }) => {
            updateSetting('screensaverEnabled', false);
            popBack();
          },
        },
      ],
    },
  ];

  if (settings.screensaverEnabled) {
    items.push({
      id: 'screensaver_inactivity',
      type: 'nested',
      label: t('common_inactivity_time'),
      value: tp('common_time_format_minutes', inactivityMin),
      items: INACTIVITY_MINUTES.map((min) => ({
        id: `ss_inact_${min}`,
        type: 'option',
        label: tp('common_time_format_minutes', min),
        isSelected: inactivityMin === min,
        onClick: ({ popBack }) => {
          updateSetting('screensaverInactivityTime', min * 60_000);
          popBack();
        },
      })),
    });

    items.push({
      id: 'screensaver_slide',
      type: 'nested',
      label: t('common_slide_duration'),
      value: tp('common_time_format_seconds', slideSec),
      items: SLIDE_SECONDS.map((sec) => ({
        id: `ss_slide_${sec}`,
        type: 'option',
        label: tp('common_time_format_seconds', sec),
        isSelected: slideSec === sec,
        onClick: ({ popBack }) => {
          updateSetting('screensaverSlideDuration', sec * 1000);
          popBack();
        },
      })),
    });
  }

  return {
    id: 'screensaver_settings',
    type: 'nested',
    label: t('common_screensaver_settings'),
    items,
  };
}

function buildLanguageMenu(settings) {
  const currentLang = settings.appLanguage || (isPolish() ? 'pl' : 'en');
  const currentLangLabel =
    currentLang === 'pl'
      ? t('overlay_menu_language_pl')
      : t('overlay_menu_language_en');

  return {
    id: 'app_language',
    type: 'nested',
    label: t('overlay_menu_language'),
    value: currentLangLabel,
    items: [
      {
        id: 'lang_en',
        type: 'option',
        label: t('overlay_menu_language_en'),
        isSelected: currentLang === 'en',
        onClick: ({ popBack }) => {
          updateSetting('appLanguage', 'en');
          popBack();
        },
      },
      {
        id: 'lang_pl',
        type: 'option',
        label: t('overlay_menu_language_pl'),
        isSelected: currentLang === 'pl',
        onClick: ({ popBack }) => {
          updateSetting('appLanguage', 'pl');
          popBack();
        },
      },
    ],
  };
}

export function buildAppSettingsMenu({
  settings,
  session,
  syncStatus,
  systemVoices,
  onRefreshSystemVoices,
  onNavigate,
  onClose,
  onToast,
}) {
  const qualityLabel =
    settings.preferredQuality === 'auto'
      ? t('overlay_menu_quality_auto')
      : settings.preferredQuality;

  let syncLabel = t('overlay_menu_sync_now');
  if (syncStatus?.state === 'syncing') syncLabel = t('overlay_menu_syncing');
  else if (syncStatus?.state === 'success') syncLabel = t('overlay_menu_synced_just_now');
  else if (syncStatus?.state === 'error') syncLabel = t('overlay_menu_sync_error');

  const items = [
    {
      id: 'playback_header',
      type: 'header',
      label: t('overlay_menu_header_playback'),
    },
    {
      id: 'preferred_quality',
      type: 'nested',
      label: t('overlay_menu_preferred_quality'),
      value: qualityLabel,
      items: QUALITY_OPTIONS.map((q) => ({
        id: `quality_${q}`,
        type: 'option',
        label: q === 'auto' ? t('overlay_menu_quality_auto') : q,
        isSelected: settings.preferredQuality === q,
        onClick: ({ popBack }) => {
          updateSetting('preferredQuality', q);
          popBack();
        },
      })),
    },
    buildContentRulesMenu(settings),
    buildAutoPlayMenu(settings),
    buildLektorSettingsMenu({
      settings,
      systemVoices,
      onRefreshSystemVoices,
      onToast,
    }),
    {
      id: 'library_header',
      type: 'header',
      label: t('overlay_menu_header_library'),
    },
    {
      id: 'watch_history',
      type: 'button',
      label: t('overlay_menu_watch_history'),
      onClick: () => {
        onClose?.();
        onNavigate?.({ name: 'watchhistory' });
      },
    },
    {
      id: 'favorites',
      type: 'button',
      label: t('overlay_menu_favorites'),
      onClick: () => {
        onClose?.();
        onNavigate?.({ name: 'favorites' });
      },
    },
    {
      id: 'other_header',
      type: 'header',
      label: t('overlay_menu_header_other'),
    },
    buildLanguageMenu(settings),
    buildScreensaverMenu(settings),
  ];

  if (session?.isLoggedIn) {
    items.push({
      id: 'sync_now',
      type: 'button',
      label: syncLabel,
      onClick: () => triggerSync(),
    });
    items.push({
      id: 'logout',
      type: 'nested',
      label: t('overlay_menu_logout'),
      value: session.username
        ? t('overlay_menu_logged_in_as', session.username)
        : t('overlay_menu_account'),
      items: [
        {
          id: 'logout_confirm_hdr',
          type: 'header',
          label: t('overlay_menu_are_you_sure'),
        },
        {
          id: 'logout_yes',
          type: 'button',
          label: t('overlay_menu_yes'),
          onClick: () => {
            logoutAccount();
            onClose?.();
          },
        },
        {
          id: 'logout_no',
          type: 'button',
          label: t('overlay_menu_no'),
          onClick: ({ popBack }) => popBack(),
        },
      ],
    });

    items.push({
      id: 'pair_tv',
      type: 'button',
      label: t('overlay_menu_pair_device'),
      onClick: () => {
        onClose?.();
        onNavigate?.({ name: 'pair' });
      },
    });
  } else {
    items.push({
      id: 'login',
      type: 'button',
      label: t('overlay_menu_login'),
      onClick: () => {
        onClose?.();
        onNavigate?.({ name: 'login' });
      },
    });

    items.push({
      id: 'pair_tv',
      type: 'button',
      label: t('overlay_menu_pair_device'),
      onClick: () => {
        onClose?.();
        onNavigate?.({ name: 'pair' });
      },
    });
  }

  items.push({
    id: 'footer_version',
    type: 'footer',
    label: t('overlay_menu_app_version', '2.44 (web)'),
  });

  return {
    title: t('overlay_menu_settings'),
    items,
  };
}
