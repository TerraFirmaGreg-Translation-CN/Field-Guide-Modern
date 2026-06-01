#!/usr/bin/env bash
# Build static site from guide-export/ via :site jar; copy emi/ bundle beside output.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
EXPORT_GUIDE="${EXPORT_GUIDE:?EXPORT_GUIDE required}"
SITE_OUTPUT_DIR="${SITE_OUTPUT_DIR:?SITE_OUTPUT_DIR required}"
EXPORT_ROOT="${EXPORT_ROOT:?EXPORT_ROOT required}"

cd "$ROOT"
chmod +x gradlew
./gradlew :site:jar --no-daemon

SITE_JAR=$(ls -t site/build/libs/field-guide-site-*.jar 2>/dev/null | head -1)
if [[ -z "$SITE_JAR" ]]; then
  echo "::error::Site jar not found under site/build/libs/"
  exit 1
fi

rm -rf "$SITE_OUTPUT_DIR"
java -jar "$SITE_JAR" -e "$EXPORT_GUIDE" -o "$SITE_OUTPUT_DIR"

if [[ -d "${EXPORT_ROOT}/emi" ]]; then
  rm -rf "${SITE_OUTPUT_DIR}/emi"
  cp -a "${EXPORT_ROOT}/emi" "${SITE_OUTPUT_DIR}/emi"
  echo "Copied EMI bundle to ${SITE_OUTPUT_DIR}/emi"
else
  echo "::warning::No ${EXPORT_ROOT}/emi — recipe cards will not render"
fi
