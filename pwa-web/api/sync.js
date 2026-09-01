// Sync relay between the web studio and the watch.
//
// Storage is Vercel KV (Upstash Redis) over its REST API, called with plain
// fetch so this file needs no dependencies and no package.json. Connect a KV
// store to the project and Vercel injects KV_REST_API_URL / KV_REST_API_TOKEN
// automatically; until then the relay falls back to per-instance memory, which
// works but does not survive a cold start. The response always reports which
// one served the request under `storage`.
//
// The previous implementation persisted to a public webhook.site bin, which
// expires after about a week and exposed saved GPS locations to anyone.

const KV_URL = process.env.KV_REST_API_URL;
const KV_TOKEN = process.env.KV_REST_API_TOKEN;
const SYNC_TOKEN = process.env.SYNC_TOKEN;
const hasKv = Boolean(KV_URL && KV_TOKEN);

// Only used when no KV store is connected. Per-instance and short lived.
const memoryStore = new Map();

async function kvFetch(path, init = {}) {
  const response = await fetch(`${KV_URL}${path}`, {
    ...init,
    headers: { Authorization: `Bearer ${KV_TOKEN}`, ...(init.headers || {}) },
  });
  if (!response.ok) throw new Error(`KV ${path} responded ${response.status}`);
  return response.json();
}

async function readRecord(key) {
  if (!hasKv) return memoryStore.get(key) || null;
  const { result } = await kvFetch(`/get/${encodeURIComponent(key)}`);
  if (result === null || result === undefined) return null;
  try {
    return typeof result === 'string' ? JSON.parse(result) : result;
  } catch (_) {
    return null;
  }
}

async function writeRecord(key, record) {
  if (!hasKv) {
    memoryStore.set(key, record);
    return;
  }
  await kvFetch(`/set/${encodeURIComponent(key)}`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(record),
  });
}

/**
 * Auth is opt-in: without SYNC_TOKEN configured the relay stays open, exactly as
 * before, so setting it is a deliberate upgrade rather than a breaking change.
 */
function isAuthorised(req) {
  if (!SYNC_TOKEN) return true;
  const presented = req.headers['x-sync-token'] || req.query.token;
  return presented === SYNC_TOKEN;
}

module.exports = async (req, res) => {
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Methods', 'GET,POST,OPTIONS');
  res.setHeader('Access-Control-Allow-Headers', 'Content-Type, X-Sync-Token');
  res.setHeader('Cache-Control', 'no-cache, no-store, must-revalidate');

  if (req.method === 'OPTIONS') return res.status(200).end();

  if (!isAuthorised(req)) {
    return res.status(401).json({ status: 'error', error: 'Invalid sync token' });
  }

  const code = req.query.code || req.headers['x-sync-code'] || '41331';
  const key = `sync:${code}`;
  const storage = hasKv ? 'vercel-kv' : 'memory';

  if (req.method === 'GET') {
    try {
      const record = await readRecord(key);
      return res.status(200).json({
        status: 'ok',
        code,
        storage,
        hasData: Boolean(record),
        data: record?.data ?? null,
        updatedAt: record?.updatedAt ?? null,
      });
    } catch (error) {
      // Surface the failure instead of silently serving an empty payload, which
      // the watch would happily treat as "nothing to sync".
      return res.status(502).json({ status: 'error', storage, error: error.message });
    }
  }

  if (req.method === 'POST') {
    try {
      let body = req.body;
      if (typeof body === 'string') {
        try { body = JSON.parse(body); } catch (_) { /* keep the raw string */ }
      }
      if (!body || typeof body !== 'object') {
        return res.status(400).json({ status: 'error', error: 'Body must be a JSON object' });
      }

      const record = { data: body, updatedAt: Date.now() };
      await writeRecord(key, record);
      return res.status(200).json({
        status: 'ok',
        code,
        storage,
        message: hasKv ? 'Saved to Vercel KV' : 'Saved to instance memory (no KV store connected)',
        updatedAt: record.updatedAt,
      });
    } catch (error) {
      return res.status(502).json({ status: 'error', storage, error: error.message });
    }
  }

  return res.status(405).json({ status: 'error', error: 'Method not allowed' });
};
