import { defineConfig, loadEnv } from 'vite';
import react from '@vitejs/plugin-react';
import { loadServerConfig } from './server/env.js';
import { handleStreamRequest } from './server/streamHandler.js';
import { handleProxyRequest } from './server/proxyHandler.js';
import { handleSyncRequest } from './server/syncHandler.js';
import { handleSubtitlesRequest } from './server/subtitlesHandler.js';
import { handleTtsRequest } from './server/ttsHandler.js';

function attachApiMiddleware(middlewares, viteEnv) {
  middlewares.use('/api/stream', (req, res) => handleStreamRequest(req, res, viteEnv));
  middlewares.use('/api/proxy', (req, res) => handleProxyRequest(req, res));
  middlewares.use('/api/sync', (req, res) => handleSyncRequest(req, res, viteEnv));
  middlewares.use('/api/subtitles', (req, res) => handleSubtitlesRequest(req, res, viteEnv));
  middlewares.use('/api/tts', (req, res) => handleTtsRequest(req, res));
}

export default defineConfig(({ mode }) => {
  const viteEnv = loadEnv(mode, process.cwd(), '');
  const config = loadServerConfig(process.cwd(), viteEnv);

  return {
    envPrefix: ['VITE_'],
    define: {
      'import.meta.env.VITE_TMDB_API_KEY': JSON.stringify(config.tmdbApiKey),
      'import.meta.env.VITE_STREAM_API_CONFIGURED': JSON.stringify(config.streamApiBaseUrl ? 'true' : ''),
    },
    plugins: [
      react(),
      {
        name: 'tiwi-api-proxy',
        configureServer(server) {
          attachApiMiddleware(server.middlewares, viteEnv);
        },
        configurePreviewServer(server) {
          attachApiMiddleware(server.middlewares, viteEnv);
        },
      },
    ],
    server: {
      port: 3000,
    },
  };
});
