import { useCallback, useEffect, useState } from 'react';
import { t } from '../i18n/index.js';
import { useSession } from '../data/local/sessionStore.js';
import { fetchConnectedDevices, sendPlayCommandToDevice } from '../data/api/deviceApiClient.js';
import { resolvePlaybackTarget } from '../data/api/devicePlaybackTargetResolver.js';
import { buildProgressMap, progressStore } from '../data/local/progressStore.js';

export function useDeviceCasting({
  onToast,
  onCloseOverlay,
  detailedMedia = null,
  watchButtonUrl = null,
} = {}) {
  const session = useSession();
  const [connectedDevices, setConnectedDevices] = useState([]);
  const [isLoadingDevices, setIsLoadingDevices] = useState(false);

  const loadDevices = useCallback(async () => {
    if (!session?.isLoggedIn || !session?.authToken) {
      setConnectedDevices([]);
      return;
    }
    setIsLoadingDevices(true);
    try {
      const devices = await fetchConnectedDevices();
      setConnectedDevices(devices);
    } catch {
      setConnectedDevices([]);
    } finally {
      setIsLoadingDevices(false);
    }
  }, [session?.isLoggedIn, session?.authToken]);

  useEffect(() => {
    loadDevices();
  }, [loadDevices]);

  const handleCastToDevice = useCallback(
    async (targetDevice, targetMovie) => {
      try {
        const media = targetMovie || detailedMedia?.baseItem;
        if (!media) return;

        const effectiveWatchButtonUrl =
          media.url === detailedMedia?.baseItem?.url ? watchButtonUrl : null;

        const resolved = await resolvePlaybackTarget(media, {
          detailedMedia,
          progressItems: progressStore.get(),
          watchButtonUrl: effectiveWatchButtonUrl,
        });
        if (!resolved) return;

        await sendPlayCommandToDevice(targetDevice.deviceId, {
          url: resolved.url,
          episodeUrl: resolved.episodeUrl,
          title: resolved.title,
          progressMs: resolved.progressMs,
          autoPlay: true,
        });

        onToast?.(t('player_devices_sent_success', targetDevice.deviceName));
        onCloseOverlay?.();
      } catch (err) {
        onToast?.(t('player_devices_sent_error', err?.message || targetDevice.deviceName));
      }
    },
    [detailedMedia, onCloseOverlay, onToast, watchButtonUrl],
  );

  const buildContextMenuProps = useCallback(
    (movie, options, overrides = {}) => {
      const progressMap = overrides.progressMap || buildProgressMap();
      return {
        movie,
        options,
        progressMap,
        connectedDevices,
        isLoadingDevices,
        isLoggedIn: Boolean(session?.isLoggedIn),
        onCastToDevice: (device, targetMovie) => handleCastToDevice(device, targetMovie || movie),
        onClose: onCloseOverlay,
        ...overrides,
      };
    },
    [connectedDevices, isLoadingDevices, session?.isLoggedIn, handleCastToDevice, onCloseOverlay],
  );

  return {
    connectedDevices,
    isLoadingDevices,
    isLoggedIn: Boolean(session?.isLoggedIn),
    loadDevices,
    handleCastToDevice,
    buildContextMenuProps,
  };
}
