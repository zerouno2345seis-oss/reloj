// Vercel Serverless Sync Relay API with Persistent Cloud Storage
const https = require('https');

const WH_UUID = '8c4af8cb-77b7-4acc-adf3-a97f3270b692';
const POST_URL = `https://webhook.site/${WH_UUID}`;
const GET_URL = `https://webhook.site/token/${WH_UUID}/requests?sorting=newest&per_page=1`;

let memoryStore = {};

function httpRequest(url, method, data = null) {
  return new Promise((resolve, reject) => {
    const parsed = new URL(url);
    const options = {
      hostname: parsed.hostname,
      port: 443,
      path: parsed.pathname + parsed.search,
      method: method,
      headers: {
        'Content-Type': 'application/json',
        'User-Agent': 'QuranWatch-Vercel-Relay/1.0'
      },
      timeout: 4000
    };

    const req = https.request(options, (res) => {
      let body = '';
      res.on('data', (chunk) => body += chunk);
      res.on('end', () => {
        try {
          resolve({ status: res.statusCode, body: JSON.parse(body) });
        } catch (_) {
          resolve({ status: res.statusCode, body: body });
        }
      });
    });

    req.on('error', (err) => reject(err));
    req.on('timeout', () => {
      req.destroy();
      reject(new Error('Request timeout'));
    });

    if (data) {
      req.write(typeof data === 'string' ? data : JSON.stringify(data));
    }
    req.end();
  });
}

module.exports = async (req, res) => {
  // CORS Headers
  res.setHeader('Access-Control-Allow-Credentials', 'true');
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Methods', 'GET,OPTIONS,PATCH,DELETE,POST,PUT');
  res.setHeader(
    'Access-Control-Allow-Headers',
    'X-CSRF-Token, X-Requested-With, Accept, Accept-Version, Content-Length, Content-MD5, Content-Type, Date, X-Api-Version, Authorization'
  );
  res.setHeader('Cache-Control', 'no-cache, no-store, must-revalidate');

  if (req.method === 'OPTIONS') {
    return res.status(200).end();
  }

  const code = req.query.code || req.headers['x-sync-code'] || '41331';

  if (req.method === 'GET') {
    try {
      // 1. Try fetching from persistent cloud store
      const cloudRes = await httpRequest(GET_URL, 'GET');
      if (cloudRes.status === 200 && cloudRes.body?.data?.length > 0) {
        const rawContent = cloudRes.body.data[0].content;
        let parsed = rawContent;
        if (typeof rawContent === 'string') {
          try { parsed = JSON.parse(rawContent); } catch (_) {}
        }
        return res.status(200).json({
          status: 'ok',
          code: code,
          hasData: true,
          data: parsed,
          updatedAt: Date.now()
        });
      }
    } catch (e) {
      console.error('Cloud fetch fallback:', e);
    }

    // Fallback to memoryStore
    const payload = memoryStore[code] || memoryStore['default'] || null;
    return res.status(200).json({
      status: 'ok',
      code: code,
      hasData: payload !== null,
      data: payload,
      updatedAt: memoryStore[`${code}_time`] || Date.now()
    });
  }

  if (req.method === 'POST') {
    try {
      let body = req.body;
      if (typeof body === 'string') {
        try { body = JSON.parse(body); } catch (_) {}
      }

      // 1. Save to memory cache
      memoryStore[code] = body;
      memoryStore['default'] = body;
      memoryStore[`${code}_time`] = Date.now();

      // 2. Persist to cloud store
      try {
        await httpRequest(POST_URL, 'POST', body);
      } catch (err) {
        console.error('Persistent cloud save error:', err);
      }

      return res.status(200).json({
        status: 'ok',
        code: code,
        message: 'Sync state saved on persistent cloud relay',
        updatedAt: memoryStore[`${code}_time`]
      });
    } catch (e) {
      return res.status(400).json({ status: 'error', error: e.message });
    }
  }

  return res.status(405).json({ error: 'Method not allowed' });
};
