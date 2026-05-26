#!/usr/bin/env bash
#
# Deploy legacy CLI-generated site to ./output
#
# Env:
#   SKIP_BUILD=1   skip Gradle when cli/build/libs/field-guide-tfg-*.jar already exists
#   SKIP_PAKKU=1   skip Modpack-Modern pakku fetch
#
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT"

find_cli_jar() {
  local -a jars=()
  shopt -s nullglob
  jars=( cli/build/libs/field-guide-tfg-*.jar cli/build/libs/field-guide-*.jar )
  shopt -u nullglob
  if ((${#jars[@]} == 0)); then
    return 1
  fi
  ls -t "${jars[@]}" | head -1
}

if [[ "${SKIP_PAKKU:-0}" != "1" ]]; then
  cd Modpack-Modern
  java -jar pakku.jar fetch || {
    echo "❌ pakku.jar Fetch failed"
    exit 1
  }
  cd "$ROOT"
else
  echo "SKIP_PAKKU=1 — using existing Modpack-Modern mods"
fi

CLI_JAR=""
if [[ "${SKIP_BUILD:-0}" == "1" ]]; then
  CLI_JAR="$(find_cli_jar || true)"
  if [[ -z "$CLI_JAR" ]]; then
    echo "❌ SKIP_BUILD=1 but no CLI jar under cli/build/libs/ (run: ./gradlew :cli:jar)"
    exit 1
  fi
  echo "SKIP_BUILD=1 — using $CLI_JAR"
else
  ./gradlew :cli:jar || {
    echo "❌ Gradle :cli:jar failed"
    exit 1
  }
  CLI_JAR="$(find_cli_jar || true)"
  if [[ -z "$CLI_JAR" ]]; then
    echo "❌ CLI jar missing after build (expected cli/build/libs/field-guide-tfg-*.jar)"
    exit 1
  fi
fi

rm -rf output
java -jar "$CLI_JAR" -i Modpack-Modern -o output

echo "✅ Build Success"
