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
export PATH="$JAVA_HOME/bin:$PATH"
# The faces ride along as separate assets; the button installs them too. A
# face that fails the official schema stops the whole release here.
faces=(watchface-row watchface watchface-hands watchface-mihrab watchface-custom)
for face in "${faces[@]}"; do
  scripts/validate-watchface.sh "$face/src/main/res/raw/watchface.xml"
done
./gradlew --quiet assembleRelease testReleaseUnitTest

count=$(git rev-list --count HEAD)
apk="app/build/outputs/apk/release/app-release.apk"
cp "$apk" "build-quran-watch8-v${count}.apk"
assets=("build-quran-watch8-v${count}.apk")
for face in "${faces[@]}"; do
  # face-<package>-v<count>.apk: SelfUpdater.facePackage reads the package from the name.
  name="face-com.quran.watch8.${face//-/.}-v${count}.apk"
  cp "$face/build/outputs/apk/release/$face-release.apk" "$name"
  assets+=("$name")
done

git push --quiet origin HEAD
gh release create "v${count}" "${assets[@]}" \
  --target "$(git rev-parse HEAD)" \
  --title "Watch v${count}" \
  --notes "$(git log -1 --format=%s)"
rm "${assets[@]}"
echo "Published v${count}."
