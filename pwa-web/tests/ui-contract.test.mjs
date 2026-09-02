import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';

const projectRoot = new URL('../', import.meta.url);
const repoRoot = new URL('../../', import.meta.url);
const [html, css, app, serviceWorker, home] = await Promise.all([
  readFile(new URL('index.html', projectRoot), 'utf8'),
  readFile(new URL('style.css', projectRoot), 'utf8'),
  readFile(new URL('app.js', projectRoot), 'utf8'),
  readFile(new URL('sw.js', projectRoot), 'utf8'),
  readFile(new URL('app/src/main/java/com/quran/watch8/ui/screens/HomeScreen.kt', repoRoot), 'utf8'),
]);

const requiredControlIds = [
  'watchScreenSimulator',
  'selectedTileEditorRight',
  'selectedTileEditorLeft',
  'btnUndo',
  'btnRedo',
  'btnCopy',
  'btnPaste',
  'btnSelectAll',
  'btnDeleteKey',
  'btnAddTile',
  'btnAddRow',
  'btnCompactRow',
  'btnRestoreRow',
  'btnExpandTile',
  'btnDeleteTile',
  'btnSaveTilesToWatch',
  'btnOpenDesignerMore',
  'designerAddMenu',
  'designerMoreMenu',
  'designerInspectorPanel',
  'designerLayersPanel',
  'btnToggleAllDetails',
  'tileLayersList',
  'btnCloudPush',
  'btnCloudPull',
  'btnLocalSync',
  'quranSearchInput',
  'locationsList',
  'btnSaveSettingsToWatch',
];

const requiredSections = [
  'tab-overview',
  'tab-tiles',
  'tab-presets',
  'tab-quran',
  'tab-locations',
  'tab-settings',
  'tab-sync',
];

test('Finder shell exposes every planned workspace section', () => {
  assert.match(html, /data-finder-shell/);
  for (const sectionId of requiredSections) {
    assert.match(html, new RegExp(`id=["']${sectionId}["']`));
  }
});

test('all existing feature controls remain present exactly once', () => {
  for (const id of requiredControlIds) {
    const matches = html.match(new RegExp(`id=["']${id}["']`, 'g')) ?? [];
    assert.equal(matches.length, 1, `${id} should exist exactly once`);
  }
});

test('HTML does not mix inline click handlers with centralized listeners', () => {
  assert.doesNotMatch(html, /\sonclick=/i);
});

