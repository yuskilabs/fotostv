#!/bin/bash
# Pruebas automáticas de la lógica: ./tools/test/run.sh  (no hace falta tele ni móvil)
set -e
cd "$(dirname "$0")/../.."
SDK=/usr/lib/android-sdk/platforms/android-23/android.jar
OUT=$(mktemp -d)
mkdir -p "$OUT/gen" "$OUT/cl"
aapt package -f -m -J "$OUT/gen" -M AndroidManifest.xml -S res -A appassets -I "$SDK" -F "$OUT/x.apk"
javac -source 8 -target 8 -encoding UTF-8 -nowarn -Xlint:-options -cp "$SDK" -sourcepath "src:$OUT/gen" \
  -d "$OUT/cl" tools/test/Tests.java 2>&1 | grep -v "^Picked\|^Note" || true
java -cp "$OUT/cl:$SDK" com.yuskilabs.fotostv.Tests 2>&1 | grep -v "^Picked"
RC=${PIPESTATUS[0]}
rm -rf "$OUT"
exit $RC
