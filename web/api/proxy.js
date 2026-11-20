import { handleProxyRequest } from '../server/proxyHandler.js';

export default async function handler(req, res) {
  return handleProxyRequest(req, res);
}
