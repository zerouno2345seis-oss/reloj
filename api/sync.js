// Vercel discovers serverless functions from the root-level api/ directory.
// Keep the implementation next to the PWA while exporting it from here.
module.exports = require('../pwa-web/api/sync.js');
