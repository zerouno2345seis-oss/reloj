import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';

const root = new URL('../../', import.meta.url);
const [home, tileConfig, settings, reader, preferences, sync, mainActivity, qibla, watchFaceCalculations, watchFaceHome, locations, prayerHelper, appJs, watchIcons, newFaces, viewModel] = await Promise.all([
  readFile(new URL('app/src/main/java/com/quran/watch8/ui/screens/HomeScreen.kt', root), 'utf8'),
  readFile(new URL('app/src/main/java/com/quran/watch8/data/model/TileConfig.kt', root), 'utf8'),
  readFile(new URL('app/src/main/java/com/quran/watch8/ui/screens/SettingsScreen.kt', root), 'utf8'),
  readFile(new URL('app/src/main/java/com/quran/watch8/ui/screens/QuranReaderScreen.kt', root), 'utf8'),
  readFile(new URL('app/src/main/java/com/quran/watch8/data/repository/PreferencesRepository.kt', root), 'utf8'),
  readFile(new URL('app/src/main/java/com/quran/watch8/util/LocalSyncServer.kt', root), 'utf8'),
  readFile(new URL('app/src/main/java/com/quran/watch8/MainActivity.kt', root), 'utf8'),
  readFile(new URL('app/src/main/java/com/quran/watch8/ui/screens/QiblaCompassScreen.kt', root), 'utf8'),
  readFile(new URL('app/src/main/java/com/quran/watch8/ui/screens/watchfaces/WatchFaceCalculations.kt', root), 'utf8'),
  readFile(new URL('app/src/main/java/com/quran/watch8/ui/screens/WatchFaceHomeScreen.kt', root), 'utf8'),
  readFile(new URL('app/src/main/java/com/quran/watch8/ui/screens/LocationsScreen.kt', root), 'utf8'),
  readFile(new URL('app/src/main/java/com/quran/watch8/util/PrayerTimesHelper.kt', root), 'utf8'),
  readFile(new URL('pwa-web/app.js', root), 'utf8'),
  readFile(new URL('app/src/main/java/com/quran/watch8/ui/components/WatchIcons.kt', root), 'utf8'),
  readFile(new URL('app/src/main/java/com/quran/watch8/ui/screens/watchfaces/NewWatchFaces.kt', root), 'utf8'),
  readFile(new URL('app/src/main/java/com/quran/watch8/ui/viewmodel/MainViewModel.kt', root), 'utf8'),
]);

