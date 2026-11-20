import { handleSyncRequest } from '../server/syncHandler.js';

export default async function handler(req, res) {
  return handleSyncRequest(req, res);
}
