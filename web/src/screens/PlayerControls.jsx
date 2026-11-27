import React from 'react';
import { t } from '../i18n/index.js';
import { NextEpisodeAppearance } from '../data/models/playerConstants.js';
import TiwiButton from '../components/TiwiButton.jsx';
import TiwiIconButton from '../components/TiwiIconButton.jsx';
import TiwiSeekBar from '../components/TiwiSeekBar.jsx';
import {
  IconBack,
  IconExitFullscreen,
  IconFullscreen,
  IconMoreHoriz,
  IconNext,
  IconPause,
  IconPlay,
} from '../components/Icons.jsx';

function formatQuickSeekDelta(deltaMs) {
  const sign = deltaMs >= 0 ? '+' : '-';
  const absSec = Math.round(Math.abs(deltaMs) / 1000);
  if (absSec < 60) return `${sign}${absSec}s`;
  const m = Math.floor(absSec / 60);
  const s = absSec % 60;
  return `${sign}${m}:${String(s).padStart(2, '0')}`;
}

export function NextEpisodeButton({
  timerProgress = null,
  onClick,
  focusZone = 'player',
}) {
  return (
    <button
      type="button"
      className="player-next-ep-btn selectable-pulse"
      data-focusable="true"
      data-focus-zone={focusZone}
      onClick={onClick}
    >
      {timerProgress !== null && (
        <span
          className="player-next-ep-btn__timer-fill"
          style={{ width: `${Math.max(0, Math.min(100, timerProgress * 100)).toFixed(1)}%` }}
        />
      )}
      <span className="player-next-ep-btn__label" style={{ display: 'inline-flex', alignItems: 'center', gap: '8px' }}>
        <IconNext size={18} />
        <span>{t('player_next_episode')}</span>
      </span>
    </button>
  );
}