test('watch exposes automatic layout as an assignable and reversible tile action', () => {
  assert.match(tileConfig, /TileActionItem\("auto_layout"/);
  assert.match(tileConfig, /TileActionItem\("auto_layout_restore"/);
  assert.match(home, /fun generateAutomaticLayout\s*\(/);
  assert.match(home, /"auto_layout"\s*->/);
  assert.match(home, /"auto_layout_restore"\s*->/);
});

test('watch reading tile starts with surah and ayah then wraps the verse', () => {
  assert.match(home, /buildAnnotatedString/);
  assert.match(home, /lastPos\?\.ayahSnippet/);
  assert.match(home, /سورة الكهف/);
  assert.match(home, /maxLines\s*=\s*3/);
});

test('watch folder launcher is circular, translucent, balanced, and editable', () => {
  assert.match(home, /FolderLauncherOverlay/);
  assert.match(home, /CircleShape/);
  assert.match(home, /selectedFolderItems/);
  assert.doesNotMatch(home, /def\.title\.take\(6\)/);
});

test('watch prayer strip is rendered as two explicit rows', () => {
  assert.match(home, /PrayerStripTable/);
  // A names row and a times row, with the names row dropped when the columns
  // are too narrow to hold them.
  assert.match(home, /pList\.forEach\s*\{\s*\(name,\s*_\)\s*->/);
  assert.match(home, /pList\.forEach\s*\{\s*\(_,\s*time\)\s*->/);
  assert.match(home, /showNames/);
});

test('reader settings support the expanded range, fonts, and synced custom colours', () => {
  assert.match(preferences, /coerceIn\(8f,\s*48f\)/);
  for (const font of ['amiri', 'naskh', 'tajawal', 'cairo']) assert.match(settings, new RegExp(`"${font}"`));
  assert.match(preferences, /customReaderBgColor/);
  assert.match(preferences, /customReaderTextColor/);
  assert.match(preferences, /customAyahColor/);
  assert.match(sync, /customReaderBgColor/);
  assert.match(reader, /customReaderBgColor/);
});

test('mixed oval and square mode is honoured by the actual watch renderer', () => {
  // The tile shape is driven by appearance.tileShape, and "mixed" alternates
  // by tile index so a connected grid still gets some rhythm.
  assert.match(home, /appearance\.tileShape/);
  assert.match(home, /"mixed"\s*->/);
  assert.match(home, /index\s*%\s*3/);
});

test('qibla action opens a real sensor-backed compass screen', () => {
  assert.match(mainActivity, /composable\("qibla"\)/);
  assert.match(qibla, /TYPE_ROTATION_VECTOR/);
  assert.match(qibla, /qiblaBearing/);
  assert.match(watchFaceCalculations, /KAABA_LATITUDE/);
  assert.match(watchFaceCalculations, /KAABA_LONGITUDE/);
});

test('settings can save the current watch face as a reusable preset', () => {
  assert.match(settings, /saveCustomPreset/);
  assert.match(settings, /tileConfig/);
  assert.match(settings, /حفظ الواجهة الحالية كقالب/);
});

test('watch face Quran resume includes surah, ayah number, and verse text', () => {
  assert.match(watchFaceHome, /formatQuranReadingLine\s*\(/);
  assert.match(watchFaceHome, /reading\?\.text/);
  assert.match(watchFaceHome, /maxLines\s*=\s*2/);
});

test('watch-face carousel does not draw a second numbered perimeter dial', () => {
  assert.doesNotMatch(watchFaceHome, /for\s*\(num\s+in\s+1\.\.12\)/);
  assert.doesNotMatch(watchFaceHome, /فلكي محيطي \(أرقام\)/);
});

test('location quick actions use consistent vector icons and single-line labels', () => {
  assert.match(locations, /LocationQuickAction\s*\(/);
  for (const icon of ['Car', 'Star']) {
    assert.match(locations, new RegExp(`WatchIcons\\.${icon}`));
  }
  assert.match(locations, /label = "موقف سريع"/);
  assert.match(locations, /label = "مهم"/);
  assert.doesNotMatch(locations, /label = "مسجد"|"المنزل"/);
  assert.doesNotMatch(locations, /LumiaSaveButton\("[🚗⭐🕌]/u);
  assert.match(locations, /softWrap\s*=\s*false/);
});

// Comments in these files describe the very patterns the battery tests forbid,
// so match against code with comments stripped.
const codeOnly = (source) =>
  source.replace(/\/\*[\s\S]*?\*\//g, ' ').replace(/(^|[^:])\/\/.*$/gm, '$1');

test('primary circular lists reserve the twelve-percent safe inset', () => {
  assert.match(settings, /WatchSafeInsets\.listContentPadding/);
  assert.match(locations, /WatchSafeInsets\.listContentPadding/);
});

// The studio preview and the watch must format a countdown the same way. This
// reads the units out of the Kotlin formatter rather than hard-coding them, so
// changing formatCountdown fails here until the web mockups follow.
test('studio countdown mockups mirror PrayerTimesHelper.formatCountdown', () => {
  const withHours = prayerHelper.match(/if \(hours > 0\) "([^"]*)"/);
  const minutesOnly = prayerHelper.match(/else "([^"]*)"/);
  assert.ok(withHours && minutesOnly, 'formatCountdown no longer has two literal branches');

  const strip = (s) => s.replace(/\$\{[^}]*\}|\$\w+/g, ' ').trim().split(/\s+/);
  const [hourUnit, minuteUnit] = strip(withHours[1]);
  assert.equal(strip(minutesOnly[1])[0], minuteUnit);

  const countdowns = [...appJs.matchAll(/countdown:\s*'([^']*)'/g)].map((m) => m[1]);
  assert.ok(countdowns.length >= 3, 'expected the studio to carry countdown mockups');
  for (const value of countdowns) {
    assert.ok(
      value.includes(minuteUnit),
      `studio countdown "${value}" does not use the watch minute unit "${minuteUnit}"`,
    );
    assert.doesNotMatch(value, /\d\s*[hm]\b/, `studio countdown "${value}" still uses Latin h/m`);
  }
  assert.ok(countdowns.some((v) => v.includes(hourUnit)), `no studio countdown exercises "${hourUnit}"`);
  assert.doesNotMatch(appJs, /'المغرب \d+\s*h/, 'a studio label still formats the countdown the Latin way');
});

// Every hand-drawn icon costs bytes and review attention; one that nothing calls
// is dead weight that slipped in with a screen rewrite.
test('every WatchIcons composable is actually used', async () => {
  const { readdir } = await import('node:fs/promises');
  const srcRoot = new URL('app/src/main/java/', root);
  const files = (await readdir(srcRoot, { recursive: true })).filter((f) => f.endsWith('.kt'));
  const sources = await Promise.all(files.map((f) => readFile(new URL(f, srcRoot), 'utf8')));
  const corpus = sources.join('\n');

  const declared = [...watchIcons.matchAll(/^\s{4}fun (\w+)\(/gm)].map((m) => m[1]);
  assert.ok(declared.length > 5, 'expected WatchIcons to declare a set of icons');
  const unused = declared.filter((name) => !corpus.includes(`WatchIcons.${name}`));
  assert.deepEqual(unused, [], `unused WatchIcons: ${unused.join(', ')}`);
});

// Battery: a `while (true) { delay() }` inside a LaunchedEffect keeps waking the
// CPU with the screen off, because the composition outlives onStop. Every such
// ticker has to be lifecycle-scoped instead.
test('no screen ticker runs outside the resumed lifecycle', () => {
  for (const [name, source] of [['HomeScreen', home], ['WatchFaceHomeScreen', watchFaceHome]]) {
    assert.doesNotMatch(
      codeOnly(source),
      /LaunchedEffect\([^)]*\)\s*\{\s*(?:[^{}]*\n)*?\s*while \(true\)/,
      `${name} still drives a ticker from a bare LaunchedEffect`,
    );
    assert.match(source, /RepeatOnResumed/, `${name} should tick via RepeatOnResumed`);
  }
  assert.match(newFaces, /RepeatOnResumed/, 'the compass sensor must be lifecycle-scoped');
  assert.match(qibla, /RepeatOnResumed/, 'the compass screen must be lifecycle-scoped');
  // ~5 Hz is enough for a qibla arrow; SENSOR_DELAY_UI is ~16 Hz of fused sensor.
  assert.doesNotMatch(codeOnly(newFaces), /SENSOR_DELAY_UI/);
  assert.doesNotMatch(codeOnly(qibla), /SENSOR_DELAY_UI/);
});

// The LAN channel is unreachable from an HTTPS page (mixed content), so the
// listening socket was a permanently blocked thread serving nothing.
test('the dead LAN sync socket is gone', () => {
  const code = codeOnly(sync);
  assert.doesNotMatch(code, /ServerSocket|\.accept\(\)/);
  assert.doesNotMatch(code, /FIXED_PORT/);
  assert.doesNotMatch(codeOnly(mainActivity), /LocalSyncServer\.start/);
  // The HTTPS relay is the surviving channel and must still be wired up.
  assert.match(code, /CLOUD_RELAY_URL/);
});

// Weather is only worth fetching when someone can see it.
test('weather refreshes on demand rather than from an endless timer', () => {
  assert.doesNotMatch(codeOnly(viewModel), /while \(true\) \{ delay\(/);
  assert.match(viewModel, /fun refreshWeatherIfStale/);
  assert.match(codeOnly(mainActivity), /refreshWeatherIfStale/);
});
