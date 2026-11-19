#!/usr/bin/env bash
# Build an isolated test app with an ephemeral signing key; not a release artifact.
set -euo pipefail
work="${RUNNER_TEMP:?}/netopt-fixture"
mkdir -p "$work/classes" "$work/dex"
tools=$(find "$ANDROID_HOME/build-tools" -name apksigner -type f | sort -V | tail -1 | xargs dirname)
platform="$ANDROID_HOME/platforms/android-34/android.jar"
javac -source 8 -target 8 -bootclasspath "$platform" -d "$work/classes" scripts/fixtures/manual-app/MainActivity.java
"$tools/d8" --min-api 31 --output "$work/dex" "$work/classes/com/doxaiz/netoptfixture/MainActivity.class"
"$tools/aapt" package -f -M scripts/fixtures/manual-app/AndroidManifest.xml -I "$platform" -F "$work/unsigned.apk"
(cd "$work/dex" && zip -q "$work/unsigned.apk" classes.dex)
secret=$(openssl rand -hex 16)
keytool -genkeypair -keystore "$work/fixture.jks" -storepass "$secret" -keypass "$secret" -alias fixture -dname 'CN=Disposable Test' -keyalg RSA -keysize 2048 -validity 1 >/dev/null 2>&1
"$tools/apksigner" sign --ks "$work/fixture.jks" --ks-key-alias fixture --ks-pass "pass:$secret" --key-pass "pass:$secret" --out "$work/fixture.apk" "$work/unsigned.apk"
rm -f "$work/fixture.jks"
