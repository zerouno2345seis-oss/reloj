import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';

const root = new URL('../../', import.meta.url);
const [config, home, newFaces, app, css] = await Promise.all([
  readFile(new URL('app/src/main/java/com/quran/watch8/data/model/WatchFaceConfig.kt', root), 'utf8'),
  readFile(new URL('app/src/main/java/com/quran/watch8/ui/screens/WatchFaceHomeScreen.kt', root), 'utf8'),
  readFile(new URL('app/src/main/java/com/quran/watch8/ui/screens/watchfaces/NewWatchFaces.kt', root), 'utf8'),
  readFile(new URL('pwa-web/app.js', root), 'utf8'),
  readFile(new URL('pwa-web/style.css', root), 'utf8'),
]);

const newIds = [
  'FAJR_MIHRAB',
  'DHIKR_PULSE',
  'QIBLA_SERENITY',
  'QURAN_GALLERY',
  'DAILY_ORBITS',
  'BELIEVER_MOSAIC',
];

test('Android and web expose exactly the same fifteen face identifiers', () => {
  for (const id of newIds) {
    assert.match(config, new RegExp(`\\b${id}\\b`));
    assert.match(home, new RegExp(`WatchFaceModelId\\.${id}`));
    assert.match(app, new RegExp(`id:\\s*['\"]${id}['\"]`));
  }
  const enumBody = config.match(/enum class WatchFaceModelId[\s\S]*?\n}/)?.[0] ?? '';
  const webBody = app.match(/const WATCH_FACE_MODELS\s*=\s*\[[\s\S]*?\n\];/)?.[0] ?? '';
  assert.equal((enumBody.match(/^\s{4}[A-Z][A-Z0-9_]+\(/gm) ?? []).length, 15);
  assert.equal((webBody.match(/\bid:\s*['\"][A-Z0-9_]+['\"]/g) ?? []).length, 15);
});

test('new faces use the 438 reference grid and 53px circular safe inset', () => {
  assert.match(newFaces, /REFERENCE_SIZE\s*=\s*438f/);
  assert.match(newFaces, /SAFE_INSET\s*=\s*53f/);
  assert.doesNotMatch(newFaces, /renderDialHours|N\/E\/S\/W|"6\.4k"|"72 bpm"|72 نبضة/);
});

test('every new model has a dedicated web renderer and model-specific slot profile', () => {
  assert.match(app, /slotProfiles/);
  assert.match(app, /function getActiveWatchFaceSlots\s*\(/);
  assert.match(app, /function switchWatchFaceModel\s*\(/);
  for (const id of newIds) {
    assert.match(app, new RegExp(`case ['\"]${id}['\"]`));
    assert.match(css, new RegExp(`wf-v2-${id.toLowerCase().replaceAll('_', '-')}`));
  }
});

test('Quran gallery keeps metadata and verse in one wrapping block', () => {
  assert.match(newFaces, /buildAnnotatedString/);
  assert.match(newFaces, /maxLines\s*=\s*3/);
  assert.match(newFaces, /TextOverflow\.Ellipsis/);
  assert.match(app, /سورة الفاتحة · 1/);
});

test('new face previews never render a second perimeter dial', () => {
  const renderer = app.match(/function renderNewWatchFacePreview[\s\S]*?\n}/)?.[0] ?? '';
  assert.ok(renderer.length > 0, 'specialized renderer is missing');
  assert.doesNotMatch(renderer, /renderDialHours|wf-dial-number/);
});

test('every visible information surface has tap and long-press interaction wiring', () => {
  assert.match(newFaces, /detectTapGestures\s*\(/);
  assert.match(newFaces, /onTap\s*=/);
  assert.match(newFaces, /onLongPress\s*=/);
  const interactiveSurfaces = newFaces.match(/\.(?:faceAction|fixedAction)\s*\(/g) ?? [];
  assert.ok(interactiveSurfaces.length >= 30, `expected at least 30 interactive surfaces, found ${interactiveSurfaces.length}`);
  for (const action of ['NEXT_PRAYER', 'BATTERY', 'GREGORIAN_DATE', 'QURAN_RESUME', 'QIBLA', 'TASBIH', 'WEATHER', 'SUNRISE_SUNSET']) {
    assert.match(newFaces, new RegExp(`ComplicationType\\.${action}`));
  }
  const webRenderer = app.match(/function renderNewWatchFacePreview[\s\S]*?\n}/)?.[0] ?? '';
  assert.match(app, /function getNewFaceSlotHtml[\s\S]*?data-slot-key/);
  assert.match(webRenderer, /data-action/);
  assert.match(app, /querySelectorAll\('\[data-slot-key\]'\)/);
  assert.match(app, /addEventListener\('contextmenu'/);
});
