echo "zygisk build start"

ndk-build

cp libs/arm64-v8a/libzygisk.so module/zygisk/arm64-v8a.so
cp libs/armeabi-v7a/libzygisk.so module/zygisk/armeabi-v7a.so
cp libs/x86_64/libzygisk.so module/zygisk/x86_64.so
cp libs/x86/libzygisk.so module/zygisk/x86.so

ls -hal module/zygisk

echo "zygisk build finish"