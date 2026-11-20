import { handleTtsRequest } from '../server/ttsHandler.js';

export default async function handler(req, res) {
  return handleTtsRequest(req, res);
}
