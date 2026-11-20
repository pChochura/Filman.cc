import { handleStreamRequest } from '../server/streamHandler.js';

export default async function handler(req, res) {
  return handleStreamRequest(req, res);
}
