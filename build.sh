#!/bin/bash
# 线稿生成 APK 手动构建脚本（无 Gradle 依赖）
set -e

SDK_BT=/root/android-sdk/android-14
PLATFORM=/root/android-sdk/platforms/android-34/android.jar
APP=/root/build/lineart/app
SRC=/root/build/lineart/src
OUT=/root/build/lineart/build
DIST=/root/build/lineart/dist

rm -rf "$OUT" "$DIST"
mkdir -p "$OUT/compiled-res" "$OUT/gen" "$OUT/obj" "$OUT/dex" "$DIST"

echo "[1/6] aapt2 compile 资源..."
"$SDK_BT/aapt2" compile --dir "$APP/res" -o "$OUT/res.zip"

echo "[2/6] aapt2 link 生成资源包与 R.java..."
"$SDK_BT/aapt2" link \
    -o "$OUT/base.apk" \
    --manifest "$APP/AndroidManifest.xml" \
    -I "$PLATFORM" \
    --min-sdk-version 24 \
    --target-sdk-version 34 \
    --version-code 1 \
    --version-name 1.0.0 \
    --java "$OUT/gen" \
    --auto-add-overlay \
    "$OUT/res.zip"

echo "[3/6] javac 编译 Java 源码..."
javac -source 1.8 -target 1.8 \
    -bootclasspath "$PLATFORM" \
    -d "$OUT/obj" \
    "$OUT/gen/com/lineart/gen/R.java" \
    "$SRC"/com/lineart/gen/*.java \
    "$SRC"/com/lineart/gen/core/*.java 2> "$OUT/javac.log" || { cat "$OUT/javac.log"; exit 1; }
grep -v "^Note:" "$OUT/javac.log" || true

echo "[4/6] d8 转 dex..."
java -cp "$SDK_BT/lib/d8.jar" com.android.tools.r8.D8 \
    --release \
    --lib "$PLATFORM" \
    --output "$OUT/dex" \
    $(find "$OUT/obj" -name "*.class")

echo "[5/6] 打包 APK..."
cp "$OUT/base.apk" "$OUT/unsigned.apk"
cd "$OUT/dex" && zip -q -j "$OUT/unsigned.apk" classes.dex && cd /root/build/lineart
"$SDK_BT/zipalign" -f 4 "$OUT/unsigned.apk" "$OUT/aligned.apk"

echo "[6/6] 签名..."
"$SDK_BT/apksigner" sign \
    --ks /root/build/lineart/release.keystore \
    --ks-key-alias lineart \
    --ks-pass pass:lineart2024 \
    --key-pass pass:lineart2024 \
    --out "$DIST/LineArt-Generator-v1.0.0.apk" \
    "$OUT/aligned.apk"

"$SDK_BT/apksigner" verify --print-certs "$DIST/LineArt-Generator-v1.0.0.apk" | head -5
echo ""
echo "构建完成: $DIST/LineArt-Generator-v1.0.0.apk"
ls -la "$DIST/"
