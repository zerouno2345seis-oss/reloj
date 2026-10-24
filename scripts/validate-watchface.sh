#!/usr/bin/env bash
# Checks watchface.xml against the official Watch Face Format schema before
# the face can be built. An invalid face runs inside the watch's own system
# UI, and one that broke it sent the watch into a reboot loop (2026-10-22).
set -euo pipefail

root="$(cd "$(dirname "$0")/.." && pwd)"
face="${1:-$root/watchface/src/main/res/raw/watchface.xml}"
version="${WFF_VERSION:-2}"
cache="${WFF_CACHE:-$HOME/.cache/reloj-wff}"
mkdir -p "$cache/jars"

if [ ! -d "$cache/watchface" ]; then
  git clone --quiet --depth 1 https://github.com/google/watchface.git "$cache/watchface"
fi
maven=https://repo1.maven.org/maven2
for jar in \
  xml-apis/xml-apis/1.4.01/xml-apis-1.4.01.jar \
  org/exist-db/thirdparty/xerces/xercesImpl/2.12.0/xercesImpl-2.12.0-xml-schema-1.1.jar \
  com/rackspace/eclipse/webtools/sourceediting/org.eclipse.wst.xml.xpath2.processor/2.1.100/org.eclipse.wst.xml.xpath2.processor-2.1.100.jar \
  edu/princeton/cup/java-cup/10k/java-cup-10k.jar \
  com/ibm/icu/icu4j/4.6/icu4j-4.6.jar; do
  [ -f "$cache/jars/$(basename "$jar")" ] || curl -sSfL -o "$cache/jars/$(basename "$jar")" "$maven/$jar"
done

cp="$(ls "$cache"/jars/*.jar | tr '\n' ':')"
javac -d "$cache" -cp "$cp" "$root/scripts/wff/ValidateWatchFace.java"
java -cp "$cp$cache" ValidateWatchFace \
  "$cache/watchface/third_party/wff/specification/documents/$version/watchface.xsd" "$face"
