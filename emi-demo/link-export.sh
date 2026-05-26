#!/usr/bin/env bash
# 将 guide-export 链接到 emi-demo/export（不复制，避免卡顿）
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
DEMO="$ROOT/emi-demo"
TARGET="${1:-$HOME/Downloads/guide-export}"

if [[ ! -f "$TARGET/generated/recipes/layouts-index.json" ]]; then
  echo "找不到 layouts-index: $TARGET/generated/recipes/layouts-index.json" >&2
  exit 1
fi

rm -f "$DEMO/export"
ln -sf "$TARGET" "$DEMO/export"
echo "已链接: emi-demo/export -> $TARGET"
echo "启动: cd $DEMO && python3 -m http.server 8765"
echo "打开: http://127.0.0.1:8765/"
