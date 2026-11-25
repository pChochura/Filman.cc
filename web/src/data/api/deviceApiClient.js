import { sessionStore } from '../local/sessionStore.js';

export async function fetchConnectedDevices() {
  const session = sessionStore.get();
  if (!session?.authToken) {
    return [];
  }

  try {
    const res = await fetch('/api/devices', {
      headers: {
        Accept: 'application/json',
        Authorization: `Bearer ${session.authToken}`,
      },
    });

    if (!res.ok) {
      return [];
    }

    const data = await res.json();
    return Array.isArray(data?.devices) ? data.devices : [];
  } catch (err) {
    console.warn('[devices] Failed to fetch connected devices:', err);
    return [];
  }
}

export async function sendPlayCommandToDevice(deviceId, { url, episodeUrl, title, progressMs, autoPlay = true }) {
  const session = sessionStore.get();
  if (!session?.authToken) {
    throw new Error('Not authenticated');
  }

  const res = await fetch(`/api/devices/${encodeURIComponent(deviceId)}/play`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json; charset=utf-8',
      Authorization: `Bearer ${session.authToken}`,
    },
    body: JSON.stringify({
      url,
      episodeUrl: episodeUrl || null,
      title: title || null,
      progressMs: Math.round(Number(progressMs) || 0),
      autoPlay: Boolean(autoPlay),
    }),
  });

  const data = await res.json().catch(() => ({}));
  if (!res.ok || data.success === false) {
    throw new Error(data.message || `Failed to send command (HTTP ${res.status})`);
  }

  return data;
}