export default function PlayerControls({
  hudVisible,
  isEmbed = false,
  isPlaying,
  isFullscreen = false,
  isBuffering,
  showBufferingPrompt,
  hasAlternativeSources,
  positionMs,
  durationMs,
  seekTargetMs,
  quickSeekDeltaMs,
  mediaItem,
  activeSource,
  nextEpisodeUrl,
  nextEpisodeState,
  watchMode = 'original',
  hasDubbing = false,
  onSelectWatchMode,
  playBtnRef,
  seekBarRef,
  onBackClick,
  onTogglePlay,
  onScrubStart,
  onCancelScrub,
  onSeekCommit,
  onOpenSettings,
  onOpenSourcesMenu,
  onNextEpisodeClick,
  onToggleFullscreen,
}) {
  const appearance = nextEpisodeState?.appearance || NextEpisodeAppearance.HIDE;
  const timerProgress = nextEpisodeState?.timerProgress ?? null;
  const showNextInOverlay =
    Boolean(nextEpisodeUrl) &&
    hudVisible &&
    (appearance === NextEpisodeAppearance.SHOW ||
      appearance === NextEpisodeAppearance.SHOW_IN_OVERLAY ||
      appearance === NextEpisodeAppearance.SHOW_WITH_TIMER);
  const showNextFloating =
    Boolean(nextEpisodeUrl) &&
    !hudVisible &&
    (appearance === NextEpisodeAppearance.SHOW ||
      appearance === NextEpisodeAppearance.SHOW_WITH_TIMER);

  const isTvShow = Boolean(mediaItem?.isTvShow);
  const mainTitle = isTvShow
    ? mediaItem?.seriesTitle || mediaItem?.titlePl || ''
    : mediaItem?.titlePl || '';
  const epLine =
    isTvShow && mediaItem?.seasonNumber != null && mediaItem?.episodeNumber != null
      ? `S${mediaItem.seasonNumber}E${mediaItem.episodeNumber}${
          mediaItem.episodeTitle ? ` • ${mediaItem.episodeTitle}` : ''
        }`
      : null;
  const sourceLabel = activeSource
    ? [activeSource.serverName, activeSource.version, activeSource.quality]
        .filter(Boolean)
        .join(' • ')
    : '';

  return (
    <>
      {!isEmbed && (
        <div
          className="player-hud-backdrop"
          style={{ opacity: hudVisible ? 1 : 0 }}
        />
      )}

      {(hudVisible || isEmbed) && (
        <div
          className="player-top-controls"
          style={
            isEmbed && !hudVisible
              ? { opacity: 0.72, transition: 'opacity 0.2s ease' }
              : undefined
          }
        >
          <TiwiIconButton
            icon={<IconBack size={20} />}
            tooltip={t('common_back')}
            tooltipPosition="below"
            variant="surface"
            focusZone="player"
            onClick={(e) => {
              e?.preventDefault?.();
              e?.stopPropagation?.();
              onBackClick?.(e);
            }}
          />
          {isEmbed && (
            <>
              <TiwiButton
                text={t('player_switch_source')}
                variant="secondary"
                focusZone="player"
                onClick={onOpenSourcesMenu}
              />
              <TiwiIconButton
                icon={isFullscreen ? <IconExitFullscreen size={20} /> : <IconFullscreen size={20} />}
                tooltip={isFullscreen ? t('player_exit_fullscreen') : t('player_fullscreen')}
                tooltipPosition="below"
                variant="surface"
                focusZone="player"
                onClick={onToggleFullscreen}
              />
            </>
          )}
        </div>
      )}

      {!isEmbed && quickSeekDeltaMs !== null && quickSeekDeltaMs !== 0 && !hudVisible && (
        <div className="player-quick-seek-pill type-headline-medium">
          {formatQuickSeekDelta(quickSeekDeltaMs)}
        </div>
      )}

      {!isEmbed && isBuffering && (
        <div className="player-buffering-center">
          <div className="spinner" />
          {showBufferingPrompt && hasAlternativeSources && (
            <div className="player-buffering-center__prompt">
              <span className="type-title-medium">{t('player_still_waiting')}</span>
              <TiwiButton
                text={t('player_video_source')}
                variant="secondary"
                focusZone="player"
                onClick={onOpenSourcesMenu}
              />
            </div>
          )}
        </div>
      )}

      {!isEmbed && showNextFloating && (
        <div
          style={{
            position: 'absolute',
            right: 'var(--spacing-xl)',
            bottom: 'var(--spacing-xl)',
            zIndex: 35,
          }}
        >
          <NextEpisodeButton
            timerProgress={timerProgress}
            onClick={onNextEpisodeClick}
          />
        </div>
      )}

      {!isEmbed && hudVisible && (
        <div className="player-bottom-controls">
          <div className="player-details-and-next">
            <div className="player-media-details">
              {mainTitle && <div className="type-title-large">{mainTitle}</div>}
              {epLine && (
                <div
                  className="type-body-medium"
                  style={{ color: 'var(--color-on-surface-variant)' }}
                >
                  {epLine}
                </div>
              )}
              {sourceLabel && (
                <div
                  className="type-body-small"
                  style={{ color: 'var(--color-on-surface-variant)' }}
                >
                  {sourceLabel}
                </div>
              )}
            </div>

            {showNextInOverlay && (
              <NextEpisodeButton
                timerProgress={timerProgress}
                onClick={onNextEpisodeClick}
              />
            )}
          </div>

          <div className="player-controls-row">
            <TiwiIconButton
              buttonRef={playBtnRef}
              icon={isPlaying ? <IconPause size={24} /> : <IconPlay size={24} />}
              tooltip={isPlaying ? t('player_pause') : t('player_play')}
              variant="transparent"
              focusZone="player"
              onClick={onTogglePlay}
            />

            <TiwiSeekBar
              progressMs={positionMs}
              durationMs={durationMs}
              seekTargetMs={seekTargetMs}
              isBuffering={isBuffering}
              seekBarRef={seekBarRef}
              onScrubStart={onScrubStart}
              onCancelScrub={onCancelScrub}
              onSeekCommit={onSeekCommit}
            />

            <TiwiIconButton
              icon={isFullscreen ? <IconExitFullscreen size={20} /> : <IconFullscreen size={20} />}
              tooltip={isFullscreen ? t('player_exit_fullscreen') : t('player_fullscreen')}
              variant="transparent"
              focusZone="player"
              onClick={onToggleFullscreen}
            />
          </div>

          <div className="player-watch-modes-row">
            <div className="tiwi-nav-pill">
              <button
                type="button"
                className={`tiwi-nav-item type-title-medium selectable-pulse ${
                  watchMode === 'original' ? 'is-selected' : ''
                }`}
                data-focusable="true"
                data-focus-zone="player"
                data-selected={watchMode === 'original' ? 'true' : 'false'}
                onClick={() => onSelectWatchMode?.('original')}
              >
                <span>{t('player_mode_original')}</span>
              </button>
              <button
                type="button"
                className={`tiwi-nav-item type-title-medium selectable-pulse ${
                  watchMode === 'subtitles' ? 'is-selected' : ''
                }`}
                data-focusable="true"
                data-focus-zone="player"
                data-selected={watchMode === 'subtitles' ? 'true' : 'false'}
                onClick={() => onSelectWatchMode?.('subtitles')}
              >
                <span>{t('player_mode_subtitles')}</span>
              </button>
              <button
                type="button"
                className={`tiwi-nav-item type-title-medium selectable-pulse ${
                  watchMode === 'lektor' ? 'is-selected' : ''
                }`}
                data-focusable="true"
                data-focus-zone="player"
                data-selected={watchMode === 'lektor' ? 'true' : 'false'}
                onClick={() => onSelectWatchMode?.('lektor')}
              >
                <span>{t('player_mode_lektor')}</span>
              </button>
              {hasDubbing && (
                <button
                  type="button"
                  className={`tiwi-nav-item type-title-medium selectable-pulse ${
                    watchMode === 'dubbing' ? 'is-selected' : ''
                  }`}
                  data-focusable="true"
                  data-focus-zone="player"
                  data-selected={watchMode === 'dubbing' ? 'true' : 'false'}
                  onClick={() => onSelectWatchMode?.('dubbing')}
                >
                  <span>{t('player_mode_dubbing')}</span>
                </button>
              )}
            </div>

            <div className="tiwi-nav-pill tiwi-nav-pill--settings">
              <button
                type="button"
                className="tiwi-nav-item tiwi-nav-item--icon selectable-pulse"
                data-focusable="true"
                data-focus-zone="player"
                aria-label={t('player_mode_more')}
                onClick={onOpenSettings}
              >
                <IconMoreHoriz size={18} />
              </button>
            </div>
          </div>
        </div>
      )}
    </>
  );
}
