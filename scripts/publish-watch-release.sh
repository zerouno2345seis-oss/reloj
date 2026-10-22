#!/usr/bin/env bash
# Builds the release APK and publishes it as a GitHub release, where the
# watch's "تحقّق من التحديث" button (util/SelfUpdater.kt) picks it up.
#
# Run on the machine whose ~/.android/debug.keystore signed the build already
# on the watch: Android only installs an update signed with the same key.
# Needs: JDK 17, Android SDK, and `gh` signed in with push access.
set -euo pipefail
cd "$(dirname "$0")/.."

export JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk@17}"
./gradlew --quiet assembleRelease testReleaseUnitTest

count=$(git rev-list --count HEAD)
apk="app/build/outputs/apk/release/app-release.apk"
cp "$apk" "build-quran-watch8-v${count}.apk"

git push --quiet origin HEAD
gh release create "v${count}" "build-quran-watch8-v${count}.apk" \
  --target "$(git rev-parse HEAD)" \
  --title "Watch v${count}" \
  --notes "$(git log -1 --format=%s)"
rm "build-quran-watch8-v${count}.apk"
echo "Published v${count}."
