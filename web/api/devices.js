import { handleDevicesRequest } from '../server/devicesHandler.js';

export default async function handler(req, res) {
  return handleDevicesRequest(req, res);
}
