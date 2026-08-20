#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
OUT_DIR="$ROOT_DIR/out"
VERSION="v1.0.0"

echo "=========================================="
echo "    转基因反应堆 (Reactor) 一键打包构建"
echo "    版本: $VERSION"
echo "=========================================="

mkdir -p "$OUT_DIR"

# 1. 编译 Android 控制端 APK (开启 R8 混淆压缩与资源缩减)
echo ""
echo ">> [1/3] 正在编译 Android 控制端 Release App (开启 R8 极速压缩)..."
cd "$ROOT_DIR"
./gradlew :app:assembleRelease

APK_SOURCE="$ROOT_DIR/app/build/outputs/apk/release/app-release.apk"
if [ ! -f "$APK_SOURCE" ]; then
    echo "错误: 未找到生成的 APK 文件: $APK_SOURCE" >&2
    exit 1
fi

# 复制一份独立 APK 到发布目录
cp "$APK_SOURCE" "$OUT_DIR/Reactor-${VERSION}.apk"
echo ">> 独立 APK 打包完成: $OUT_DIR/Reactor-${VERSION}.apk"

# 2. 准备打包 Zygisk C++ 模块并嵌入 APK
echo ""
echo ">> [2/3] 正在编译与打包 Zygisk Native 模块 (NDK)..."
cd "$ROOT_DIR/zygisk"

# 将 APK 放入 module 目录供 build.sh 打包
cp "$APK_SOURCE" "$ROOT_DIR/zygisk/module/reactor.apk"

./scripts/build.sh

# 清理 module 目录中的临时 apk
rm -f "$ROOT_DIR/zygisk/module/reactor.apk"

# 复制模块 ZIP 到发布目录
if [ -f "$ROOT_DIR/zygisk/out/Reactor-${VERSION}.zip" ]; then
    cp "$ROOT_DIR/zygisk/out/Reactor-${VERSION}.zip" "$OUT_DIR/Reactor-${VERSION}.zip"
fi

# 3. 产物汇总展示
echo ""
echo "=========================================="
echo "          构建与发布资产就绪！"
echo "=========================================="
echo "1. Magisk/KernelSU 刷机模块 (内置自动安装 APK):"
echo "   -> $OUT_DIR/Reactor-${VERSION}.zip"
echo ""
echo "2. 独立 Android 安装包 (供单独更新 UI):"
echo "   -> $OUT_DIR/Reactor-${VERSION}.apk"
echo "=========================================="
