#!/usr/bin/env bash
# Post-export contract for guide-export/ + emi/ siblings.
set -euo pipefail

GUIDE="${EXPORT_GUIDE:?EXPORT_GUIDE required}"
EXPORT_ROOT="${EXPORT_ROOT:?EXPORT_ROOT required}"

for f in manifest.json meta.json; do
  if [[ ! -f "$GUIDE/$f" ]]; then
    echo "::error::Missing $GUIDE/$f"
    exit 1
  fi
done

exporter=$(python3 -c "import json; print(json.load(open('$GUIDE/manifest.json')).get('exporter',''))")
if [[ "$exporter" != "field-guide-export" ]]; then
  echo "::error::manifest.exporter must be field-guide-export (got: $exporter)"
  exit 1
fi

for d in assets data lang assets/icons; do
  if [[ ! -d "$GUIDE/$d" ]]; then
    echo "::error::Missing directory $GUIDE/$d"
    exit 1
  fi
done

if [[ -d "$GUIDE/emi" ]]; then
  echo "::error::guide-export must not contain emi/ (use $EXPORT_ROOT/emi)"
  exit 1
fi

if [[ ! -d "$EXPORT_ROOT/emi" ]]; then
  echo "::error::Missing EMI bundle at $EXPORT_ROOT/emi"
  exit 1
fi

if [[ ! -f "$EXPORT_ROOT/emi/bundle.json" ]]; then
  echo "::error::Missing $EXPORT_ROOT/emi/bundle.json"
  exit 1
fi

schema=$(python3 -c "import json; print(json.load(open('$EXPORT_ROOT/emi/bundle.json')).get('schema',0))")
if [[ "$schema" != "2" ]]; then
  echo "::error::emi/bundle.json schema must be 2 (got: $schema)"
  exit 1
fi

echo "guide-export OK: $GUIDE"
du -sh "$GUIDE" "$GUIDE/assets" "$GUIDE/data" "$GUIDE/lang" "$GUIDE/assets/icons" 2>/dev/null || true
du -sh "${EXPORT_ROOT}/emi" 2>/dev/null || true
