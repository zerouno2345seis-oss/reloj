import assert from 'node:assert/strict';
import { mkdir } from 'node:fs/promises';
import path from 'node:path';
import { pathToFileURL } from 'node:url';

const modulesRoot = process.env.CODEX_NODE_MODULES;
if (!modulesRoot) throw new Error('Set CODEX_NODE_MODULES to a node_modules directory containing Playwright.');
const { chromium } = await import(pathToFileURL(path.join(modulesRoot, 'playwright', 'index.mjs')).href);

const faceIds = [
  'FAJR_MIHRAB',
  'DHIKR_PULSE',
  'QIBLA_SERENITY',
  'QURAN_GALLERY',
  'DAILY_ORBITS',
  'BELIEVER_MOSAIC',
];
const outputDirectory = path.resolve('design-proposals/watchfaces-v2/implementation');
await mkdir(outputDirectory, { recursive: true });

const browser = await chromium.launch({ channel: 'chrome', headless: true });
const context = await browser.newContext({
  viewport: { width: 1700, height: 1100 },
  deviceScaleFactor: 1,
  serviceWorkers: 'block',
  reducedMotion: 'reduce',
});
const page = await context.newPage();
await page.goto(process.env.WATCHFACE_PREVIEW_URL || 'http://127.0.0.1:8765', { waitUntil: 'networkidle' });
await page.locator('[data-tab-target="watchfaces"]').click();
await page.addStyleTag({ content: `
  .watch-frame { width: 472px !important; height: 472px !important; aspect-ratio: auto !important; }
  .wf-dial-container { width: 438px !important; height: 438px !important; }
` });

for (const faceId of faceIds) {
  await page.locator(`[data-model-id="${faceId}"]`).click();
  const dial = page.locator('#wfDialPreviewContainer');
  const box = await dial.boundingBox();
  assert.ok(box, `${faceId} preview is not visible`);
  assert.equal(Math.round(box.width), 438, `${faceId} width`);
  assert.equal(Math.round(box.height), 438, `${faceId} height`);
  assert.ok(await dial.locator('[data-slot-key]').count(), `${faceId} has no interactive slots`);
  assert.ok(await dial.locator('[data-action]').count(), `${faceId} has no interactive actions`);
  await dial.screenshot({ path: path.join(outputDirectory, `${faceId}.png`) });

  const slot = dial.locator('[data-slot-key]').first();
  const slotKey = await slot.getAttribute('data-slot-key');
  const selectId = { topSlot: 'selectTopSlot', leftSlot: 'selectLeftSlot', rightSlot: 'selectRightSlot', bottomSlot: 'selectBottomSlot' }[slotKey];
  const before = await page.locator(`#${selectId}`).inputValue();
  await slot.click();
  assert.notEqual(await page.locator(`#${selectId}`).inputValue(), before, `${faceId} tap did not cycle its slot`);
  await dial.locator(`[data-slot-key="${slotKey}"]`).first().click({ button: 'right' });
  assert.equal(await page.evaluate(() => document.activeElement?.id), selectId, `${faceId} long-press equivalent did not focus customization`);

  await dial.locator('[data-action]').first().click();
  await page.locator('.app-toast.is-visible').waitFor({ state: 'visible' });
}

await browser.close();
console.log(`Captured ${faceIds.length} watch faces at 438×438 in ${outputDirectory}`);
