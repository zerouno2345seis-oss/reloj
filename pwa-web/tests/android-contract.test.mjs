import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';

const root = new URL('../../', import.meta.url);
const [home, tileConfig, settings, reader, preferences, sync, mainActivity, qibla] = await Promise.all([
  readFile(new URL('app/src/main/java/com/quran/watch8/ui/screens/HomeScreen.kt', root), 'utf8'),
  readFile(new URL('app/src/main/java/com/quran/watch8/data/model/TileConfig.kt', root), 'utf8'),
  readFile(new URL('app/src/main/java/com/quran/watch8/ui/screens/SettingsScreen.kt', root), 'utf8'),
  readFile(new URL('app/src/main/java/com/quran/watch8/ui/screens/QuranReaderScreen.kt', root), 'utf8'),
  readFile(new URL('app/src/main/java/com/quran/watch8/data/repository/PreferencesRepository.kt', root), 'utf8'),
  readFile(new URL('app/src/main/java/com/quran/watch8/util/LocalSyncServer.kt', root), 'utf8'),
  readFile(new URL('app/src/main/java/com/quran/watch8/MainActivity.kt', root), 'utf8'),
  readFile(new URL('app/src/main/java/com/quran/watch8/ui/screens/QiblaCompassScreen.kt', root), 'utf8'),
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
  assert.match(home, /pList\.map\s*\{\s*it\.first\s*\}/);
  assert.match(home, /pList\.map\s*\{\s*it\.second\s*\}/);
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
  assert.match(home, /appearance\.tileShape\s*==\s*"mixed"/);
  assert.match(home, /index\s*%\s*3/);
});

test('qibla action opens a real sensor-backed compass screen', () => {
  assert.match(mainActivity, /composable\("qibla"\)/);
  assert.match(qibla, /TYPE_ROTATION_VECTOR/);
  assert.match(qibla, /qiblaBearing/);
  assert.match(qibla, /KAABA_LATITUDE/);
});

test('settings can save the current watch face as a reusable preset', () => {
  assert.match(settings, /saveCustomPreset/);
  assert.match(settings, /tileConfig/);
  assert.match(settings, /حفظ الواجهة الحالية كقالب/);
});
