#!/usr/bin/env bash
# Builds Mysten's Move trace debug adapter from the sui repository and bundles it into
# src/main/resources/trace-adapter/server.js. Requires git, node and npm.
set -euo pipefail
cd "$(dirname "$0")"
SUI_COMMIT="${SUI_COMMIT:-9948bc0d23ca89c0fe00ad7448071713ff14860d}"
WORK="$(mktemp -d)"
git clone -q --filter=blob:none --sparse --depth 1 https://github.com/MystenLabs/sui.git "$WORK/sui"
git -C "$WORK/sui" fetch -q --depth 1 origin "$SUI_COMMIT" && git -C "$WORK/sui" checkout -q "$SUI_COMMIT"
git -C "$WORK/sui" sparse-checkout set external-crates/move/crates/move-analyzer/trace-adapter
ADAPTER="$WORK/sui/external-crates/move/crates/move-analyzer/trace-adapter"
(cd "$ADAPTER" && npm install --no-audit --no-fund && npx tsc -p .)
npm install --no-audit --no-fund
npx esbuild "$ADAPTER/out/server.js" --bundle --platform=node --target=node18 --minify-whitespace --legal-comments=none \
  --outfile=../src/main/resources/trace-adapter/server.js
echo "$SUI_COMMIT" > ../src/main/resources/trace-adapter/SUI_COMMIT
rm -rf "$WORK"
echo "bundled trace adapter from sui@$SUI_COMMIT"
