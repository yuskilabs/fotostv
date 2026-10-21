#!/bin/bash
set -e
cd "$(dirname "$0")"
SDK=/usr/lib/android-sdk/platforms/android-23/android.jar
rm -rf build && mkdir -p build/gen build/classes
aapt package -f -m -J build/gen -M AndroidManifest.xml -S res -I $SDK -F build/app.unsigned.apk
javac -source 8 -target 8 -bootclasspath $SDK -encoding UTF-8 -nowarn -Xlint:-options \
  -d build/classes $(find src build/gen -name '*.java')
dalvik-exchange --dex --output=build/classes.dex build/classes
cd build && aapt add -f app.unsigned.apk classes.dex >/dev/null && cd ..
zipalign -f 4 build/app.unsigned.apk build/app.aligned.apk
KS=${KEYSTORE:-release.keystore}
KS_PASS=${KS_PASS:-album123}
[ -f "$KS" ] || keytool -genkeypair -keystore "$KS" -alias album -keyalg RSA -keysize 2048 \
  -validity 10000 -storepass "$KS_PASS" -keypass "$KS_PASS" -dname "CN=FotosTV" 2>/dev/null
apksigner sign --ks "$KS" --ks-pass pass:"$KS_PASS" --key-pass pass:"$KS_PASS" \
  --out FotosTV.apk build/app.aligned.apk
apksigner verify FotosTV.apk && ls -la FotosTV.apk
