import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import Hls from 'hls.js';
import { t } from '../i18n/index.js';
import { getMediaDetails } from '../data/api/tmdbClient.js';
import {
  extractYoutubeVideoId,
  findBestSource,
  isEmbedUrl,
  isPolishSource,
  prefetchStreamsForMediaUrl,
  resolveStreamsWithUpdates,
} from '../data/api/videoUrlResolver.js';
import {
  downloadSubtitleCandidate,
  fetchRawSubtitleContent,
  findBestPolishSubtitle,
  findMatchingSubtitle,
  findReferenceSubtitleForAlignment,
  isPolishSubtitle,
  searchSubtitleCandidates,
} from '../data/api/subtitleRepository.js';
import { SubtitleWarning, diagnoseSubtitle } from '../data/api/subtitleDiagnostics.js';
import {
  activeCuesAt,
  cleanDisplayCueText,
  createSubtitleTimeline,
  detectDialogueEndMs,
  hasSpokenDialogueAt,
} from '../data/api/subtitleTimeline.js';
import { alignSubtitleTracks } from '../data/api/subtitleTrackAligner.js';
import { parseSrtOrVtt } from '../player/lektor/srtParser.js';
import { createLektorPlaybackController } from '../player/lektor/lektorPlaybackController.js';
import { getPolishSystemVoices } from '../player/lektor/systemLektorSpeech.js';
import {
  getProgressForUrl,
  markAsWatched,
  savePlaybackProgress,
} from '../data/local/progressStore.js';
import { triggerSync } from '../data/sync/syncManager.js';
import {
  getEffectiveSecondaryAppearanceType,
  updateSetting,
  useSettings,
} from '../data/local/settingsStore.js';
import {
  getTvShowSourceSettings,
  saveTvShowSourceSettings,
} from '../data/local/tvShowSettingsStore.js';
import { getMediaKey, getTvShowKey, parseMediaRef } from '../data/models/mediaRef.js';
import {
  ContentCategory,
  DEFAULT_CONTENT_RULES,
  classifyContent,
  hasPolishSpokenAudio,
  isOriginallyPolish,
  resolveShouldEnableLektor,
  resolveSubtitlesEnabled,
} from '../data/models/contentClassification.js';
import {
  AspectRatio,
  EdgeType,
  MENU_SOURCES_ID,
  NextEpisodeAppearance,
} from '../data/models/playerConstants.js';
import { buildPlayerMenu } from '../menus/playerMenuBuilder.js';
import { useSession } from '../data/local/sessionStore.js';
import { fetchConnectedDevices, sendPlayCommandToDevice } from '../data/api/deviceApiClient.js';
import TiwiButton from '../components/TiwiButton.jsx';
import TiwiIconButton from '../components/TiwiIconButton.jsx';
import TiwiOverlayMenu from '../components/TiwiOverlayMenu.jsx';
import { IconBack, IconClose, IconStar } from '../components/Icons.jsx';
import ErrorSection from '../components/sections/ErrorSection.jsx';
import PlayerControls from './PlayerControls.jsx';

const HUD_HIDE_DELAY_MS = 5000;
const BUFFERING_PROMPT_DELAY_MS = 5000;
const QUICK_SEEK_COMMIT_DELAY_MS = 1000;
const PROGRESS_SAVE_INTERVAL_MS = 30_000;
const PREFETCH_THRESHOLD_MS = 180_000;

function isHlsUrl(rawUrl) {
  if (!rawUrl) return false;
  const lower = String(rawUrl).toLowerCase();
  return lower.includes('.m3u8') || lower.includes('m3u8-proxy') || lower.includes('/hls/');
}

function needsProxyByDefault(src) {
  if (!src?.url) return false;
  if (src.headers && Object.keys(src.headers).length > 0) return true;
  if (
    typeof window !== 'undefined' &&
    window.location.protocol === 'https:' &&
    String(src.url).startsWith('http:')
  ) {
    return true;
  }
  return false;
}

function buildProxiedUrl(rawUrl, headers = {}) {
  const base = `/api/proxy?url=${encodeURIComponent(rawUrl)}`;
  if (headers && Object.keys(headers).length > 0) {
    return `${base}&headers=${encodeURIComponent(JSON.stringify(headers))}`;
  }
  return base;
}

function getAspectClass(mode) {
  if (mode === AspectRatio.CROP) return 'player-video player-video--crop';
  if (mode === AspectRatio.STRETCH) return 'player-video player-video--stretch';
  return 'player-video player-video--fit';
}

