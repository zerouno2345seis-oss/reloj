import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';

const root = new URL('../../', import.meta.url);
const [settings, reader, preferences, mainActivity, locations, prayerHelper, appJs, watchIcons, viewModel, entry, tile, manifest, gradle] = await Promise.all([
  readFile(new URL('app/src/main/java/com/quran/watch8/ui/screens/SettingsScreen.kt', root), 'utf8'),
  readFile(new URL('app/src/main/java/com/quran/watch8/ui/screens/QuranReaderScreen.kt', root), 'utf8'),
  readFile(new URL('app/src/main/java/com/quran/watch8/data/repository/PreferencesRepository.kt', root), 'utf8'),
  readFile(new URL('app/src/main/java/com/quran/watch8/MainActivity.kt', root), 'utf8'),
  readFile(new URL('app/src/main/java/com/quran/watch8/ui/screens/LocationsScreen.kt', root), 'utf8'),
  readFile(new URL('app/src/main/java/com/quran/watch8/util/PrayerTimesHelper.kt', root), 'utf8'),
  readFile(new URL('pwa-web/app.js', root), 'utf8'),
  readFile(new URL('app/src/main/java/com/quran/watch8/ui/components/WatchIcons.kt', root), 'utf8'),
  readFile(new URL('app/src/main/java/com/quran/watch8/ui/viewmodel/MainViewModel.kt', root), 'utf8'),
  readFile(new URL('app/src/main/java/com/quran/watch8/ui/screens/EntryScreen.kt', root), 'utf8'),
  readFile(new URL('app/src/main/java/com/quran/watch8/tile/QuranPrayerTileService.kt', root), 'utf8'),
  readFile(new URL('app/src/main/AndroidManifest.xml', root), 'utf8'),
  readFile(new URL('app/build.gradle.kts', root), 'utf8'),
]);

