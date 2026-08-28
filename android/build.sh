#!/usr/bin/env bash
# Kiki — Copyright (C) 2026 kekeqwq
# SPDX-License-Identifier: GPL-3.0-or-later
# Bare Android build: javac + R8 + aapt2 + zipalign + apksigner. No Gradle.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
SDK="${ANDROID_SDK:-/tmp/android-sdk}"
BT="${ANDROID_BUILD_TOOLS:-$SDK/android-14}"
AJ="${ANDROID_JAR:-$SDK/android-34/android.jar}"
OUT="${1:-$ROOT/dist}"
WORK="${WORK:-$ROOT/.work}"

AAPT2="$BT/aapt2"
ZIPALIGN="$BT/zipalign"
APKSIGNER="$BT/apksigner"
R8_JAR="$BT/lib/d8.jar"

test -x "$AAPT2"
test -f "$AJ"
test -f "$R8_JAR"

rm -rf "$WORK"
mkdir -p "$WORK/flat" "$WORK/gen" "$WORK/classes" "$WORK/dex" "$OUT"

"$AAPT2" compile --dir "$ROOT/res" -o "$WORK/res.zip"
"$AAPT2" link \
  -o "$WORK/base.apk" \
  --manifest "$ROOT/AndroidManifest.xml" \
  -I "$AJ" \
  --java "$WORK/gen" \
  --custom-package io.github.kekeqwq.kiki \
  --min-sdk-version 24 \
  --target-sdk-version 34 \
  --version-code 7 \
  --version-name 1.6 \
  --auto-add-overlay \
  --no-proguard-location-reference \
  "$WORK/res.zip"

find "$ROOT/src" "$WORK/gen" -name '*.java' | sort > "$WORK/sources.txt"
javac \
  -encoding UTF-8 \
  -source 1.8 -target 1.8 \
  -bootclasspath "$AJ" \
  -classpath "$AJ" \
  -d "$WORK/classes" \
  @"$WORK/sources.txt"

# Class directory as R8 program input (smaller than listing every file).
jar --create --file "$WORK/program.jar" -C "$WORK/classes" .

java -cp "$R8_JAR" com.android.tools.r8.R8 \
  --release \
  --min-api 24 \
  --lib "$AJ" \
  --pg-conf "$ROOT/proguard.pro" \
  --output "$WORK/dex.zip" \
  --pg-map-output "$WORK/mapping.txt" \
  --no-data-resources \
  "$WORK/program.jar"

python3 - "$WORK/dex.zip" "$WORK/classes.dex" <<'PY'
import sys, zipfile
z = zipfile.ZipFile(sys.argv[1])
data = z.read("classes.dex")
open(sys.argv[2], "wb").write(data)
print("dex", len(data))
PY

python3 - "$WORK/base.apk" "$WORK/classes.dex" "$WORK/unsigned.apk" <<'PY'
import shutil, sys, zipfile
shutil.copyfile(sys.argv[1], sys.argv[3])
with zipfile.ZipFile(sys.argv[3], "a") as z:
    # Store uncompressed then let zipalign fix; DEFLATE is smaller.
    z.write(sys.argv[2], "classes.dex", compress_type=zipfile.ZIP_DEFLATED)
PY

"$ZIPALIGN" -f -p 4 "$WORK/unsigned.apk" "$WORK/aligned.apk"

KS="$ROOT/kiki.keystore"
if [ ! -f "$KS" ]; then
  keytool -genkeypair \
    -keystore "$KS" \
    -alias kiki \
    -storepass kiki-eink \
    -keypass kiki-eink \
    -dname "CN=kiki, O=kiki" \
    -keyalg RSA \
    -keysize 2048 \
    -validity 10000 \
    -deststoretype pkcs12
fi

"$APKSIGNER" sign \
  --ks "$KS" \
  --ks-key-alias kiki \
  --ks-pass pass:kiki-eink \
  --key-pass pass:kiki-eink \
  --min-sdk-version 24 \
  --v1-signing-enabled false \
  --v2-signing-enabled true \
  --v3-signing-enabled false \
  --out "$OUT/kiki.apk" \
  "$WORK/aligned.apk"

"$APKSIGNER" verify --min-sdk-version 24 "$OUT/kiki.apk"
python3 - "$OUT/kiki.apk" <<'PY'
import os, sys, zipfile
p = sys.argv[1]
n = os.path.getsize(p)
print("apk", n, "bytes", "%.1f KB" % (n / 1024.0))
with zipfile.ZipFile(p) as z:
    for i in z.infolist():
        print(" ", i.filename, i.file_size, "->", i.compress_size)
if n >= 50 * 1024:
    raise SystemExit("apk exceeds 50KB target")
PY