function YoutubeTrailerPlayer({
  youtubeId,
  title,
  description,
  isTrailer = true,
  onBack,
  onPlaybackChange,
}) {
  const [hudVisible, setHudVisible] = useState(true);
  const hideTimeoutRef = useRef(null);

  useEffect(() => {
    onPlaybackChange?.(true);
    return () => onPlaybackChange?.(false);
  }, [onPlaybackChange]);

  const scheduleHudHide = useCallback(() => {
    if (hideTimeoutRef.current) clearTimeout(hideTimeoutRef.current);
    hideTimeoutRef.current = setTimeout(() => {
      setHudVisible(false);
    }, HUD_HIDE_DELAY_MS);
  }, []);

  useEffect(() => {
    scheduleHudHide();
    const handleMouseMove = () => {
      setHudVisible(true);
      scheduleHudHide();
    };
    window.addEventListener('mousemove', handleMouseMove);
    return () => {
      if (hideTimeoutRef.current) clearTimeout(hideTimeoutRef.current);
      window.removeEventListener('mousemove', handleMouseMove);
    };
  }, [scheduleHudHide]);

  useEffect(() => {
    const onKeyDown = (e) => {
      setHudVisible(true);
      scheduleHudHide();
      if (e.key === 'Escape' || e.key === 'Backspace') {
        e.preventDefault();
        e.stopPropagation();
        onBack?.();
      }
    };
    window.addEventListener('keydown', onKeyDown, true);
    return () => window.removeEventListener('keydown', onKeyDown, true);
  }, [onBack, scheduleHudHide]);

  const trailerLabel = t('player_trailer');
  const fullTitle = useMemo(() => {
    if (!title) return '';
    const lower = title.toLowerCase();
    if (isTrailer && !lower.includes('trailer') && !lower.includes('zwiastun')) {
      return `${title} - ${trailerLabel}`;
    }
    return title;
  }, [title, isTrailer, trailerLabel]);

  return (
    <div className="player-root" data-hud-visible={hudVisible ? 'true' : 'false'}>
      <iframe
        className="player-iframe"
        src={`https://www.youtube-nocookie.com/embed/${encodeURIComponent(youtubeId)}?autoplay=1&rel=0`}
        title={fullTitle || 'Trailer'}
        allow="autoplay; encrypted-media; fullscreen"
        allowFullScreen
      />
      {hudVisible && (
        <div className="player-top-controls">
          <TiwiIconButton
            icon={<IconBack size={20} />}
            tooltip={t('common_back')}
            tooltipPosition="below"
            variant="surface"
            focusZone="player"
            onClick={(e) => {
              e?.preventDefault?.();
              e?.stopPropagation?.();
              onBack?.();
            }}
          />
        </div>
      )}
      {hudVisible && (fullTitle || description) && (
        <div
          className="player-bottom-controls"
          style={{
            pointerEvents: 'none',
            background: 'linear-gradient(to top, rgba(0,0,0,0.85) 0%, rgba(0,0,0,0) 100%)',
          }}
        >
          <div className="player-details-and-next">
            <div className="player-media-details">
              {fullTitle && <div className="type-title-large">{fullTitle}</div>}
              {description && (
                <div
                  className="type-body-medium"
                  style={{
                    color: 'var(--color-on-surface-variant)',
                    maxWidth: '600px',
                    display: '-webkit-box',
                    WebkitLineClamp: 3,
                    WebkitBoxOrient: 'vertical',
                    overflow: 'hidden',
                  }}
                >
                  {description}
                </div>
              )}
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

function VideoPlayerCore({
  url,
  isTvShow: isTvShowProp,
  isKids: isKidsProp,
  onBack,
  onNavigate,
  onToast,
  onPlaybackChange,
}) {
  const settings = useSettings();
  const session = useSession();
  const [connectedDevices, setConnectedDevices] = useState([]);
  const [isLoadingDevices, setIsLoadingDevices] = useState(false);

  const parsedRef = useMemo(() => parseMediaRef(url, isTvShowProp), [url, isTvShowProp]);
  const isTvShow = isTvShowProp !== undefined ? Boolean(isTvShowProp) : Boolean(parsedRef?.isTvShow);
  const isKids = Boolean(isKidsProp || (url && url.includes('forkids')));

  const [detailedMedia, setDetailedMedia] = useState(null);
  const [alternativeSources, setAlternativeSources] = useState([]);
  const [streamsSettled, setStreamsSettled] = useState(false);
  const [streamsMessage, setStreamsMessage] = useState(null);
  const [streamAttempt, setStreamAttempt] = useState(0);

  const [activeSource, setActiveSource] = useState(null);
  const [useProxyForSource, setUseProxyForSource] = useState(false);
  const [fatalError, setFatalError] = useState(null);

  const [isPlaying, setIsPlaying] = useState(false);
  const [isBuffering, setIsBuffering] = useState(true);
  const [showBufferingPrompt, setShowBufferingPrompt] = useState(false);
  const [positionMs, setPositionMs] = useState(0);
  const [durationMs, setDurationMs] = useState(0);
  const [seekTargetMs, setSeekTargetMs] = useState(null);
  const [isScrubbing, setIsScrubbing] = useState(false);
  const [quickSeekDeltaMs, setQuickSeekDeltaMs] = useState(null);
  const [hudVisible, setHudVisible] = useState(true);

  const [playbackSpeed, setPlaybackSpeed] = useState(1.0);
  const [aspectRatioMode, setAspectRatioMode] = useState(AspectRatio.FIT);
  const [audioTracks, setAudioTracks] = useState([]);
  const [selectedAudioTrackId, setSelectedAudioTrackId] = useState(null);

  const [downloadedSubtitles, setDownloadedSubtitles] = useState([]);
  const [selectedSubtitleUrl, setSelectedSubtitleUrl] = useState(null);
  const [subtitleSearchResults, setSubtitleSearchResults] = useState([]);
  const [isSearchingSubtitles, setIsSearchingSubtitles] = useState(false);
  const [subtitleOffsetMs, setSubtitleOffsetMs] = useState(0);
  const [subtitleFpsScale, setSubtitleFpsScale] = useState(1.0);
  const [isAutoAligning, setIsAutoAligning] = useState(false);
  const [activeSubtitleText, setActiveSubtitleText] = useState('');
  const [isLektorActive, setIsLektorActive] = useState(false);

  const [menuOpenTarget, setMenuOpenTarget] = useState(null);
  const [systemVoices, setSystemVoices] = useState(() => getPolishSystemVoices());

  const loadDevices = useCallback(async () => {
    if (!session?.isLoggedIn) return;
    setIsLoadingDevices(true);
    try {
      const devs = await fetchConnectedDevices();
      setConnectedDevices(devs);
    } finally {
      setIsLoadingDevices(false);
    }
  }, [session?.isLoggedIn]);

  useEffect(() => {
    if (menuOpenTarget) {
      loadDevices();
    }
  }, [menuOpenTarget, loadDevices]);

  const handleCastToDevice = useCallback(
    async (targetDevice) => {
      try {
        if (videoRef.current) {
          videoRef.current.pause();
          setIsPlaying(false);
        }
        const currentProgressMs = Math.round((videoRef.current?.currentTime || 0) * 1000);
        const mediaTitle = detailedMedia?.baseItem?.titlePl || detailedMedia?.baseItem?.title || null;

        const seriesUrl = isTvShow ? (parsedRef?.parentKey || detailedMedia?.baseItem?.url || url) : url;
        const targetEpisodeUrl = isTvShow ? url : null;

        await sendPlayCommandToDevice(targetDevice.deviceId, {
          url: seriesUrl,
          episodeUrl: targetEpisodeUrl,
          title: mediaTitle,
          progressMs: currentProgressMs,
          autoPlay: true,
        });

        onToast?.(t('player_devices_sent_success', targetDevice.deviceName));
      } catch (err) {
        onToast?.(t('player_devices_sent_error', err.message || targetDevice.deviceName));
      }
    },
    [detailedMedia, isTvShow, onToast, parsedRef, url],
  );

  const [nextEpisodeTimerProgress, setNextEpisodeTimerProgress] = useState(null);
  const [nextEpisodeCancelled, setNextEpisodeCancelled] = useState(false);
  const [isFullscreen, setIsFullscreen] = useState(false);

  const playerRootRef = useRef(null);
  const videoRef = useRef(null);
  const hlsRef = useRef(null);
  const playBtnRef = useRef(null);
  const seekBarRef = useRef(null);
  const lektorRef = useRef(null);
  const clickTimerRef = useRef(null);

  const resumeMsRef = useRef(0);
  const initialResumeMsRef = useRef(0);
  const hasResumedPlaybackRef = useRef(false);
  const failedUrlsRef = useRef(new Set());
  const useProxyRef = useRef(false);
  const baseItemRef = useRef(null);
  const positionRef = useRef(0);
  const durationRef = useRef(0);
  const playbackSpeedRef = useRef(1.0);
  const duckVolumeRef = useRef(1.0);
  const isPlayingRef = useRef(false);
  const isBufferingRef = useRef(true);
  const isEmbedRef = useRef(false);
  const subtitleOffsetRef = useRef(0);
  const subtitleFpsRef = useRef(1.0);
  const parsedCuesRef = useRef([]);
  const subtitleTimelineRef = useRef(null);
  const lastSubtitleTextRef = useRef('');
  const autoLektorSourceKeyRef = useRef(null);
  const autoAlignedKeyRef = useRef(null);
  const alignAbortControllerRef = useRef(null);
  const initialSubtitlePickedRef = useRef(null);
  const userDisabledSubtitlesRef = useRef(false);
  const prefetchedNextRef = useRef(false);
  const mediaErrorRecoveryUsedRef = useRef(false);

  const hudTimerRef = useRef(null);
  const quickSeekTimerRef = useRef(null);
  const quickSeekCountRef = useRef(0);
  const quickSeekDeltaRef = useRef(0);
  const quickSeekBaseRef = useRef(0);

  const isMenuOpen = Boolean(menuOpenTarget);
  const isEmbed = Boolean(activeSource && isEmbedUrl(activeSource.url));
  isEmbedRef.current = isEmbed;
  playbackSpeedRef.current = playbackSpeed;
  subtitleOffsetRef.current = subtitleOffsetMs;
  subtitleFpsRef.current = subtitleFpsScale;
  isBufferingRef.current = isBuffering;
  positionRef.current = positionMs;
  durationRef.current = durationMs;

  const tvShowKey = useMemo(() => {
    const base = detailedMedia?.baseItem;
    return getMediaKey(base || { url, isTvShow });
  }, [detailedMedia, url, isTvShow]);

  const tvShowSettings = useMemo(
    () => (tvShowKey ? getTvShowSourceSettings(tvShowKey) : null),
    [tvShowKey],
  );
  const tvShowSettingsRef = useRef(tvShowSettings);
  tvShowSettingsRef.current = tvShowSettings;

  // Inform App shell so screensaver stays suppressed
  useEffect(() => {
    onPlaybackChange?.(true);
    return () => onPlaybackChange?.(false);
  }, [onPlaybackChange]);

  // Listen for browser system voice updates
  const refreshSystemVoices = useCallback(() => {
    setSystemVoices(getPolishSystemVoices());
  }, []);

  useEffect(() => {
    refreshSystemVoices();
    if (typeof window === 'undefined' || !window.speechSynthesis) return undefined;
    window.speechSynthesis.addEventListener?.('voiceschanged', refreshSystemVoices);
    return () => {
      window.speechSynthesis.removeEventListener?.('voiceschanged', refreshSystemVoices);
    };
  }, [refreshSystemVoices]);

  // Initialize Lektor TTS controller
  useEffect(() => {
    const ctrl = createLektorPlaybackController({
      onDuckingVolumeChange: (vol) => {
        const clamped = Math.max(0, Math.min(1, Number(vol) || 1));
        duckVolumeRef.current = clamped;
        if (videoRef.current) {
          videoRef.current.volume = clamped;
        }
      },
      nativePosition: () => {
        const v = videoRef.current;
        if (!v || !Number.isFinite(v.currentTime)) return null;
        const rawMs = Math.round(v.currentTime * 1000);
        const scale = Math.max(0.001, subtitleFpsRef.current || 1.0);
        return Math.max(0, Math.trunc((rawMs - subtitleOffsetRef.current) / scale));
      },
      nativeIsPlaying: () => {
        const v = videoRef.current;
        if (!v) return false;
        return !v.paused && !v.ended && v.readyState >= 2;
      },
      onError: (msg) => onToast?.(msg),
    });
    lektorRef.current = ctrl;
    return () => {
      ctrl.release();
      lektorRef.current = null;
    };
  }, [onToast]);

  // Sync settings changes to Lektor controller
  useEffect(() => {
    const ctrl = lektorRef.current;
    if (!ctrl) return;
    ctrl.setVoiceId(settings.lektorVoice);
    ctrl.setDuckingVolume(settings.lektorDuckingVolume);
    ctrl.setTimingOffsetMs(settings.lektorTimingOffsetMs);
    ctrl.setEdgeVoiceOptions({
      rate: settings.lektorEdgeRate,
      pitch: settings.lektorEdgePitch,
      volume: settings.lektorEdgeVolume,
      calmMode: settings.lektorEdgeCalmMode,
    });
    const enabled = Boolean(isLektorActive && !isEmbed);
    ctrl.setEnabled(enabled);
    if (!enabled) {
      ctrl.clearSubtitles();
    }
  }, [
    isLektorActive,
    settings.lektorVoice,
    settings.lektorDuckingVolume,
    settings.lektorTimingOffsetMs,
    settings.lektorEdgeRate,
    settings.lektorEdgePitch,
    settings.lektorEdgeVolume,
    settings.lektorEdgeCalmMode,
    isEmbed,
  ]);

  // Load media details & initial resume position
  useEffect(() => {
    let cancelled = false;
    prefetchedNextRef.current = false;
    failedUrlsRef.current.clear();
    resumeMsRef.current = 0;
    initialResumeMsRef.current = 0;
    hasResumedPlaybackRef.current = false;

    const existing = getProgressForUrl(url);
    if (existing && existing.kind === 'InProgress' && existing.progressMs > 0) {
      resumeMsRef.current = existing.progressMs;
      initialResumeMsRef.current = existing.progressMs;
      positionRef.current = existing.progressMs;
      setPositionMs(existing.progressMs);
    }

    getMediaDetails(url)
      .then((details) => {
        if (cancelled || !details) return;
        setDetailedMedia(details);
        baseItemRef.current = details.baseItem;
        if (details.baseItem?.isTvShow) {
          const showKey = getTvShowKey(details.baseItem);
          const saved = showKey ? getTvShowSourceSettings(showKey) : null;
          if (saved) {
            if (typeof saved.playbackSpeed === 'number') setPlaybackSpeed(saved.playbackSpeed);
            if (typeof saved.aspectRatioMode === 'number') setAspectRatioMode(saved.aspectRatioMode);
            if (typeof saved.subtitleOffsetMs === 'number') setSubtitleOffsetMs(saved.subtitleOffsetMs);
          }
        }
      })
      .catch(() => {});

    return () => {
      cancelled = true;
    };
  }, [url]);

  const selectSourceInternal = useCallback((src, preserveCurrentTime = false) => {
    if (!src) return;
    alignAbortControllerRef.current?.abort();
    alignAbortControllerRef.current = null;
    setIsAutoAligning(false);
    if (preserveCurrentTime && videoRef.current && Number.isFinite(videoRef.current.currentTime)) {
      const ms = Math.round(videoRef.current.currentTime * 1000);
      if (ms > 5000) {
        resumeMsRef.current = ms;
        initialResumeMsRef.current = ms;
      }
    }
    const useProxy = needsProxyByDefault(src);
    useProxyRef.current = useProxy;
    mediaErrorRecoveryUsedRef.current = false;
    setFatalError(null);
    setAudioTracks([]);
    setSelectedAudioTrackId(null);
    setSelectedSubtitleUrl(null);
    userDisabledSubtitlesRef.current = false;
    setActiveSubtitleText('');
    setUseProxyForSource(useProxy);
    setActiveSource(src);
    setIsBuffering(!isEmbedUrl(src.url));
  }, []);

  // Resolve streams (live SSE updates + failover recovery when late sources arrive)
  useEffect(() => {
    setAlternativeSources([]);
    setStreamsSettled(false);
    setStreamsMessage(null);
    setActiveSource(null);
    setFatalError(null);
    setIsBuffering(true);

    const unsubscribe = resolveStreamsWithUpdates({
      mediaUrl: url,
      onSourcesUpdate: (sorted, status, msg) => {
        setAlternativeSources(sorted);
        if (msg) setStreamsMessage(msg);
        if (sorted.length > 0) {
          setActiveSource((prev) => {
            const hasPrev = prev && !failedUrlsRef.current.has(prev.url);
            const prevIsPolish = hasPrev && isPolishSource(prev);
            if (hasPrev && (prevIsPolish || !isBuffering)) return prev;

            const candidates = sorted.filter((s) => !failedUrlsRef.current.has(s.url));
            if (candidates.length === 0) return prev;
            const baseItem = detailedMedia?.baseItem;
            const countries = detailedMedia?.metaInfo?.countries || [];
            const isPolishProduction = isOriginallyPolish({
              originalLanguage: baseItem?.originalLanguage,
              countries,
              allSources: candidates,
            });
            const category = classifyContent(
              {
                genreIds: baseItem?.genreIds || [],
                originalLanguage: baseItem?.originalLanguage || '',
                countries,
              },
              isKids,
            );
            const effectiveCategory = isPolishProduction ? ContentCategory.NATIVE_DOMESTIC : category;
            const contentRule = settings.contentRules?.[effectiveCategory] || DEFAULT_CONTENT_RULES[effectiveCategory];
            const preferredVersion =
              contentRule.audioMode === 'dubbing'
                ? 'dub'
                : contentRule.audioMode === 'lektor'
                ? 'lektor'
                : isPolishProduction
                ? 'pl'
                : 'napisy';
            const targetSettingsWithRule = !tvShowSettingsRef.current?.version && contentRule ? {
              ...(tvShowSettingsRef.current || {}),
              version: preferredVersion,
            } : tvShowSettingsRef.current;

            const picked =
              findBestSource(candidates, targetSettingsWithRule) ||
              findBestSource(candidates, tvShowSettingsRef.current);
            if (picked) {
              const useProxy = needsProxyByDefault(picked);
              useProxyRef.current = useProxy;
              mediaErrorRecoveryUsedRef.current = false;
              setFatalError(null);
              setUseProxyForSource(useProxy);
              setIsBuffering(!isEmbedUrl(picked.url));
              return picked;
            }
            return prev;
          });
        }
        if (status === 'READY' || status === 'ERROR') {
          setStreamsSettled(true);
        }
      },
      onSettled: ({ links, message }) => {
        setAlternativeSources(links || []);
        if (message) setStreamsMessage(message);
        setStreamsSettled(true);
      },
    });

    return () => {
      unsubscribe();
    };
  }, [url, streamAttempt]);

  const handleRetryStreams = useCallback(() => {
    failedUrlsRef.current.clear();
    setStreamAttempt((n) => n + 1);
  }, []);

  const handleSourcePlaybackFailure = useCallback(() => {
    const current = activeSource;
    if (!current) return;

    // Step 1: retry same source via /api/proxy if not already proxied
    if (!useProxyRef.current) {
      useProxyRef.current = true;
      setUseProxyForSource(true);
      setIsBuffering(true);
      return;
    }

    // Step 2: mark current failed and advance to next untried alternative source
    failedUrlsRef.current.add(current.url);
    const nextCandidate = alternativeSources.find((s) => !failedUrlsRef.current.has(s.url));
    if (nextCandidate) {
      selectSourceInternal(nextCandidate, true);
      return;
    }

    if (streamsSettled) {
      setIsBuffering(false);
      setFatalError(t('player_all_sources_failed'));
    }
  }, [activeSource, alternativeSources, streamsSettled, selectSourceInternal]);

  // Attach HLS or native HTML5 video whenever activeSource or proxy mode changes
  useEffect(() => {
    if (!activeSource || isEmbedUrl(activeSource.url)) {
      if (hlsRef.current) {
        hlsRef.current.destroy();
        hlsRef.current = null;
      }
      setIsBuffering(false);
      return undefined;
    }

    const videoEl = videoRef.current;
    if (!videoEl) return undefined;

    if (hlsRef.current) {
      hlsRef.current.destroy();
      hlsRef.current = null;
    }

    const playbackUrl = useProxyForSource
      ? buildProxiedUrl(activeSource.url, activeSource.headers)
      : activeSource.url;

    let cancelled = false;
    setIsBuffering(true);

    const applyPostLoadSetup = () => {
      if (cancelled) return;
      const resumeTarget =
        resumeMsRef.current > 0
          ? resumeMsRef.current
          : !hasResumedPlaybackRef.current
            ? initialResumeMsRef.current
            : 0;

      if (resumeTarget > 0) {
        const sec = resumeTarget / 1000;
        try {
          if (Number.isFinite(videoEl.duration) && videoEl.duration > 0) {
            videoEl.currentTime = Math.min(Math.max(0, videoEl.duration - 1), sec);
          } else {
            videoEl.currentTime = sec;
          }
        } catch {}
      }
      videoEl.playbackRate = playbackSpeedRef.current;
      videoEl.volume = duckVolumeRef.current;
      videoEl.play().catch(() => {
        setIsBuffering(false);
        setHudVisible(true);
      });
    };

    if (isHlsUrl(activeSource.url) && Hls.isSupported()) {
      const targetResume =
        resumeMsRef.current > 0
          ? resumeMsRef.current
          : !hasResumedPlaybackRef.current
            ? initialResumeMsRef.current
            : 0;
      const startPosSec = targetResume > 0 ? targetResume / 1000 : -1;
      const hls = new Hls({
        enableWorker: true,
        lowLatencyMode: false,
        startPosition: startPosSec,
      });
      hlsRef.current = hls;

      hls.on(Hls.Events.MANIFEST_PARSED, () => {
        if (cancelled) return;
        const tracks = (hls.audioTracks || []).map((tr, idx) => ({
          id: tr.id ?? idx,
          label: tr.name || tr.lang || t('player_audio_track_default', idx + 1),
        }));
        setAudioTracks(tracks);
        setSelectedAudioTrackId(hls.audioTrack ?? tracks[0]?.id ?? null);
        applyPostLoadSetup();
      });

      hls.on(Hls.Events.AUDIO_TRACKS_UPDATED, () => {
        if (cancelled) return;
        const tracks = (hls.audioTracks || []).map((tr, idx) => ({
          id: tr.id ?? idx,
          label: tr.name || tr.lang || t('player_audio_track_default', idx + 1),
        }));
        setAudioTracks(tracks);
        setSelectedAudioTrackId(hls.audioTrack ?? tracks[0]?.id ?? null);
      });

      hls.on(Hls.Events.AUDIO_TRACK_SWITCHED, (_evt, data) => {
        if (!cancelled) setSelectedAudioTrackId(data?.id ?? null);
      });

      hls.on(Hls.Events.ERROR, (_evt, data) => {
        if (cancelled || !data?.fatal) return;
        if (data.type === Hls.ErrorTypes.MEDIA_ERROR && !mediaErrorRecoveryUsedRef.current) {
          mediaErrorRecoveryUsedRef.current = true;
          hls.recoverMediaError();
          return;
        }
        hls.destroy();
        if (hlsRef.current === hls) hlsRef.current = null;
        handleSourcePlaybackFailure();
      });

      hls.loadSource(playbackUrl);
      hls.attachMedia(videoEl);

      return () => {
        cancelled = true;
        hls.destroy();
        if (hlsRef.current === hls) hlsRef.current = null;
      };
    }

    const onLoadedMetadata = () => {
      applyPostLoadSetup();
    };
    const onCanPlayThrough = () => {
      const resumeTarget =
        resumeMsRef.current > 0
          ? resumeMsRef.current
          : !hasResumedPlaybackRef.current
            ? initialResumeMsRef.current
            : 0;
      if (resumeTarget > 0 && videoEl.currentTime < 1) {
        try {
          videoEl.currentTime = resumeTarget / 1000;
        } catch {}
      }
    };
    const onNativeError = () => {
      if (!cancelled) handleSourcePlaybackFailure();
    };

    videoEl.addEventListener('loadedmetadata', onLoadedMetadata);
    videoEl.addEventListener('canplay', onCanPlayThrough);
    videoEl.addEventListener('error', onNativeError);
    videoEl.src = playbackUrl;
    videoEl.load();

    return () => {
      cancelled = true;
      videoEl.removeEventListener('loadedmetadata', onLoadedMetadata);
      videoEl.removeEventListener('canplay', onCanPlayThrough);
      videoEl.removeEventListener('error', onNativeError);
      videoEl.pause();
      videoEl.removeAttribute('src');
      videoEl.load();
    };
  }, [activeSource, useProxyForSource, handleSourcePlaybackFailure]);

  // Apply playbackSpeed live
  useEffect(() => {
    if (videoRef.current) {
      videoRef.current.playbackRate = playbackSpeed;
    }
  }, [playbackSpeed]);

  // Persist progress helper
  const persistCurrentProgress = useCallback(() => {
    const base = baseItemRef.current;
    const pos = positionRef.current;
    const dur = durationRef.current;
    const pendingResume =
      resumeMsRef.current > 0
        ? resumeMsRef.current
        : !hasResumedPlaybackRef.current
          ? initialResumeMsRef.current
          : 0;

    // Never overwrite a valid saved progress if position is still 0 or resume is pending
    if (pendingResume > 5000 && pos < 5000 && !hasResumedPlaybackRef.current) {
      return;
    }

    if (base && dur > 0 && pos > 5000) {
      savePlaybackProgress(base, pos, dur);
    }
  }, []);

  const navigateNextEpisode = useCallback(() => {
    const nextUrl = detailedMedia?.baseItem?.nextEpisodeUrl;
    if (!nextUrl) return;
    if (baseItemRef.current) {
      markAsWatched(baseItemRef.current);
    }
    onNavigate?.({ name: 'player', url: nextUrl, replace: true });
  }, [detailedMedia, onNavigate]);

  // Video DOM event bindings (play/pause/timeupdate/waiting/playing/ended)
  useEffect(() => {
    const videoEl = videoRef.current;
    if (!videoEl || isEmbed) return undefined;

    const onPlay = () => {
      isPlayingRef.current = true;
      setIsPlaying(true);
    };
    const onPause = () => {
      isPlayingRef.current = false;
      setIsPlaying(false);
      persistCurrentProgress();
    };
    const onWaiting = () => {
      setIsBuffering(true);
    };
    const onPlaying = () => {
      setIsBuffering(false);
      setSeekTargetMs(null);
    };
    const onCanPlay = () => {
      setIsBuffering(false);
    };
    const onDurationChange = () => {
      if (Number.isFinite(videoEl.duration) && videoEl.duration > 0) {
        const ms = Math.round(videoEl.duration * 1000);

        const expectedRuntimeMin = detailedMedia?.metaInfo?.runtimeMinutes;
        if (expectedRuntimeMin && expectedRuntimeMin > 0) {
          const expectedMs = expectedRuntimeMin * 60_000;
          const isTrailer = ms < 5 * 60_000 && expectedMs > 15 * 60_000;
          const diff = Math.abs(ms - expectedMs);
          const maxAllowedDiff = Math.max(15 * 60_000, expectedMs * 0.25);

          if (isTrailer || diff > maxAllowedDiff) {
            console.warn(
              `[Player] Duration mismatch (stream=${Math.round(ms / 60000)}m, expected=${expectedRuntimeMin}m). Failing over to next source.`,
            );
            if (activeSource?.url) {
              failedUrlsRef.current.add(activeSource.url);
            }
            const nextCandidate = alternativeSources.find((s) => !failedUrlsRef.current.has(s.url));
            if (nextCandidate) {
              selectSourceInternal(nextCandidate, true);
              return;
            }
          }
        }

        durationRef.current = ms;
        setDurationMs(ms);
      }
    };
    const onTimeUpdate = () => {
      if (Number.isFinite(videoEl.currentTime)) {
        const ms = Math.round(videoEl.currentTime * 1000);
        const pendingResume =
          resumeMsRef.current > 0
            ? resumeMsRef.current
            : !hasResumedPlaybackRef.current
              ? initialResumeMsRef.current
              : 0;

        // Ignore transient 0:00 timeupdate during initial seek/buffer so we don't clobber position
        if (pendingResume > 5000 && ms < 2000 && !hasResumedPlaybackRef.current) {
          return;
        }

        if (pendingResume > 0 && ms >= pendingResume - 3000) {
          hasResumedPlaybackRef.current = true;
          resumeMsRef.current = 0;
        } else if (ms > 5000) {
          hasResumedPlaybackRef.current = true;
        }

        positionRef.current = ms;
        setPositionMs(ms);
      }
    };
    const onEnded = () => {
      isPlayingRef.current = false;
      setIsPlaying(false);
      if (baseItemRef.current) {
        markAsWatched(baseItemRef.current);
      }
      if (detailedMedia?.baseItem?.nextEpisodeUrl && settings.autoPlayNext) {
        navigateNextEpisode();
      } else {
        handleExitPlayer();
      }
    };

    videoEl.addEventListener('play', onPlay);
    videoEl.addEventListener('pause', onPause);
    videoEl.addEventListener('waiting', onWaiting);
    videoEl.addEventListener('playing', onPlaying);
    videoEl.addEventListener('canplay', onCanPlay);
    videoEl.addEventListener('durationchange', onDurationChange);
    videoEl.addEventListener('timeupdate', onTimeUpdate);
    videoEl.addEventListener('ended', onEnded);

    return () => {
      videoEl.removeEventListener('play', onPlay);
      videoEl.removeEventListener('pause', onPause);
      videoEl.removeEventListener('waiting', onWaiting);
      videoEl.removeEventListener('playing', onPlaying);
      videoEl.removeEventListener('canplay', onCanPlay);
      videoEl.removeEventListener('durationchange', onDurationChange);
      videoEl.removeEventListener('timeupdate', onTimeUpdate);
      videoEl.removeEventListener('ended', onEnded);
    };
  }, [isEmbed, detailedMedia, settings.autoPlayNext, persistCurrentProgress, navigateNextEpisode]);

  // Save progress every 30s while playing + on unmount
  useEffect(() => {
    const timer = setInterval(() => {
      if (isPlayingRef.current) persistCurrentProgress();
    }, PROGRESS_SAVE_INTERVAL_MS);
    return () => {
      clearInterval(timer);
      persistCurrentProgress();
      triggerSync().catch(() => {});
    };
  }, [persistCurrentProgress]);

  // 5-second buffering escape prompt timer
  useEffect(() => {
    if (!isBuffering || alternativeSources.length <= 1 || isEmbed || fatalError) {
      setShowBufferingPrompt(false);
      return undefined;
    }
    const timer = setTimeout(() => {
      setShowBufferingPrompt(true);
    }, BUFFERING_PROMPT_DELAY_MS);
    return () => clearTimeout(timer);
  }, [isBuffering, alternativeSources.length, isEmbed, fatalError]);

  // Initial subtitle selection & Polish subtitle auto-fetch when Lektor is enabled
  const sourceSubtitles = activeSource?.subtitles || [];
  const allSubtitles = useMemo(
    () => [...sourceSubtitles, ...downloadedSubtitles],
    [sourceSubtitles, downloadedSubtitles],
  );

  const ensurePolishLektorSubtitle = useCallback(
    async (explicitSource = activeSource) => {
      if (!explicitSource || isEmbedUrl(explicitSource.url)) return;
      const available = [...(explicitSource.subtitles || []), ...downloadedSubtitles];
      const existingPolish = available.find((s) => isPolishSubtitle(s));
      if (existingPolish) {
        setSelectedSubtitleUrl(existingPolish.url);
        return;
      }

      const sourceKey = `${url}::${explicitSource.url}`;
      if (autoLektorSourceKeyRef.current === sourceKey) return;
      autoLektorSourceKeyRef.current = sourceKey;

      const res = await findBestPolishSubtitle({
        detailedMedia,
        videoUrl: explicitSource.url,
        videoHeaders: explicitSource.headers,
        durationMs: durationRef.current,
        serverName: explicitSource.serverName,
        quality: explicitSource.quality,
        filename: explicitSource.filename,
        preferredGroup: tvShowSettings?.preferredSubtitleGroup,
        preferredSource: tvShowSettings?.preferredRipSource,
      });

      if (res.found && res.subtitle) {
        setDownloadedSubtitles((prev) =>
          prev.some((s) => s.url === res.subtitle.url) ? prev : [...prev, res.subtitle],
        );
        setSelectedSubtitleUrl(res.subtitle.url);
      } else {
        onToast?.(
          res.searchFailed
            ? t('player_subtitle_search_failed')
            : t('player_lektor_subtitles_not_found'),
        );
      }
    },
    [activeSource, downloadedSubtitles, url, detailedMedia, tvShowSettings, onToast],
  );

  useEffect(() => {
    if (!activeSource || isEmbed) return;
    const sourceKey = activeSource.url;
    const baseItem = detailedMedia?.baseItem;
    const countries = detailedMedia?.metaInfo?.countries || [];

    const isPolishProduction = isOriginallyPolish({
      originalLanguage: baseItem?.originalLanguage,
      countries,
      sourceVersion: activeSource.version,
      sourceFilename: activeSource.filename,
      allSources: alternativeSources,
    });
    const category = classifyContent(
      {
        genreIds: baseItem?.genreIds || [],
        originalLanguage: baseItem?.originalLanguage || '',
        countries,
      },
      isKids,
    );
    const effectiveCategory = isPolishProduction ? ContentCategory.NATIVE_DOMESTIC : category;
    const contentRule = settings.contentRules?.[effectiveCategory] || DEFAULT_CONTENT_RULES[effectiveCategory];

    const hasPolishSpoken = hasPolishSpokenAudio(
      isPolishProduction,
      activeSource.version,
      activeSource.filename,
    );
    const shouldEnableLektor = resolveShouldEnableLektor({
      isPolishProduction,
      hasPolishSpokenAudio: hasPolishSpoken,
      contentRule,
      isLektorSettingEnabled: Boolean(settings.lektorEnabled),
    });
    setIsLektorActive(shouldEnableLektor);

    if (initialSubtitlePickedRef.current !== sourceKey) {
      initialSubtitlePickedRef.current = sourceKey;

      const subtitlesDefault = resolveSubtitlesEnabled({
        isPolishProduction,
        hasPolishSpokenAudio: hasPolishSpoken,
        contentRule,
        shouldEnableLektor,
        savedSubtitlesEnabled: tvShowSettings?.subtitlesEnabled,
      });

      const targetPrefs =
        tvShowSettings ||
        (shouldEnableLektor || subtitlesDefault
          ? { subtitlesEnabled: true, subtitleLanguage: 'pl', subtitleLabel: null }
          : null);
      if (subtitlesDefault) {
        const matched = findMatchingSubtitle(allSubtitles, targetPrefs);
        if (matched) {
          setSelectedSubtitleUrl(matched);
          return;
        }
      } else {
        setSelectedSubtitleUrl(null);
      }
    }

    if (shouldEnableLektor && detailedMedia && !userDisabledSubtitlesRef.current) {
      const selectedObj = allSubtitles.find((s) => s.url === selectedSubtitleUrl);
      if (!selectedObj || !isPolishSubtitle(selectedObj)) {
        ensurePolishLektorSubtitle(activeSource);
      }
    }
  }, [
    activeSource,
    isEmbed,
    isKids,
    tvShowSettings,
    settings.lektorEnabled,
    settings.contentRules,
    detailedMedia,
    allSubtitles,
    selectedSubtitleUrl,
    alternativeSources,
    ensurePolishLektorSubtitle,
  ]);

  // Fetch & parse selected subtitle whenever selectedSubtitleUrl changes
  useEffect(() => {
    if (!selectedSubtitleUrl) {
      parsedCuesRef.current = [];
      subtitleTimelineRef.current = null;
      lastSubtitleTextRef.current = '';
      setActiveSubtitleText('');
      lektorRef.current?.clearSubtitles();
      return undefined;
    }

    let cancelled = false;
    const isDownloaded = downloadedSubtitles.some((s) => s.url === selectedSubtitleUrl);

    fetchRawSubtitleContent({
      url: selectedSubtitleUrl,
      isDownloaded,
      videoHeaders: activeSource?.headers || {},
    }).then((res) => {
      if (cancelled) return;
      if (!res.ok) {
        parsedCuesRef.current = [];
        subtitleTimelineRef.current = null;
        lektorRef.current?.clearSubtitles();
        onToast?.(
          res.notifyDownloadError
            ? t('player_subtitle_download_failed')
            : t('player_subtitle_unreadable'),
        );
        return;
      }

      const warning = diagnoseSubtitle(res.body, durationRef.current);
      if (warning === SubtitleWarning.UNREADABLE) {
        onToast?.(t('player_subtitle_unreadable'));
      } else if (warning === SubtitleWarning.POSSIBLY_INCOMPLETE) {
        onToast?.(t('player_subtitle_incomplete'));
      }

      const cues = parseSrtOrVtt(res.body);
      parsedCuesRef.current = cues;
      subtitleTimelineRef.current = createSubtitleTimeline(
        cues,
        subtitleOffsetRef.current,
        subtitleFpsRef.current,
      );
      lektorRef.current?.setSubtitles(res.body);

      if (videoRef.current && Number.isFinite(videoRef.current.currentTime)) {
        const rawMs = Math.round(videoRef.current.currentTime * 1000);
        const scale = Math.max(0.001, subtitleFpsRef.current || 1.0);
        const adj = Math.max(0, Math.trunc((rawMs - subtitleOffsetRef.current) / scale));
        lektorRef.current?.onSeek(adj);
      }

      if (subtitleOffsetRef.current === 0) {
        handleAutoAlign({ silent: true });
      }
    });

    return () => {
      cancelled = true;
    };
  }, [selectedSubtitleUrl, downloadedSubtitles, activeSource?.headers, onToast]);

  // Rebuild SubtitleTimeline whenever offset or FPS changes
  useEffect(() => {
    if (parsedCuesRef.current.length > 0) {
      subtitleTimelineRef.current = createSubtitleTimeline(
        parsedCuesRef.current,
        subtitleOffsetMs,
        subtitleFpsScale,
      );
    }
    if (lektorRef.current && videoRef.current && Number.isFinite(videoRef.current.currentTime)) {
      const rawMs = Math.round(videoRef.current.currentTime * 1000);
      const scale = Math.max(0.001, subtitleFpsScale || 1.0);
      const adj = Math.max(0, Math.trunc((rawMs - subtitleOffsetMs) / scale));
      lektorRef.current.onSeek(adj);
    }
  }, [subtitleOffsetMs, subtitleFpsScale]);

  // 50ms cue renderer + 100ms Lektor heartbeat loop
  useEffect(() => {
    if (isEmbed) return undefined;
    let tickCount = 0;

    const timer = setInterval(() => {
      const videoEl = videoRef.current;
      const rawMs =
        videoEl && Number.isFinite(videoEl.currentTime)
          ? Math.round(videoEl.currentTime * 1000)
          : positionRef.current;

      const hideVisual = Boolean(settings.lektorEnabled && settings.lektorHideSubtitles);
      if (selectedSubtitleUrl && !hideVisual && subtitleTimelineRef.current) {
        const cuesNow = activeCuesAt(subtitleTimelineRef.current, rawMs);
        const nextText = cuesNow
          .map((c) => cleanDisplayCueText(c.text))
          .filter(Boolean)
          .join('\n');
        if (nextText !== lastSubtitleTextRef.current) {
          lastSubtitleTextRef.current = nextText;
          setActiveSubtitleText(nextText);
        }
      } else if (lastSubtitleTextRef.current !== '') {
        lastSubtitleTextRef.current = '';
        setActiveSubtitleText('');
      }

      tickCount++;
      if (tickCount % 2 === 0 && lektorRef.current) {
        const scale = Math.max(0.001, subtitleFpsRef.current || 1.0);
        const adj = Math.max(0, Math.trunc((rawMs - subtitleOffsetRef.current) / scale));
        const playing = videoEl ? !videoEl.paused && !videoEl.ended : false;
        lektorRef.current.updatePlaybackState(adj, playing, isBufferingRef.current);
      }
    }, 50);

    return () => clearInterval(timer);
  }, [isEmbed, selectedSubtitleUrl, settings.lektorEnabled, settings.lektorHideSubtitles]);

  // 3-minute next-episode prefetch + 2-phase Next Episode appearance state
  const nextEpisodeUrl = detailedMedia?.baseItem?.nextEpisodeUrl || null;

  useEffect(() => {
    if (!nextEpisodeUrl || durationMs <= 0 || prefetchedNextRef.current) return;
    const remainingMs = Math.max(0, durationMs - positionMs);
    if (remainingMs <= PREFETCH_THRESHOLD_MS) {
      prefetchedNextRef.current = true;
      prefetchStreamsForMediaUrl(nextEpisodeUrl);
    }
  }, [nextEpisodeUrl, durationMs, positionMs]);

  const nextEpisodePhase = useMemo(() => {
    if (!nextEpisodeUrl || durationMs <= 10_000) {
      return NextEpisodeAppearance.HIDE;
    }
    const timeline = subtitleTimelineRef.current;
    const detectedCreditsStart = timeline ? detectDialogueEndMs(timeline, durationMs) : null;
    const isSpeaking = timeline ? hasSpokenDialogueAt(timeline, positionMs) : false;

    if (isSpeaking) {
      return NextEpisodeAppearance.HIDE;
    }

    const remainingMs = Math.max(0, durationMs - positionMs);
    const remainingSec = remainingMs / 1000;
    const remainingPct = (remainingMs / durationMs) * 100;

    let isSecondary = false;
    let isInitial = false;

    if (detectedCreditsStart != null) {
      const standardSecondaryMs =
        durationMs -
        Math.min(
          settings.secondaryAppearanceOffset * 1000,
          durationMs * (settings.secondaryAppearancePercentage / 100)
        );
      const secondaryThreshold = Math.min(
        durationMs - 2000,
        Math.max(detectedCreditsStart + 5000, standardSecondaryMs)
      );
      isSecondary = positionMs >= secondaryThreshold;
      isInitial = positionMs >= detectedCreditsStart;
    } else {
      isSecondary =
        remainingSec <= settings.secondaryAppearanceOffset ||
        remainingPct <= settings.secondaryAppearancePercentage;
      isInitial =
        remainingSec <= settings.initialAppearanceOffset ||
        remainingPct <= settings.initialAppearancePercentage;
    }

    if (isSecondary) {
      return getEffectiveSecondaryAppearanceType(settings);
    }
    if (isInitial) {
      return settings.initialAppearanceType;
    }
    return NextEpisodeAppearance.HIDE;
  }, [nextEpisodeUrl, durationMs, positionMs, settings]);

  const movieRecommendations = useMemo(() => {
    if (nextEpisodeUrl || !detailedMedia?.recommendations?.length) return [];
    return detailedMedia.recommendations.filter((rec) => Boolean(rec.posterUrl || rec.posterPath));
  }, [nextEpisodeUrl, detailedMedia]);

  const [movieCreditsDismissed, setMovieCreditsDismissed] = useState(false);
  const firstRecCardRef = useRef(null);

  const isMovieCredits = useMemo(() => {
    if (nextEpisodeUrl || movieCreditsDismissed || durationMs <= 10_000 || movieRecommendations.length === 0) {
      return false;
    }
    const timeline = subtitleTimelineRef.current;
    const detectedCreditsStart = timeline ? detectDialogueEndMs(timeline, durationMs) : null;
    const isSpeaking = timeline ? hasSpokenDialogueAt(timeline, positionMs) : false;
    if (isSpeaking) return false;

    if (detectedCreditsStart != null) {
      return positionMs >= detectedCreditsStart;
    }
    const remainingMs = Math.max(0, durationMs - positionMs);
    return remainingMs <= 120_000 || (remainingMs / durationMs) <= 0.04;
  }, [nextEpisodeUrl, movieCreditsDismissed, durationMs, positionMs, movieRecommendations.length]);

  useEffect(() => {
    if (isMovieCredits) {
      const timer = setTimeout(() => {
        firstRecCardRef.current?.focus();
      }, 50);
      return () => clearTimeout(timer);
    }
  }, [isMovieCredits]);

  useEffect(() => {
    if (
      nextEpisodePhase !== NextEpisodeAppearance.SHOW_WITH_TIMER ||
      !isPlaying ||
      isBuffering ||
      nextEpisodeCancelled
    ) {
      setNextEpisodeTimerProgress(null);
      return undefined;
    }

    const totalMs = Math.max(1000, (settings.secondaryTimerAmount || 10) * 1000);
    const startedAt = Date.now();
    setNextEpisodeTimerProgress(0);

    const timer = setInterval(() => {
      const elapsed = Date.now() - startedAt;
      const frac = Math.min(1, elapsed / totalMs);
      setNextEpisodeTimerProgress(frac);
      if (frac >= 1) {
        clearInterval(timer);
        navigateNextEpisode();
      }
    }, 50);

    return () => clearInterval(timer);
  }, [
    nextEpisodePhase,
    isPlaying,
    isBuffering,
    nextEpisodeCancelled,
    settings.secondaryTimerAmount,
    navigateNextEpisode,
  ]);

  // HUD 5-second auto-hide timer
  const resetHudTimer = useCallback(() => {
    clearTimeout(hudTimerRef.current);
    if (isPlaying && !isMenuOpen && !isScrubbing && !fatalError) {
      hudTimerRef.current = setTimeout(() => {
        setHudVisible(false);
      }, HUD_HIDE_DELAY_MS);
    }
  }, [isPlaying, isMenuOpen, isScrubbing, fatalError]);

  useEffect(() => {
    resetHudTimer();
    return () => clearTimeout(hudTimerRef.current);
  }, [hudVisible, resetHudTimer]);

  const revealHud = useCallback(() => {
    setHudVisible(true);
    clearTimeout(hudTimerRef.current);
    if (isPlayingRef.current && !isMenuOpen && !isScrubbing) {
      hudTimerRef.current = setTimeout(() => {
        setHudVisible(false);
      }, HUD_HIDE_DELAY_MS);
    }
  }, [isMenuOpen, isScrubbing]);

  const commitSeekMs = useCallback(
    (targetMs) => {
      lektorRef.current?.unlockAudio();
      const dur = durationRef.current;
      const clamped = dur > 0 ? Math.max(0, Math.min(dur - 500, targetMs)) : Math.max(0, targetMs);
      setSeekTargetMs(clamped);
      setIsScrubbing(false);

      if (videoRef.current) {
        videoRef.current.currentTime = clamped / 1000;
      }
      positionRef.current = clamped;
      setPositionMs(clamped);

      const scale = Math.max(0.001, subtitleFpsRef.current || 1.0);
      const adj = Math.max(0, Math.trunc((clamped - subtitleOffsetRef.current) / scale));
      lektorRef.current?.onSeek(adj);
      revealHud();
    },
    [revealHud],
  );

  const cancelQuickSeek = useCallback(() => {
    clearTimeout(quickSeekTimerRef.current);
    quickSeekCountRef.current = 0;
    quickSeekDeltaRef.current = 0;
    setQuickSeekDeltaMs(null);
  }, []);

  const togglePlayPause = useCallback(() => {
    lektorRef.current?.unlockAudio();
    const v = videoRef.current;
    if (!v || isEmbedRef.current) return;
    if (v.paused || v.ended) {
      v.play().catch(() => {});
    } else {
      v.pause();
    }
    revealHud();
  }, [revealHud]);

  const exitBrowserFullscreen = useCallback(() => {
    if (typeof document === 'undefined') return;
    const activeEl = document.fullscreenElement || document.webkitFullscreenElement;
    if (!activeEl) return;
    (document.exitFullscreen || document.webkitExitFullscreen)?.call(document)?.catch?.(() => {});
  }, []);

  const toggleFullscreen = useCallback(() => {
    lektorRef.current?.unlockAudio();
    if (typeof document === 'undefined') return;
    const activeEl = document.fullscreenElement || document.webkitFullscreenElement;
    if (activeEl) {
      exitBrowserFullscreen();
      return;
    }
    const target = playerRootRef.current;
    const req = target?.requestFullscreen || target?.webkitRequestFullscreen;
    if (req) {
      Promise.resolve(req.call(target)).catch(() => {
        videoRef.current?.webkitEnterFullscreen?.();
      });
    } else if (videoRef.current?.webkitEnterFullscreen) {
      videoRef.current.webkitEnterFullscreen();
    }
  }, [exitBrowserFullscreen]);

  useEffect(() => {
    if (typeof document === 'undefined') return undefined;
    const syncFullscreen = () => {
      const activeEl = document.fullscreenElement || document.webkitFullscreenElement;
      setIsFullscreen(Boolean(activeEl));
    };
    document.addEventListener('fullscreenchange', syncFullscreen);
    document.addEventListener('webkitfullscreenchange', syncFullscreen);
    return () => {
      document.removeEventListener('fullscreenchange', syncFullscreen);
      document.removeEventListener('webkitfullscreenchange', syncFullscreen);
      clearTimeout(clickTimerRef.current);
      const activeEl = document.fullscreenElement || document.webkitFullscreenElement;
      if (activeEl && activeEl === playerRootRef.current) {
        exitBrowserFullscreen();
      }
    };
  }, [exitBrowserFullscreen]);

  const handleExitPlayer = useCallback(
    (e) => {
      if (e && typeof e.stopPropagation === 'function') {
        e.stopPropagation();
      }
      if (e && typeof e.preventDefault === 'function') {
        e.preventDefault();
      }
      exitBrowserFullscreen();
      persistCurrentProgress();
      onBack?.();
    },
    [exitBrowserFullscreen, persistCurrentProgress, onBack],
  );

  // Keyboard / D-pad / Remote controls + Back precedence + Quick-Seek
  useEffect(() => {
    const onKeyDown = (e) => {
      if (isMenuOpen) return;

      if (e.key === 'Escape' || e.key === 'Backspace') {
        e.preventDefault();
        e.stopPropagation();
        if (e.key === 'Escape' && (document.fullscreenElement || document.webkitFullscreenElement)) {
          exitBrowserFullscreen();
          return;
        }
        if (quickSeekDeltaRef.current !== 0) {
          cancelQuickSeek();
          return;
        }
        if (isMovieCredits) {
          setMovieCreditsDismissed(true);
          return;
        }
        if (nextEpisodeTimerProgress !== null) {
          setNextEpisodeCancelled(true);
          return;
        }
        if (hudVisible && !fatalError && !(streamsSettled && alternativeSources.length === 0)) {
          setHudVisible(false);
          return;
        }
        handleExitPlayer();
        return;
      }

      if ((e.key === 'f' || e.key === 'F') && !e.metaKey && !e.ctrlKey && !e.altKey && !fatalError) {
        e.preventDefault();
        e.stopPropagation();
        toggleFullscreen();
        return;
      }

      if (isEmbedRef.current || fatalError) return;

      if (e.key === ' ' || e.key === 'MediaPlayPause') {
        if (document.activeElement?.getAttribute?.('data-seekbar-capture') === 'true' && hudVisible) {
          return;
        }
        e.preventDefault();
        e.stopPropagation();
        togglePlayPause();
        return;
      }

      if (!hudVisible) {
        if (e.key === 'Enter') {
          e.preventDefault();
          e.stopPropagation();
          togglePlayPause();
          return;
        }
        if (e.key === 'ArrowLeft' || e.key === 'ArrowRight') {
          if (durationRef.current <= 0) return;
          e.preventDefault();
          e.stopPropagation();

          if (quickSeekDeltaRef.current === 0) {
            quickSeekBaseRef.current = positionRef.current;
          }
          quickSeekCountRef.current += 1;
          const count = quickSeekCountRef.current;
          const step = count <= 2 ? 10_000 : count <= 4 ? 20_000 : 30_000;
          const dir = e.key === 'ArrowRight' ? 1 : -1;
          const nextDelta = quickSeekDeltaRef.current + dir * step;
          const clampedTarget = Math.max(
            0,
            Math.min(durationRef.current - 500, quickSeekBaseRef.current + nextDelta),
          );
          const effectiveDelta = clampedTarget - quickSeekBaseRef.current;

          quickSeekDeltaRef.current = effectiveDelta;
          setQuickSeekDeltaMs(effectiveDelta);

          clearTimeout(quickSeekTimerRef.current);
          quickSeekTimerRef.current = setTimeout(() => {
            const target = quickSeekBaseRef.current + quickSeekDeltaRef.current;
            quickSeekCountRef.current = 0;
            quickSeekDeltaRef.current = 0;
            setQuickSeekDeltaMs(null);
            commitSeekMs(target);
          }, QUICK_SEEK_COMMIT_DELAY_MS);
          return;
        }
        if (e.key === 'ArrowUp' || e.key === 'ArrowDown') {
          e.preventDefault();
          e.stopPropagation();
          setHudVisible(true);
          setTimeout(() => {
            (seekBarRef.current || playBtnRef.current)?.focus();
          }, 30);
          return;
        }
      } else {
        resetHudTimer();
      }
    };

    window.addEventListener('keydown', onKeyDown, true);
    return () => {
      window.removeEventListener('keydown', onKeyDown, true);
      clearTimeout(quickSeekTimerRef.current);
    };
  }, [
    isMenuOpen,
    hudVisible,
    fatalError,
    streamsSettled,
    alternativeSources.length,
    nextEpisodeTimerProgress,
    cancelQuickSeek,
    exitBrowserFullscreen,
    handleExitPlayer,
    toggleFullscreen,
    togglePlayPause,
    commitSeekMs,
    resetHudTimer,
  ]);

  // Menu action handlers
  const handleSelectSubtitle = useCallback(
    (subUrl) => {
      autoAlignedKeyRef.current = null;
      lektorRef.current?.unlockAudio();
      userDisabledSubtitlesRef.current = !subUrl;
      parsedCuesRef.current = [];
      subtitleTimelineRef.current = null;
      lastSubtitleTextRef.current = '';
      setActiveSubtitleText('');
      setSubtitleOffsetMs(0);
      setSubtitleFpsScale(1.0);
      setSelectedSubtitleUrl(subUrl);
      if (tvShowKey) {
        const sub = allSubtitles.find((s) => s.url === subUrl);
        saveTvShowSourceSettings(tvShowKey, {
          subtitlesEnabled: Boolean(subUrl),
          subtitleLanguage: sub?.language || null,
          subtitleLabel: sub?.label || null,
          subtitleOffsetMs: 0,
        });
      }
    },
    [tvShowKey, allSubtitles],
  );

  const handleSearchSubtitles = useCallback(
    async (lang) => {
      if (!activeSource) return;
      setIsSearchingSubtitles(true);
      try {
        const { candidates } = await searchSubtitleCandidates({
          detailedMedia,
          language: lang,
          videoUrl: activeSource.url,
          videoHeaders: activeSource.headers,
          serverName: activeSource.serverName,
          quality: activeSource.quality,
          filename: activeSource.filename,
        });
        setSubtitleSearchResults(candidates);
        if (candidates.length === 0) {
          onToast?.(t('player_no_subtitles_found', lang.toUpperCase()));
        }
      } catch {
        onToast?.(t('player_subtitle_search_failed'));
      } finally {
        setIsSearchingSubtitles(false);
      }
    },
    [activeSource, detailedMedia, onToast],
  );

  const handleDownloadCandidate = useCallback(
    async (cand) => {
      try {
        const track = await downloadSubtitleCandidate(cand);
        if (!track) {
          onToast?.(t('player_subtitle_download_failed'));
          return;
        }
        setDownloadedSubtitles((prev) =>
          prev.some((s) => s.url === track.url) ? prev : [...prev, track],
        );
        handleSelectSubtitle(track.url);
      } catch {
        onToast?.(t('player_subtitle_download_failed'));
      }
    },
    [handleSelectSubtitle, onToast],
  );

  const updateOffset = useCallback(
    (nextOffset) => {
      setSubtitleOffsetMs(nextOffset);
      if (tvShowKey) {
        saveTvShowSourceSettings(tvShowKey, { subtitleOffsetMs: nextOffset });
      }
    },
    [tvShowKey],
  );

  const applyAlignmentResult = useCallback(
    (result) => {
      if (!result) {
        onToast?.(t('player_subtitle_alignment_failed'));
        return;
      }
      if (result.confidence < 0.15) {
        onToast?.(t('player_subtitle_alignment_low_confidence'));
        return;
      }
      updateOffset(result.bestOffsetMs);
      setSubtitleFpsScale(result.bestFpsRatio);
      onToast?.(
        t(
          'player_subtitle_aligned_success',
          (result.bestOffsetMs / 1000).toFixed(1),
          result.bestFpsRatio.toFixed(4),
        ),
      );
    },
    [updateOffset, onToast],
  );

  const handleAlignWithTrack = useCallback(
    async (refUrl) => {
      let targetCues = parsedCuesRef.current;
      if (!targetCues || targetCues.length < 5) {
        if (!selectedSubtitleUrl) {
          onToast?.(t('player_subtitles_off'));
          return;
        }
        const isDownloaded = downloadedSubtitles.some((s) => s.url === selectedSubtitleUrl);
        const curRes = await fetchRawSubtitleContent({
          url: selectedSubtitleUrl,
          isDownloaded,
          videoHeaders: activeSource?.headers || {},
        });
        if (curRes.ok && curRes.body) {
          targetCues = parseSrtOrVtt(curRes.body);
          parsedCuesRef.current = targetCues;
        }
      }
      if (!targetCues || targetCues.length < 5) {
        onToast?.(t('player_subtitle_alignment_failed'));
        return;
      }

      setIsAutoAligning(true);
      try {
        const isDownloaded = downloadedSubtitles.some((s) => s.url === refUrl);
        const raw = await fetchRawSubtitleContent({
          url: refUrl,
          isDownloaded,
          videoHeaders: activeSource?.headers || {},
        });
        if (!raw.ok) {
          onToast?.(t('player_subtitle_alignment_failed'));
          return;
        }
        const refCues = parseSrtOrVtt(raw.body);
        applyAlignmentResult(alignSubtitleTracks(refCues, targetCues));
      } finally {
        setIsAutoAligning(false);
      }
    },
    [selectedSubtitleUrl, downloadedSubtitles, activeSource?.headers, applyAlignmentResult, onToast],
  );

  const handleAutoAlign = useCallback(
    async (options = {}) => {
      const { silent = false } = options;
      if (!selectedSubtitleUrl) {
        if (!silent) onToast?.(t('player_subtitles_off'));
        return;
      }

      alignAbortControllerRef.current?.abort();
      const abortController = new AbortController();
      alignAbortControllerRef.current = abortController;

      const alignKey = `${activeSource?.url}_${selectedSubtitleUrl}`;
      if (silent && autoAlignedKeyRef.current === alignKey) {
        return;
      }

      let targetCues = parsedCuesRef.current;
      if (!targetCues || targetCues.length < 5) {
        const isDownloaded = downloadedSubtitles.some((s) => s.url === selectedSubtitleUrl);
        const curRes = await fetchRawSubtitleContent({
          url: selectedSubtitleUrl,
          isDownloaded,
          videoHeaders: activeSource?.headers || {},
        });
        if (abortController.signal.aborted) return;
        if (curRes.ok && curRes.body) {
          targetCues = parseSrtOrVtt(curRes.body);
          parsedCuesRef.current = targetCues;
        }
      }
      if (abortController.signal.aborted) return;
      if (!targetCues || targetCues.length < 5) {
        if (!silent) onToast?.(t('player_subtitle_alignment_failed'));
        return;
      }

      // 1. Try finding an alternative track in the video's stream subtitles (sourceSubtitles, NEVER from downloadedSubtitles)
      const streamAlternatives = sourceSubtitles.filter((s) => s.url !== selectedSubtitleUrl);
      const preferredRef = streamAlternatives.find((s) => {
        const l = String(s.language || '').toLowerCase();
        const lbl = String(s.label || '').toLowerCase();
        return l === 'en' || l === 'eng' || lbl.includes('ang') || lbl.includes('eng');
      }) || streamAlternatives[0];

      if (preferredRef) {
        await handleAlignWithTrack(preferredRef.url);
        if (!abortController.signal.aborted) {
          autoAlignedKeyRef.current = alignKey;
        }
        return;
      }

      setIsAutoAligning(true);
      try {
        const currentSub =
          downloadedSubtitles.find((s) => s.url === selectedSubtitleUrl) ||
          sourceSubtitles.find((s) => s.url === selectedSubtitleUrl);
        const refBody = await findReferenceSubtitleForAlignment({
          detailedMedia,
          videoUrl: activeSource?.url,
          videoHeaders: activeSource?.headers,
          serverName: activeSource?.serverName,
          quality: activeSource?.quality,
          filename: activeSource?.filename,
          durationMs: durationRef.current || (detailedMedia?.metaInfo?.duration ? detailedMedia.metaInfo.duration : 0),
          currentTargetSubtitleLabel: currentSub?.label || null,
        });
        if (abortController.signal.aborted) return;
        if (!refBody) {
          if (!silent) onToast?.(t('player_subtitle_alignment_low_confidence'));
          return;
        }
        const refCues = parseSrtOrVtt(refBody);
        const alignResult = alignSubtitleTracks(refCues, targetCues);
        if (abortController.signal.aborted) return;
        if (alignResult) {
          autoAlignedKeyRef.current = alignKey;
          applyAlignmentResult(alignResult);
        } else if (!silent) {
          onToast?.(t('player_subtitle_alignment_low_confidence'));
        }
      } finally {
        if (!abortController.signal.aborted) {
          setIsAutoAligning(false);
        }
      }
    },
    [
      selectedSubtitleUrl,
      downloadedSubtitles,
      activeSource,
      sourceSubtitles,
      handleAlignWithTrack,
      detailedMedia,
      applyAlignmentResult,
      onToast,
    ],
  );

  const handleSelectAudioTrack = useCallback((trackId) => {
    if (hlsRef.current) {
      hlsRef.current.audioTrack = trackId;
      setSelectedAudioTrackId(trackId);
    }
  }, []);

  const activeAudioTrack = audioTracks.find((t) => t.id === selectedAudioTrackId) || audioTracks[0];
  const activeAudioLabel = (activeAudioTrack?.label || activeAudioTrack?.language || '').toLowerCase();
  const isDubbingTrack = activeAudioLabel.includes('dubbing');
  const isLektorTrack = activeAudioLabel.includes('lektor');

  let currentWatchMode = 'original';
  if (isDubbingTrack) {
    currentWatchMode = 'dubbing';
  } else if (isLektorActive || isLektorTrack) {
    currentWatchMode = 'lektor';
  } else if (selectedSubtitleUrl && !(settings.lektorEnabled && settings.lektorHideSubtitles)) {
    currentWatchMode = 'subtitles';
  } else {
    currentWatchMode = 'original';
  }

  const hasDubbing = audioTracks.some((t) => {
    const lbl = (t.label || t.language || '').toLowerCase();
    return lbl.includes('dubbing');
  });

  const handleSelectWatchMode = useCallback(
    (mode) => {
      if (mode === 'original') {
        if (isLektorActive) {
          updateSetting('lektorEnabled', false);
          setIsLektorActive(false);
          lektorRef.current?.clearSubtitles();
        }
        handleSelectSubtitle(null);
        if (audioTracks.length > 1) {
          const origTrack = audioTracks.find((t) => {
            const lbl = (t.label || t.language || '').toLowerCase();
            return !lbl.includes('dubbing') && !lbl.includes('lektor');
          });
          if (origTrack && origTrack.id !== selectedAudioTrackId) {
            handleSelectAudioTrack(origTrack.id);
          }
        }
      } else if (mode === 'subtitles') {
        if (isLektorActive) {
          updateSetting('lektorEnabled', false);
          setIsLektorActive(false);
          lektorRef.current?.clearSubtitles();
        }
        if (!selectedSubtitleUrl) {
          const plSub =
            allSubtitles.find((s) => (s.language || '').toLowerCase().startsWith('pl')) || allSubtitles[0];
          if (plSub) {
            handleSelectSubtitle(plSub.url);
          }
        }
        if (audioTracks.length > 1) {
          const origTrack = audioTracks.find((t) => {
            const lbl = (t.label || t.language || '').toLowerCase();
            return !lbl.includes('dubbing') && !lbl.includes('lektor');
          });
          if (origTrack && origTrack.id !== selectedAudioTrackId) {
            handleSelectAudioTrack(origTrack.id);
          }
        }
      } else if (mode === 'lektor') {
        const lektorTrack = audioTracks.find((t) =>
          (t.label || t.language || '').toLowerCase().includes('lektor'),
        );
        if (lektorTrack) {
          if (isLektorActive) {
            updateSetting('lektorEnabled', false);
            setIsLektorActive(false);
            lektorRef.current?.clearSubtitles();
          }
          handleSelectAudioTrack(lektorTrack.id);
        } else {
          updateSetting('lektorEnabled', true);
          setIsLektorActive(true);
          lektorRef.current?.unlockAudio();
          userDisabledSubtitlesRef.current = false;
          ensurePolishLektorSubtitle(activeSource);
        }
      } else if (mode === 'dubbing') {
        if (isLektorActive) {
          updateSetting('lektorEnabled', false);
          setIsLektorActive(false);
          lektorRef.current?.clearSubtitles();
        }
        handleSelectSubtitle(null);
        const dubTrack = audioTracks.find((t) =>
          (t.label || t.language || '').toLowerCase().includes('dubbing'),
        );
        if (dubTrack) {
          handleSelectAudioTrack(dubTrack.id);
        }
      }
    },
    [
      isLektorActive,
      handleSelectSubtitle,
      audioTracks,
      selectedAudioTrackId,
      handleSelectAudioTrack,
      selectedSubtitleUrl,
      allSubtitles,
      activeSource,
      ensurePolishLektorSubtitle,
    ],
  );

  const playerMenuData = useMemo(() => {
    if (!menuOpenTarget) return null;
    return buildPlayerMenu({
      alternativeSources,
      isLoadingSources: !streamsSettled,
      videoUrl: activeSource?.url,
      audioTracks,
      selectedAudioTrackId,
      sourceSubtitles,
      downloadedSubtitles,
      selectedSubtitleUrl,
      subtitleSearchResults,
      isSearchingSubtitles,
      subtitleOffsetMs,
      subtitleFpsScale,
      isAutoAligning,
      playbackSpeed,
      aspectRatioMode,
      settings,
      systemVoices,
      initialMenuId: menuOpenTarget === MENU_SOURCES_ID ? MENU_SOURCES_ID : null,
      isLoggedIn: Boolean(session?.isLoggedIn),
      connectedDevices,
      isLoadingDevices,
      onCastToDevice: handleCastToDevice,
      onSelectSource: (src) => {
        failedUrlsRef.current.delete(src.url);
        const baseItem = detailedMedia?.baseItem;
        const countries = detailedMedia?.metaInfo?.countries || [];
        const isPolishProd = isOriginallyPolish({
          originalLanguage: baseItem?.originalLanguage,
          countries,
          sourceVersion: src.version,
          sourceFilename: src.filename,
          allSources: alternativeSources,
        });
        const cat = classifyContent(
          {
            genreIds: baseItem?.genreIds || [],
            originalLanguage: baseItem?.originalLanguage || '',
            countries,
          },
          isKids,
        );
        const effCat = isPolishProd ? ContentCategory.NATIVE_DOMESTIC : cat;
        const rule = settings.contentRules?.[effCat] || DEFAULT_CONTENT_RULES[effCat];
        const hasPlSpoken = hasPolishSpokenAudio(isPolishProd, src.version, src.filename);
        const shouldEnableLek = resolveShouldEnableLektor({
          isPolishProduction: isPolishProd,
          hasPolishSpokenAudio: hasPlSpoken,
          contentRule: rule,
          isLektorSettingEnabled: Boolean(settings.lektorEnabled),
        });
        const subsDef = resolveSubtitlesEnabled({
          isPolishProduction: isPolishProd,
          hasPolishSpokenAudio: hasPlSpoken,
          contentRule: rule,
          shouldEnableLektor: shouldEnableLek,
          savedSubtitlesEnabled: null,
        });
        setIsLektorActive(shouldEnableLek);
        if (tvShowKey) {
          saveTvShowSourceSettings(tvShowKey, {
            serverName: src.serverName,
            version: src.version,
            quality: src.quality,
            sourceWebsite: src.sourceWebsite,
            subtitlesEnabled: subsDef,
          });
        }
        selectSourceInternal(src, true);
      },
      onSelectAudioTrack: handleSelectAudioTrack,
      onSelectSubtitle: handleSelectSubtitle,
      onSearchSubtitles: handleSearchSubtitles,
      onDownloadSubtitleCandidate: handleDownloadCandidate,
      onAutoAlign: handleAutoAlign,
      onAlignWithTrack: handleAlignWithTrack,
      onAdjustSubtitleOffset: (delta) => updateOffset(subtitleOffsetMs + delta),
      onSetSubtitleOffset: updateOffset,
      onSetSubtitleFpsScale: setSubtitleFpsScale,
      onToggleLektorEnabled: (nextEnabled) => {
        updateSetting('lektorEnabled', nextEnabled);
        setIsLektorActive(nextEnabled);
        lektorRef.current?.unlockAudio();
        if (nextEnabled) {
          userDisabledSubtitlesRef.current = false;
          ensurePolishLektorSubtitle(activeSource);
          if (subtitleOffsetRef.current === 0 && parsedCuesRef.current?.length >= 5) {
            handleAutoAlign({ silent: true });
          }
        } else {
          lektorRef.current?.clearSubtitles();
        }
      },
      onRefreshSystemVoices: refreshSystemVoices,
      onSetPlaybackSpeed: (spd) => {
        setPlaybackSpeed(spd);
        if (tvShowKey) saveTvShowSourceSettings(tvShowKey, { playbackSpeed: spd });
      },
      onSetAspectRatio: (mode) => {
        setAspectRatioMode(mode);
        if (tvShowKey) saveTvShowSourceSettings(tvShowKey, { aspectRatioMode: mode });
      },
      onClose: () => setMenuOpenTarget(null),
      onToast,
    });
  }, [
    menuOpenTarget,
    alternativeSources,
    streamsSettled,
    activeSource,
    audioTracks,
    selectedAudioTrackId,
    sourceSubtitles,
    downloadedSubtitles,
    selectedSubtitleUrl,
    subtitleSearchResults,
    isSearchingSubtitles,
    subtitleOffsetMs,
    subtitleFpsScale,
    isAutoAligning,
    playbackSpeed,
    aspectRatioMode,
    settings,
    systemVoices,
    session?.isLoggedIn,
    connectedDevices,
    isLoadingDevices,
    handleCastToDevice,
    tvShowKey,
    selectSourceInternal,
    handleSelectSubtitle,
    handleSearchSubtitles,
    handleDownloadCandidate,
    handleAutoAlign,
    handleAlignWithTrack,
    updateOffset,
    ensurePolishLektorSubtitle,
    refreshSystemVoices,
    onToast,
  ]);

  // Subtitle visual styles
  const subStyle = settings.subtitleStyle;
  const subEdgeShadow =
    subStyle.edgeType === EdgeType.OUTLINE
      ? '-1.5px -1.5px 0 #000, 1.5px -1.5px 0 #000, -1.5px 1.5px 0 #000, 1.5px 1.5px 0 #000'
      : subStyle.edgeType === EdgeType.SHADOW
        ? '2px 2px 6px rgba(0, 0, 0, 0.92)'
        : 'none';

  const noSourcesAtAll = streamsSettled && alternativeSources.length === 0 && !activeSource;

  if (fatalError || noSourcesAtAll) {
    return (
      <div className="player-root" data-hud-visible="true">
        <div className="player-top-controls">
          <TiwiIconButton
            icon={<IconBack size={20} />}
            tooltip={t('common_back')}
            tooltipPosition="below"
            variant="surface"
            focusZone="player"
            onClick={handleExitPlayer}
          />
        </div>
        <div
          style={{
            height: '100%',
            display: 'flex',
            flexDirection: 'column',
            alignItems: 'center',
            justifyContent: 'center',
            gap: 'var(--spacing-md)',
          }}
        >
          <ErrorSection
            message={
              fatalError || streamsMessage || t('stream_api_unavailable')
            }
            onRetry={handleRetryStreams}
          />
          {(alternativeSources.length > 0 || !streamsSettled) && (
            <TiwiButton
              text={t('player_video_source')}
              variant="secondary"
              focusZone="player"
              onClick={() => setMenuOpenTarget(MENU_SOURCES_ID)}
            />
          )}
        </div>
        <TiwiOverlayMenu
          menuData={playerMenuData}
          onClose={() => setMenuOpenTarget(null)}
        />
      </div>
    );
  }

  return (
    <div
      ref={playerRootRef}
      className="player-root"
      data-hud-visible={hudVisible ? 'true' : 'false'}
      onPointerMove={() => revealHud()}
      onClick={(e) => {
        lektorRef.current?.unlockAudio();
        if (e.target === videoRef.current || e.target === e.currentTarget) {
          clearTimeout(clickTimerRef.current);
          if (e.detail >= 2) return;
          const wasHudVisible = hudVisible;
          clickTimerRef.current = setTimeout(() => {
            if (!wasHudVisible) revealHud();
            else togglePlayPause();
          }, 220);
        }
      }}
      onDoubleClick={(e) => {
        if (e.target === videoRef.current || e.target === e.currentTarget) {
          clearTimeout(clickTimerRef.current);
          toggleFullscreen();
        }
      }}
    >
      <div className={`player-video-stage ${isMovieCredits ? 'player-video-stage--credits' : ''}`}>
        {isEmbed && activeSource ? (
          <iframe
            className="player-iframe"
            src={activeSource.url}
            title={detailedMedia?.baseItem?.titlePl || 'Player'}
            allow="autoplay; fullscreen; encrypted-media"
            allowFullScreen
          />
        ) : (
          <video
            ref={videoRef}
            className={getAspectClass(aspectRatioMode)}
            playsInline
            autoPlay
          />
        )}

        {!isEmbed && selectedSubtitleUrl && activeSubtitleText && !(settings.lektorEnabled && settings.lektorHideSubtitles) && (
          <div
            className="player-subtitles-layer"
            style={{
              bottom: `${((subStyle.verticalPaddingFraction ?? 0.05) * 100).toFixed(1)}%`,
            }}
          >
            <span
              className="player-subtitle-cue"
              style={{
                color: subStyle.textColorHex,
                backgroundColor: subStyle.backgroundColorHex,
                textShadow: subEdgeShadow,
                fontSize: `clamp(14px, ${(subStyle.fontSizeFraction * 100).toFixed(2)}vh, 64px)`,
              }}
            >
              {activeSubtitleText}
            </span>
          </div>
        )}

        <PlayerControls
          hudVisible={hudVisible}
          isEmbed={isEmbed}
          isPlaying={isPlaying}
          isFullscreen={isFullscreen}
          isBuffering={isBuffering}
          showBufferingPrompt={showBufferingPrompt}
          hasAlternativeSources={alternativeSources.length > 1}
          positionMs={positionMs}
          durationMs={durationMs}
          seekTargetMs={seekTargetMs}
          quickSeekDeltaMs={quickSeekDeltaMs}
          mediaItem={detailedMedia?.baseItem}
          activeSource={activeSource}
          nextEpisodeUrl={nextEpisodeUrl}
          nextEpisodeState={{
            appearance: nextEpisodePhase,
            timerProgress: nextEpisodeTimerProgress,
          }}
          watchMode={currentWatchMode}
          hasDubbing={hasDubbing}
          onSelectWatchMode={handleSelectWatchMode}
          playBtnRef={playBtnRef}
          seekBarRef={seekBarRef}
          onBackClick={handleExitPlayer}
          onTogglePlay={togglePlayPause}
          onScrubStart={() => {
            setIsScrubbing(true);
            clearTimeout(hudTimerRef.current);
          }}
          onCancelScrub={() => {
            setIsScrubbing(false);
            resetHudTimer();
          }}
          onSeekCommit={commitSeekMs}
          onOpenSettings={() => {
            lektorRef.current?.unlockAudio();
            setMenuOpenTarget('root');
          }}
          onOpenSourcesMenu={() => {
            lektorRef.current?.unlockAudio();
            setMenuOpenTarget(MENU_SOURCES_ID);
          }}
          onNextEpisodeClick={navigateNextEpisode}
          onToggleFullscreen={toggleFullscreen}
          isMovieCredits={isMovieCredits}
        />
      </div>

      <div
        className={`player-credits-recommendations-section ${
          isMovieCredits ? '' : 'player-credits-recommendations-section--hidden'
        }`}
        aria-hidden={!isMovieCredits}
      >
        <h3 className="player-credits-header">{t('common_continue_watching')}</h3>
        <div className="player-credits-recommendations-cards">
          {movieRecommendations.slice(0, 3).map((rec, idx) => {
            const title = rec.titlePl || rec.title || rec.titleEn || '';
            const ratingVal =
              rec.imdbRating?.rating ||
              rec.filmanRating?.rating ||
              (rec.voteAverage ? Number(rec.voteAverage).toFixed(1) : (rec.tiwiRating ? Number(rec.tiwiRating).toFixed(1) : null));
            const imageSrc = rec.backgroundUrl || rec.backdropUrl || rec.posterUrl || rec.posterPath || '';
            const desc = rec.description || rec.overview || '';

            return (
              <button
                key={rec.url || rec.id || idx}
                ref={idx === 0 ? firstRecCardRef : null}
                type="button"
                className="player-credits-card selectable-pulse"
                data-focusable="true"
                data-focus-zone="player"
                onClick={() => onNavigate?.({ name: 'details', url: rec.url, replace: true })}
              >
                {imageSrc && (
                  <img
                    src={imageSrc}
                    alt={title}
                    className="player-credits-card-image"
                    loading="lazy"
                  />
                )}
                <div className="player-credits-card-scrim" />
                <div className="player-credits-card-content">
                  <div className="player-credits-card-header">
                    <span className="player-credits-card-title">{title}</span>
                    <div className="player-credits-card-meta">
                      {rec.year && <span>{rec.year}</span>}
                      {ratingVal && (
                        <span className="player-credits-card-rating">
                          <IconStar size={12} />
                          <span>{ratingVal}</span>
                        </span>
                      )}
                    </div>
                  </div>
                  {desc && <p className="player-credits-card-desc">{desc}</p>}
                </div>
              </button>
            );
          })}
        </div>

        <button
          type="button"
          className="player-credits-roll-button selectable-pulse"
          data-focusable="true"
          data-focus-zone="player"
          onClick={() => setMovieCreditsDismissed(true)}
        >
          <IconClose size={16} />
          <span>{t('player_roll_credits')}</span>
        </button>
      </div>

      <TiwiOverlayMenu
        menuData={playerMenuData}
        onClose={() => setMenuOpenTarget(null)}
      />
    </div>
  );
}

export default function PlayerScreen(props) {
  const youtubeId = extractYoutubeVideoId(props.url);
  if (youtubeId) {
    return (
      <YoutubeTrailerPlayer
        youtubeId={youtubeId}
        title={props.title}
        description={props.description}
        isTrailer={props.isTrailer ?? true}
        onBack={props.onBack}
        onPlaybackChange={props.onPlaybackChange}
      />
    );
  }
  return <VideoPlayerCore key={props.url} {...props} />;
}