test('reader settings support the expanded range, fonts, and synced custom colours', () => {
  assert.match(preferences, /coerceIn\(8f,\s*48f\)/);
  // The watch offers the two Quran faces, Madinah ("default") and Amiri; every
  // other font id the web can sync still resolves (see the typography test).
  for (const font of ['default', 'amiri']) assert.match(settings, new RegExp(`"${font}"`));
  assert.match(preferences, /customReaderBgColor/);
  assert.match(preferences, /customReaderTextColor/);
  assert.match(preferences, /customAyahColor/);
  assert.match(reader, /customReaderBgColor/);
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
// The LAN channel is unreachable from an HTTPS page (mixed content), so the
// listening socket was a permanently blocked thread serving nothing.
// Weather is only worth fetching when someone can see it.
// Every screen used to re-derive "which prayer is next" on its own, and they
// disagreed: some counted sunrise, some printed "دقيقة" where others printed
// "د", and one fell back to a time already hours in the past.
// The watch only ever pulled, so a bookmark added or deleted on it was invisible
// to the studio -- and the next pull deleted it again.
// A delete button that is only a caption inside another button does nothing.
test('a bookmark on the watch can be opened or deleted, and shows no timestamp', async () => {
  const bookmarks = await readFile(new URL('app/src/main/java/com/quran/watch8/ui/screens/BookmarksScreen.kt', root), 'utf8');
  // A tap opens the verse; a sideways swipe uncovers the delete.
  assert.match(bookmarks, /onOpen = \{ onOpenAyah\(bookmark\.surah, bookmark\.ayah\) \}/);
  assert.match(bookmarks, /onDelete = \{ viewModel\.removeBookmark\(bookmark\.id\) \}/);
  assert.match(bookmarks, /detectHorizontalDragGestures/);
  assert.doesNotMatch(bookmarks, /SimpleDateFormat|dateFormat/);
  // The timestamp still exists -- it is what "by time added" sorts on.
  assert.match(bookmarks, /sortedByDescending \{ it\.timestamp \}/);
});

// The reader and the settings screen must agree about what a verse looks like.
test('reader typography has one definition, previewed in settings', async () => {
  const typography = await readFile(new URL('app/src/main/java/com/quran/watch8/ui/components/ReaderTypography.kt', root), 'utf8');
  assert.match(typography, /const val SAMPLE_AYAH/);
  assert.match(typography, /fun coerceFontSize/);
  // The reader draws its round pages with the platform, so it takes the same
  // choice as a Typeface; settings previews it as a Compose FontFamily.
  assert.match(reader, /ReaderTypography\.typeface\(/);
  assert.match(settings, /ReaderTypography\.fontFamily\(/);
  assert.match(typography, /fun typeface\([\s\S]{0,200}"sansserif", "tajawal", "cairo" -> Typeface\.SANS_SERIF[\s\S]{0,80}"serif", "naskh" -> Typeface\.SERIF/);
  assert.match(settings, /ReaderTypography\.SAMPLE_AYAH/);
  assert.match(settings, /ReaderTypography\.coerceFontSize/);
  // No screen keeps its own copy of the font table any more.
  assert.doesNotMatch(reader, /"kufi"\s*->\s*FontFamily\.Cursive/);
  // System fonts cannot draw the Uthmani marks in this text (U+06E1, U+08F0-2):
  // Quranic choices must resolve to the bundled Quran font.
  assert.match(typography, /R\.font\.amiri_quran/);
  assert.doesNotMatch(typography, /FontFamily\.Cursive/);
  for (const font of ['naskh', 'tajawal', 'cairo', 'sansserif', 'serif']) assert.match(typography, new RegExp(`"${font}"`));
});

// The first layer is the navigation start destination, so swipe-right was dead.
// Updates reach the watch from GitHub releases, not over wireless debugging.
test('the watch can update itself from this repository\'s releases', async () => {
  const [updater, manifest, gradle, publish] = await Promise.all([
    readFile(new URL('app/src/main/java/com/quran/watch8/util/SelfUpdater.kt', root), 'utf8'),
    readFile(new URL('app/src/main/AndroidManifest.xml', root), 'utf8'),
    readFile(new URL('app/build.gradle.kts', root), 'utf8'),
    readFile(new URL('scripts/publish-watch-release.sh', root), 'utf8'),
  ]);
  assert.match(updater, /api\.github\.com\/repos\/zerouno2345seis-oss\/reloj\/releases\/latest/);
  assert.match(manifest, /android\.permission\.REQUEST_INSTALL_PACKAGES/);
  assert.match(manifest, /\.util\.InstallStatusReceiver"\s+android:exported="false"/);
  assert.match(settings, /UpdateChip\(/);
  // The release tag carries the versionCode the updater compares against.
  assert.match(gradle, /versionCode = gitCommitCount/);
  assert.match(publish, /gh release create "v\$\{count\}"/);
});

// The slim app: an ordinary app that opens on the time, the next prayer and
// the Quran. No launcher role, no system watch face, no weather or sync.
test('the app opens on the entry screen and no longer claims the launcher', () => {
  assert.match(mainActivity, /startDestination\s*=\s*"entry"/);
  assert.doesNotMatch(manifest, /category\.HOME/);
  assert.doesNotMatch(manifest, /WallpaperService|WATCH_FACE/);
  assert.doesNotMatch(gradle, /androidx\.wear\.watchface:watchface:/);
  assert.doesNotMatch(codeOnly(viewModel), /weather|pushToCloud|syncWithCloud/i);
});

test('the entry screen shows the next prayer, resumes reading, and ticks only while resumed', () => {
  assert.match(entry, /PrayerTimesHelper\.status\(/);
  assert.match(entry, /HijriDate\.arabic\(\)/);
  assert.match(entry, /reader\/\$\{pos\.surah\}\?startAyah=\$\{pos\.ayahNumber\}/);
  for (const route of ['quran', 'bookmarks', 'settings', 'prayer']) {
    assert.match(entry, new RegExp(`onNavigate\\("${route}"\\)`));
  }
  assert.match(entry, /RepeatOnResumed/);
  assert.doesNotMatch(codeOnly(entry), /LaunchedEffect\([^)]*\)\s*\{\s*while \(true\)/);
});

test('the tile shows prayer times from the saved location and opens the app', () => {
  assert.match(manifest, /android:label="مواقيت الصلاة"/);
  assert.match(tile, /PrayerTimesHelper\.calculate\(/);
  assert.match(tile, /prefs\.selectedLat/);
  assert.match(tile, /PrayerTimesHelper\.schedule\(/);
  assert.doesNotMatch(tile, /apply_preset/);
});

test('the release build is shrunk by R8', () => {
  assert.match(gradle, /release \{[\s\S]*?isMinifyEnabled = true/);
});

// A real watch face (Watch Face Format, no code) reaches the app through
// complications; each one opens its part of the app directly.
test('the app serves complications whose taps open the matching screen', async () => {
  const complications = await readFile(new URL('app/src/main/java/com/quran/watch8/complication/Complications.kt', root), 'utf8');
  for (const name of ['NextPrayer', 'ContinueReading', 'HijriDate', 'QuranShortcut', 'BookmarksShortcut']) {
    assert.match(manifest, new RegExp(`\\.complication\\.${name}ComplicationService`));
  }
  assert.match(manifest, /BIND_COMPLICATION_PROVIDER/);
  assert.match(complications, /putExtra\("targetRoute", route\)/);
  assert.match(mainActivity, /override fun onNewIntent/);
  assert.match(mainActivity, /pendingRoute = intent\.getStringExtra\("targetRoute"\)/);
  assert.match(viewModel, /requestComplicationUpdate\(/);
});

// Reading glides: each screen opens with the last quarter of the one before,
// dimmed, and the end of a surah runs on into the next through a divider.
test('reader carries a quarter screen over and runs on into the next surah', async () => {
  const passages = await readFile(new URL('app/src/main/java/com/quran/watch8/ui/components/RoundPassages.kt', root), 'utf8');
  assert.match(passages, /CARRY_SHARE = 0\.25f/);
  assert.match(passages, /fun carryOf\(/);
  assert.doesNotMatch(reader, /\.tail\b/);
  assert.match(reader, /SurahDivider\(/);
  assert.match(reader, /onOpenSurah\(surahNumber \+ 1\)/);
});

// Our own watch faces are code-free WFF packages, one per style, because WFF
// allows at most eight complication slots in a face. Each is checked against
// the official schema before it builds (an invalid face sent the watch into a
// reboot loop), and every provider it names must exist in the app.
test('watch faces are WFF, validated before build, and name only real services', async () => {
  for (const dir of ['watchface', 'watchface-hands', 'watchface-mihrab']) {
    const faceManifest = await readFile(new URL(`${dir}/src/main/AndroidManifest.xml`, root), 'utf8');
    const face = await readFile(new URL(`${dir}/src/main/res/raw/watchface.xml`, root), 'utf8');
    const faceGradle = await readFile(new URL(`${dir}/build.gradle.kts`, root), 'utf8');
    assert.match(faceManifest, /android:hasCode="false"/);
    assert.match(faceManifest, /com\.google\.wear\.watchface\.format\.version/);
    assert.match(faceGradle, /validate-watchface\.sh/);
    assert.doesNotMatch(face, /<ListConfiguration/);
    const providers = [...face.matchAll(/primaryProvider="com\.quran\.watch8\/com\.quran\.watch8\.complication\.(\w+)"/g)].map((m) => m[1]);
    assert.ok(providers.length >= 4);
    for (const p of new Set(providers)) assert.match(manifest, new RegExp(`\\.complication\\.${p}"`));
    // Samsung's runtime throws, and keeps falling back to its own face, when
    // a slot's fallback type is not one of its supported types.
    for (const m of face.matchAll(/<ComplicationSlot[^>]*supportedTypes="([^"]+)"[^>]*>\s*(?:<Variant[^>]*\/>\s*)?<DefaultProviderPolicy[^>]*defaultSystemProviderType="(\w+)"/g)) {
      assert.ok(m[1].split(' ').includes(m[2]), `${dir}: fallback ${m[2]} not in ${m[1]}`);
    }
    const ids = [...face.matchAll(/slotId="(\d+)"/g)].map((m) => m[1]);
    assert.ok(ids.length >= 1 && ids.length <= 8, `${dir} has ${ids.length} slots`);
    assert.equal(new Set(ids).size, ids.length);
    // Samsung's runtime clips what a slot draws to the slot's bounding shape,
    // so everything a complication draws has to lie inside it.
    for (const slot of face.matchAll(/<ComplicationSlot[^>]*>[\s\S]*?<\/ComplicationSlot>/g)) {
      const b = slot[0].match(/<Bounding(?:Box|Oval) x="([\d.]+)" y="([\d.]+)" width="([\d.]+)" height="([\d.]+)"/).slice(1).map(Number);
      for (const part of slot[0].matchAll(/<Part(?:Text|Draw|Image) x="([\d.]+)" y="([\d.]+)" width="([\d.]+)" height="([\d.]+)"/g)) {
        const [x, y, w, h] = part.slice(1).map(Number);
        assert.ok(x >= b[0] - 1 && y >= b[1] - 1 && x + w <= b[0] + b[2] + 1 && y + h <= b[1] + b[3] + 1,
          `${dir}: ${part[0]} outside its slot's bounds ${b}`);
      }
    }
    // A Template whose text has no placeholder renders as "Error" on the watch.
    for (const m of face.matchAll(/<Template>([^<]*)</g)) assert.match(m[1], /%[sd]/, `${dir}: template "${m[1]}"`);
    // Every font the face names is bundled with it.
    for (const fam of new Set([...face.matchAll(/family="([^"]+)"/g)].map((m) => m[1]))) {
      if (fam === 'SYNC_TO_DEVICE') continue;
      await readFile(new URL(`${dir}/src/main/res/font/${fam}.ttf`, root));
    }
  }
});
