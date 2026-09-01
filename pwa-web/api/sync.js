// Vercel Serverless Sync Relay API
// Provides HTTPS-compliant, zero-config real-time synchronization between Web Hub and Galaxy Watch

let memoryStore = {};

module.exports = async (req, res) => {
  // CORS Headers
  res.setHeader('Access-Control-Allow-Credentials', 'true');
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Methods', 'GET,OPTIONS,PATCH,DELETE,POST,PUT');
  res.setHeader(
    'Access-Control-Allow-Headers',
    'X-CSRF-Token, X-Requested-With, Accept, Accept-Version, Content-Length, Content-MD5, Content-Type, Date, X-Api-Version, Authorization'
  );

  if (req.method === 'OPTIONS') {
    return res.status(200).end();
  }

  const code = req.query.code || req.headers['x-sync-code'] || '41331';

  if (req.method === 'GET') {
    const payload = memoryStore[code] || null;
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
      memoryStore[code] = body;
      memoryStore[`${code}_time`] = Date.now();

      return res.status(200).json({
        status: 'ok',
        code: code,
        message: 'Sync state saved on cloud relay',
        updatedAt: memoryStore[`${code}_time`]
      });
    } catch (e) {
      return res.status(400).json({ status: 'error', error: e.message });
    }
  }

  return res.status(405).json({ error: 'Method not allowed' });
};