test('Finder design tokens and responsive application shell are defined', () => {
  assert.match(css, /--finder-blue\s*:/);
  assert.match(css, /--finder-sidebar\s*:/);
  assert.match(css, /\.finder-sidebar\s*\{/);
  assert.match(css, /\.app-shell\s*\{/);
  assert.match(css, /@media\s*\(max-width:\s*760px\)/);
});

test('watch preview is fluid instead of forcing a 480px mobile overflow', () => {
  const watchRule = css.match(/\.watch-frame\s*\{[\s\S]*?\}/)?.[0] ?? '';
  assert.match(watchRule, /min\(100%,\s*480px\)|clamp\(/);
  assert.doesNotMatch(watchRule, /width:\s*480px/);
});

test('designer supports sticky preview, square tiles, edge margins, and proportional resize', () => {
  assert.match(css, /\.canvas-sticky\s*\{[^}]*position:\s*sticky/);
  // Default (connected) tiles are square; oval/circle get their own radius.
  assert.match(css, /\.tile-shape-square-connected\s*\{[^}]*border-radius:\s*0/);
  assert.match(css, /\.tile-resize-handle\s*\{/);
  assert.match(html, /data-edge-inset="top"/);
  assert.match(app, /const MAX_EDITOR_ROWS = 5/);
  assert.match(app, /function resizeTileWithRatio\s*\(/);
  assert.match(app, /manualLayout = true/);
});

test('smart grid redistributes tiles, protects text, and formats the Quran resume tile', () => {
  assert.match(app, /function rebalanceRowWidths\s*\(/);
  assert.match(app, /function applySmartAutoLayout\s*\(/);
  assert.match(app, /function applyCircularAutoLayout\s*\(/);
  assert.match(app, /radialLayout/);
  assert.match(app, /تم إنشاء ترتيب تلقائي ذكي/);
  assert.match(app, /tile\.colorHex = color/);
  assert.match(app, /tile\.fontSize = natural === 12/);
  assert.match(app, /tile\.manualLayout = false/);
  // Label mirrors HomeScreen.kt's displayTitle map (parity with the watch).
  assert.match(app, /quran_resume:\s*'سورة الكهف'/);
  assert.match(app, /وَتَحْسَبُهُمْ أَيْقَاظًا وَهُمْ رُقُودٌ/);
  assert.match(css, /\.canvas-tile\s*\{[^}]*overflow:\s*hidden/);
  assert.match(css, /-webkit-line-clamp:\s*2/);
});

test('studio and watch renderers agree on scale, labels and anchor model', () => {
  // Shared 438px reference scale on both sides.
  assert.match(app, /renderScale = Math\.max\([\s\S]*?\/ 438\)/);
  assert.match(home, /renderScale = \(screenWidth \/ 438f\)/);
  // Anchor model: translate(-x%, -y%) in the web mirrors BiasAlignment on the watch.
  assert.match(app, /translate\(\$\{-tx\}%, \$\{-ty\}%\)/);
  assert.match(app, /translate\(\$\{-ix\}%, \$\{-iy\}%\)/);
  // Same tile labels on both sides.
  for (const label of ['التسجيلات', 'المصحف', 'الإعدادات', 'إسلاميات']) {
    assert.match(app, new RegExp(label));
    assert.match(home, new RegExp(label));
  }
  // A tile shows its information, not its name, wherever live data exists.
  assert.match(home, /"prayer"\s*->\s*"\$\{nextPrayer\?\.nameAr/);
  assert.match(home, /"battery"\s*->\s*"\$batteryPercent%"/);
  assert.match(home, /"weather"\s*->\s*weather\.temperatureLabel/);
  assert.match(home, /"qibla"\s*->\s*"\$qiblaDeg°"/);
});

test('auto-layout control sits in the two-row watch toolbar, not the distant layers panel', () => {
  const toolbarStart = html.indexOf('<div class="canvas-toolbar">');
  // The Layer 1 tab also has a .watch-stage, so scope to the one that follows
  // this toolbar rather than the first in the document.
  const watchStart = html.indexOf('<div class="watch-stage">', toolbarStart);
  const toolbar = html.slice(toolbarStart, watchStart);
  assert.match(toolbar, /id="btnAutoLayout"/);
  assert.doesNotMatch(html.match(/<div class="add-menu">[\s\S]*?<\/div>/)?.[0] ?? '', /btnAutoLayout/);
  assert.match(css, /\.canvas-toolbar-row\s*\{/);
  assert.match(css, /\.smart-layout-button\s*\{/);
});

test('canvas-first designer keeps the watch clear with menus and fixed properties', () => {
  assert.match(html, /id=["']designerAddMenu["'][^>]*role=["']menu["']/);
  assert.match(html, /id=["']designerMoreMenu["'][^>]*role=["']menu["']/);
  assert.match(html, /id=["']designerInspectorPanel["']/);
  assert.doesNotMatch(html, /designerInspectorDrawer|designerDrawerBackdrop/);
  // The layers list is a fixed panel directly under the watch, not a popover menu.
  assert.doesNotMatch(html, /id=["']designerLayersMenu["']/);
  assert.doesNotMatch(html, /id=["']btnOpenLayersPanel["']/);
  const stageAt = html.indexOf('<div class="watch-stage">', html.indexOf('canvas-toolbar'));
  assert.ok(html.indexOf('id="designerLayersPanel"') > stageAt, 'layers panel sits after the watch stage');
  assert.match(html, /id=["']designerLayersPanel["'][\s\S]{0,200}id=["']tileLayersList["']/);
  for (const section of ['المحتوى', 'التفاعل', 'التخطيط', 'الخط والأيقونة', 'الألوان']) {
    assert.match(html, new RegExp(`<summary>${section}</summary>`));
  }
  // All five property groups start expanded, with a single expand/collapse-all control.
  for (const sid of ['secContent', 'secInteract', 'secLayout', 'secFont', 'secColors']) {
    assert.match(html, new RegExp(`id=["']${sid}["'][^>]*\\sopen`));
  }
  assert.match(html, /id=["']btnToggleAllDetails["']/);
  assert.match(html, /data-section-toggle="secColors"/);
  assert.match(app, /function initInspectorToolbar\s*\(/);
  assert.match(app, /function initDesignerPanels\s*\(/);
  assert.match(app, /function closeDesignerSurfaces\s*\(/);
  assert.match(css, /DESIGNER: CANVAS FIRST \+ FIXED PROPERTIES/);
  assert.match(css, /\.designer-inspector-panel\s*\{[^}]*position:\s*sticky/);
  assert.match(css, /@media \(max-width: 940px\)/);
});

test('the calm tile surface uses the same numbers in the studio and on the watch', () => {
  // A tile is a dark panel + the user's colour as a tint and a hairline. If these
  // drift the designer stops predicting what the watch draws.
  assert.match(app, /const TILE_PANEL_RGB = \[12, 19, 25\]/);
  assert.match(app, /const TILE_TINT_ALPHA = 0\.14/);
  assert.match(app, /const TILE_BORDER_ALPHA = 0\.38/);
  assert.match(app, /function tileSurface\s*\(/);
  assert.match(app, /t\.style\.backgroundColor = surface\.bg/);
  assert.match(app, /t\.style\.borderColor = surface\.border/);
  assert.match(home, /val TilePanel = Color\(0xFF0C1319\)/);
  assert.match(home, /const val TILE_TINT_ALPHA = 0\.14f/);
  assert.match(home, /const val TILE_BORDER_ALPHA = 0\.38f/);
  assert.match(home, /accent\.copy\(alpha = TILE_TINT_ALPHA\)\.compositeOver\(TilePanel\)/);
  // The ground behind the tiles has to stay darker than the panels.
  assert.match(home, /else -> Color\(0xFF05090C\)/);
  assert.match(css, /\.watch-pattern-star-eight \{ background-color:#05090C/);
});

test('tile colours are a fixed-palette dropdown that normalises hex for reliable sync', () => {
  assert.match(html, /<select class="color-select" id="tileBgColor">/);
  assert.match(html, /<select class="color-select" id="tileFontColor">/);
  assert.match(html, /<select class="color-select" id="tileIconColor">/);
  assert.doesNotMatch(html, /<input type="color"/);
  assert.match(app, /function normalizeHex\s*\(/);
  assert.match(app, /function initColorSelects\s*\(/);
  assert.match(app, /function setColorSelect\s*\(/);
  assert.match(app, /const DESIGNER_PALETTE/);
  assert.match(app, /slot\.colorHex = normalizeHex\(/);
});

test('settings expose watch-compatible reader styles and specialised notifications', () => {
  for (const value of ['uthmani', 'kufi', 'sansserif', 'serif', 'navy', 'sepia', 'forest']) {
    assert.match(html, new RegExp(`value=["']${value}["']`));
  }
  for (const id of ['reminderDhuhr', 'reminderAsr', 'reminderMaghrib', 'reminderIsha', 'settingNotificationVibration', 'settingNotificationFullScreen']) {
    assert.match(html, new RegExp(`id=["']${id}["']`));
  }
  assert.match(app, /featureActionCatalog/);
  assert.match(app, /locations_recent/);
});

test('watch appearance offers connected shape, pattern and expanded icon-library controls', () => {
  for (const id of ['settingTileShape', 'settingWatchPattern', 'settingIconPalette']) {
    assert.match(html, new RegExp(`id=["']${id}["']`));
  }
  assert.match(html, /value=["']square-connected["']/);
  assert.match(app, /const expandedIconLibrary/);
  assert.match(app, /iconLibrary\.push/);
  assert.match(app, /function getAppearance\s*\(/);
  assert.match(app, /function applyAppearanceControl\s*\(/);
  assert.match(css, /\.tile-shape-square-connected/);
  assert.match(css, /\.watch-pattern-andalusian/);
  assert.match(css, /\.watch-pattern-damascene/);
});

test('application code supports live preview, layers, sync envelopes and PWA registration', () => {
  assert.match(app, /function getPreviewLabel\s*\(/);
  assert.match(app, /function renderTileLayers\s*\(/);
  assert.match(app, /function unwrapSyncPayload\s*\(/);
  assert.match(app, /serviceWorker\.register\s*\(/);
});

test('Quran search and bookmarks management allows adding and editing bookmarks', () => {
  assert.match(app, /Array\.isArray\(quranData\.quran\)/);
  assert.match(app, /quranData\.quran/);
  assert.match(app, /function addBookmark\s*\(/);
  assert.match(app, /function renderBookmarks\s*\(/);
  assert.match(app, /function editBookmark\s*\(/);
  assert.match(app, /function deleteBookmark\s*\(/);
  assert.match(html, /id=["']bookmarksList["']/);
});

test('service worker uses valid event listener APIs and expected core assets', () => {
  assert.match(serviceWorker, /self\.addEventListener\(['"]install['"]/);
  assert.match(serviceWorker, /self\.addEventListener\(['"]activate['"]/);
  assert.match(serviceWorker, /self\.addEventListener\(['"]fetch['"]/);
  assert.match(serviceWorker, /\.\/vercel\.json/);
  assert.doesNotMatch(serviceWorker, /1788153861/);
});

test('tile resizing is driven from all four edges and keeps neighbouring tiles packed', () => {
  assert.match(app, /const RESIZE_EDGES\s*=\s*\['n',\s*'e',\s*'s',\s*'w'\]/);
  for (const edge of ['n', 'e', 's', 'w']) {
    assert.match(css, new RegExp(`\\.tile-resize-${edge}\\s*\\{`));
  }
  assert.match(app, /function resizeTileFromEdge\s*\(/);
  assert.match(app, /function resizeGridTileWidth\s*\(/);
  assert.match(app, /function resizeGridRowHeight\s*\(/);
  assert.match(app, /rebalanceRowWidths\(/); // still used by the explicit colSpan buttons
});

test('automatic layout offers a broad weighted catalogue and text alignment controls', () => {
  assert.match(html, /data-text-align=["']right["']/);
  assert.match(html, /data-text-align=["']center["']/);
  assert.match(html, /data-text-align=["']left["']/);
  assert.match(app, /const AUTO_LAYOUT_PATTERNS\s*=\s*\[/);
  assert.match(app, /function pickWeightedLayoutPattern\s*\(/);
  assert.match(app, /recentAutoLayoutPatterns/);
});

test('Quran resume renders surah, ayah number, and verse as one wrapping reading line', () => {
  assert.match(app, /quran-resume-line/);
  assert.match(app, /سورة الكهف · 18/);
  assert.match(css, /\.quran-resume-line\s*\{/);
  assert.doesNotMatch(css, /\.quran-resume-content\s*\{[^}]*grid-template-rows/);
});

test('folders are circular translucent launchers with editable balanced contents', () => {
  assert.match(html, /id=["']folderItemsEditor["']/);
  assert.match(app, /function renderFolderItemsEditor\s*\(/);
  assert.match(app, /folderItems/);
  assert.match(app, /folder-preview-orb/);
  assert.match(css, /\.folder-launcher\s*\{/);
  assert.match(css, /backdrop-filter\s*:/);
});

test('prayer strip is a true two-row table and can be added directly', () => {
  assert.match(html, /id=["']btnAddPrayerStrip["']/);
  assert.match(app, /function addPrayerStripRow\s*\(/);
  assert.match(app, /prayer-strip-names/);
  assert.match(app, /prayer-strip-times/);
  assert.match(css, /\.prayer-strip-names/);
  assert.match(css, /\.prayer-strip-times/);
});

test('reader customisation is expanded, live, and accepts custom colours', () => {
  assert.match(html, /id=["']settingReaderBgCustom["']/);
  assert.match(html, /id=["']settingReaderTextCustom["']/);
  assert.match(html, /id=["']settingAyahCustom["']/);
  assert.match(html, /id=["']settingReaderFontSize["'][^>]*min=["']8["'][^>]*max=["']48["']/);
  for (const font of ['amiri', 'naskh', 'tajawal', 'cairo']) {
    assert.match(html, new RegExp(`value=["']${font}["']`));
  }
  assert.match(app, /function applyReaderPreviewTheme\s*\(/);
});

test('auto-layout is assignable to a tile, restores the first arrangement, and tool actions have routes', () => {
  assert.match(app, /id:\s*'auto_layout'/);
  assert.match(app, /auto_layout_restore/);
  assert.match(app, /captureInitialLayout/);
  assert.match(app, /restoreInitialLayout/);
  assert.match(app, /tasbih_increment/);
  assert.match(app, /qibla_compass/);
  assert.match(app, /folder_islamic_customize/);
});
