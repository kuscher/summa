#!/usr/bin/env bash
# SPDX-License-Identifier: MIT
# Builds a signed release and (with --publish) creates the GitHub release.
#   tools/release.sh            build + verify into executables/release-<version>/
#   tools/release.sh --publish  … and publish it as v<version> with docs/release-notes/<version>.md
set -euo pipefail
cd "$(dirname "$(readlink -f "$0")")/.."
VERSION=$(sed -n 's/.*versionName = "\(.*\)".*/\1/p' app/build.gradle.kts)
NOTES=docs/release-notes/$VERSION.md
[ -f "$NOTES" ] || { echo "missing $NOTES"; exit 1; }
[ -f ~/.config/summa/keystore.jks ] || { echo "no release key in ~/.config/summa"; exit 1; }
./gradlew :engine:test :app:lintRelease :app:assembleRelease --console=plain -q
OUT=executables/release-$VERSION
mkdir -p "$OUT"
cp app/build/outputs/apk/release/app-release.apk "$OUT/Summa.apk"
cp "$OUT/Summa.apk" "$OUT/Summa-$VERSION.apk"
BT=$(ls -d "$ANDROID_HOME"/build-tools/*/ | sort -V | tail -1)
CERT=$("$BT/apksigner" verify --print-certs "$OUT/Summa.apk" | sed -n 's/.*certificate SHA-256 digest: //p' | head -1)
EXPECT=1deaba8d673fb4f100c3d97478d22eee2550cde54dc26b6599c3f34b5ff5790f
[ "$CERT" = "$EXPECT" ] || { echo "wrong signing certificate: $CERT"; exit 1; }
(cd "$OUT" && sha256sum Summa.apk "Summa-$VERSION.apk" > SHA256SUMS)
ls -la "$OUT"; cat "$OUT/SHA256SUMS"
if [ "${1:-}" = --publish ]; then
  git diff --quiet && git diff --cached --quiet || { echo "commit first"; exit 1; }
  git push -q origin HEAD
  gh release create "v$VERSION" "$OUT/Summa.apk" "$OUT/Summa-$VERSION.apk" "$OUT/SHA256SUMS" \
    --title "Summa $VERSION" --notes-file "$NOTES" --target "$(git rev-parse HEAD)"
fi
