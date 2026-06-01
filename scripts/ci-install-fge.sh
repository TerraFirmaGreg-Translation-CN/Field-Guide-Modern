#!/usr/bin/env bash
# Download field-guide-export release jar into modpack mods/.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
MP="${MODPACK_DIR:-$ROOT/Modpack-Modern}"
FGE_TAG="${FGE_TAG:-${FGE_VERSION:?FGE_VERSION or FGE_TAG required}}"
ver="${FGE_TAG#v}"
jar_name="field-guide-export-${ver}.jar"

cd "$ROOT"
rm -f field-guide-export-*.jar
gh release download "$FGE_TAG" \
  --repo jmecn/field-guide-export \
  --pattern "$jar_name" \
  --clobber

mkdir -p "$MP/mods"
find "$MP/mods" -maxdepth 1 -name 'field-guide-export*.jar' -delete
find "$MP/mods" -maxdepth 1 -name 'field-guide-forge*.jar' -delete
find "$MP/mods" -maxdepth 1 -name 'fieldguide*.jar' -delete

jar=$(ls field-guide-export-*.jar | head -1)
if [[ -z "$jar" ]]; then
  echo "::error::No field-guide-export jar"
  exit 1
fi
cp -v "$jar" "$MP/mods/"
