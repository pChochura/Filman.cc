export default async function handler(req, res) {
  const streamApiBase =
    process.env.VITE_STREAM_API_BASE_URL ||
    process.env.STREAM_API_BASE_URL;

  const cleanBase = streamApiBase.replace(/\/+$/, '');
  const { path = '' } = req.query;

  const targetUrl = `${cleanBase}/api/streams/${decodeURIComponent(path)}`;

  try {
    const upstream = await fetch(targetUrl, {
      headers: {
        Accept: req.headers.accept || 'text/event-stream, application/json, */*',
      },
    });

    res.statusCode = upstream.status;
    upstream.headers.forEach((val, key) => {
      const lower = key.toLowerCase();
      if (
        lower !== 'transfer-encoding' &&
        lower !== 'content-encoding' &&
        lower !== 'content-length'
      ) {
        res.setHeader(key, val);
      }
    });

    res.setHeader('Access-Control-Allow-Origin', '*');
    res.setHeader('Access-Control-Allow-Methods', 'GET, HEAD, OPTIONS');

    if (req.method === 'OPTIONS') {
      res.end();
      return;
    }

    if (!upstream.body) {
      res.end();
      return;
    }

    const reader = upstream.body.getReader();
    while (true) {
      const { done, value } = await reader.read();
      if (done) break;
      res.write(value);
    }
    res.end();
  } catch (err) {
    console.error('Stream proxy error:', err);
    res.status(502).json({ error: err.message });
  }
}
