#!/bin/bash

# ==============================================================================
# 极简 Android 编译脚本 (No-Gradle / Pure Shell)
# 适用环境: Linux / macOS
# 依赖: Android SDK Command-line Tools (build-tools, platforms)
# ==============================================================================

# ---------------- 配置区域 (请根据实际情况修改) ----------------
# Android SDK 根目录 (常见路径: $HOME/Android/Sdk 或 /usr/lib/android-sdk)
SDK_DIR="$HOME/Android/Sdk"

# 编译使用的 Build-Tools 版本 (请去 $SDK_DIR/build-tools/ 目录下查看具体版本号)
BUILD_TOOLS_VERSION="36.1.0"

# 目标 Android 平台版本 (建议选择 android-30 或更高)
PLATFORM_VERSION="android-34"

# 项目配置
APP_NAME="DenmanTurn"
PACKAGE_NAME="com.denman.turn"
SRC_PATH="src"
BUILD_DIR="build"
DIST_DIR="dist"

# ---------------- 自动探测路径 (无需修改) ----------------
AAPT2="$SDK_DIR/build-tools/$BUILD_TOOLS_VERSION/aapt2"
D8="$SDK_DIR/build-tools/$BUILD_TOOLS_VERSION/d8"
ZIPALIGN="$SDK_DIR/build-tools/$BUILD_TOOLS_VERSION/zipalign"
APKSIGNER="$SDK_DIR/build-tools/$BUILD_TOOLS_VERSION/apksigner"
ANDROID_JAR="$SDK_DIR/platforms/$PLATFORM_VERSION/android.jar"

# ---------------- 错误处理与环境检查 ----------------
set -e # 遇到错误立即停止

echo "Checking environment..."

if [ ! -d "$SDK_DIR" ]; then
    echo "Error: Android SDK not found at $SDK_DIR"
    echo "Please update the SDK_DIR variable in the script."
    exit 1
fi

if [ ! -f "$ANDROID_JAR" ]; then
    echo "Error: Platform $PLATFORM_VERSION not found."
    echo "Current setting is: $PLATFORM_VERSION"
    echo "Please install it via sdkmanager or update PLATFORM_VERSION."
    exit 1
fi

# ---------------- 清理与初始化 ----------------
echo "Cleaning build directory..."
rm -rf "$BUILD_DIR" "$DIST_DIR"
mkdir -p "$BUILD_DIR/gen" "$BUILD_DIR/obj" "$BUILD_DIR/apk" "$DIST_DIR"

# ---------------- 第一步: 编译资源与清单 (AAPT2) ----------------
echo "1. Compiling resources and Manifest..."

# 由于我们没有 res 目录 (极简模式)，我们直接 Link 清单文件
# 这步会生成一个空的 APK 外壳 (只包含 AndroidManifest.xml)
$AAPT2 link \
    -o "$BUILD_DIR/unaligned.apk" \
    -I "$ANDROID_JAR" \
    --manifest AndroidManifest.xml \
    --java "$BUILD_DIR/gen" \
    --auto-add-overlay

# ---------------- 第二步: 编译 Java 代码 (Javac) ----------------
echo "2. Compiling Java sources..."

# 获取所有 .java 文件列表
JAVA_SOURCES=$(find "$SRC_PATH" -name "*.java")

if [ -z "$JAVA_SOURCES" ]; then
    echo "Error: No Java source files found in $SRC_PATH"
    exit 1
fi

# 编译 Java 到 .class
# 注意：即使没有 R.java，我们也包含 build/gen 路径以防万一
javac -source 1.8 -target 1.8 \
    -d "$BUILD_DIR/obj" \
    -cp "$ANDROID_JAR" \
    $JAVA_SOURCES

# ---------------- 第三步: 字节码转换 (D8 Dexing) ----------------
echo "3. Converting to Dalvik bytecode (DEX)..."

# 将 .class 文件打包成 classes.dex
# --lib 参数是必须的，否则找不到 Android 系统类
$D8 --output "$BUILD_DIR/apk" \
    --lib "$ANDROID_JAR" \
    $(find "$BUILD_DIR/obj" -name "*.class")

# ---------------- 第四步: 打包 APK ----------------
echo "4. Packaging APK..."

# 将生成的 classes.dex 放入之前 AAPT2 生成的 APK 外壳中
# -j: 不保留目录结构 (junk paths)，直接把文件放在根目录
zip -j "$BUILD_DIR/unaligned.apk" "$BUILD_DIR/apk/classes.dex"

# ---------------- 第五步: 优化对齐 (ZipAlign) ----------------
echo "5. Aligning APK..."

$ZIPALIGN -f -p 4 \
    "$BUILD_DIR/unaligned.apk" \
    "$BUILD_DIR/aligned.apk"

# ---------------- 第六步: 签名 (ApkSigner) ----------------
echo "6. Signing APK..."

KEYSTORE="debug.keystore"
KEY_ALIAS="androiddebugkey"
KEY_PASS="pass:android"

# 如果没有签名文件，自动生成一个 Debug 签名
if [ ! -f "$KEYSTORE" ]; then
    echo "Generating debug keystore..."
    keytool -genkeypair -v \
        -keystore "$KEYSTORE" \
        -alias "$KEY_ALIAS" \
        -keyalg RSA \
        -keysize 2048 \
        -validity 10000 \
        -storepass "android" \
        -keypass "android" \
        -dname "CN=Android Debug,O=Android,C=US"
fi

# 签名
$APKSIGNER sign --ks "$KEYSTORE" \
    --ks-key-alias "$KEY_ALIAS" \
    --ks-pass "$KEY_PASS" \
    --out "$DIST_DIR/$APP_NAME.apk" \
    "$BUILD_DIR/aligned.apk"

# ---------------- 完成 ----------------
echo "=========================================="
echo "Build Success!"
echo "APK Location: $DIST_DIR/$APP_NAME.apk"
echo "To install: adb install -r $DIST_DIR/$APP_NAME.apk"
echo "=========================================="
