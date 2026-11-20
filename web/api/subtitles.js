import { handleSubtitlesRequest } from '../server/subtitlesHandler.js';

export default async function handler(req, res) {
  return handleSubtitlesRequest(req, res);
}
