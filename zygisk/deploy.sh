echo clean
rm -rf module/module.prop
rm -rf module/aab.zip
rm -rf module/zygisk/*

sh zygisk-build.sh

cd module

cat << EOF > module.prop
id=Fukushima
name=Fukushima
version=$(date +%s)-alpha
versionCode=$(date +%s)
author=sfdex
description=A reactor locate in Fukushima
EOF

cat module.prop

# zip -r aab.zip . -x ".*,*.DS_Store,test.sh,README.md"
#zip -r aab.zip . -x@package-exclude.txt
zip -r aab.zip .
unzip -l aab.zip

adb push aab.zip /sdcard/

adb shell "su -c /data/adb/magisk/magisk --install-module /sdcard/aab.zip"
