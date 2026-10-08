#!/usr/bin/env bash
# Fails if the release build pulls in Google Play services, Firebase or ML Kit, directly or transitively,
# or if the built APK declares their permissions/components. Usage: scripts/check-no-google.sh [apk]
set -euo pipefail

cd "$(dirname "$0")/.."
apk="${1:-app/build/outputs/apk/debug/app-debug.apk}"
pattern='com\.google\.android\.gms|com\.google\.android\.play|com\.google\.firebase|com\.google\.mlkit|play-services|firebase|c2dm'

echo "Checking the dependency graph..."
deps=$(./gradlew -q :app:dependencies --configuration releaseRuntimeClasspath)
if echo "$deps" | grep -E -i "$pattern"; then
  echo "Google dependency found in releaseRuntimeClasspath" >&2
  exit 1
fi

echo "Checking the built APK manifest: $apk"
aapt2=$(ls "$ANDROID_HOME"/build-tools/*/aapt2 | sort -V | tail -1)
manifest=$("$aapt2" dump xmltree --file AndroidManifest.xml "$apk")
if echo "$manifest" | grep -E -i "$pattern"; then
  echo "Google component or permission found in the APK manifest" >&2
  exit 1
fi

echo "No Google dependencies."
