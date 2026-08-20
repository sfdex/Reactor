# Galaxy S26 Ultra for IT之家

这是一个面向 Android 15 的 Magisk Zygisk 模块。它只在 IT之家
`com.ruanmei.ithome` 主进程及其 `:*` 子进程中修改应用可见的产品身份，其他应用
和系统进程保持原样。

## 实现与维护文档

面向开发者的架构说明、源码导航和二次修改指南：

- [English implementation guide](docs/IMPLEMENTATION.md)
- [中文实现与维护指南](docs/IMPLEMENTATION.zh-CN.md)

## 修改内容

目标进程会看到：

| 字段 | 值 |
|---|---|
| `Build.MANUFACTURER` | `samsung` |
| `Build.BRAND` | `samsung` |
| `Build.MODEL` | `SM-S9480` |
| `Build.DEVICE` | `s26ultra` |
| `Build.PRODUCT` | `s26ultrachn` |

模块同时修改 `android.os.Build` 静态字段，并 Hook Android 15 的 Java 与 bionic
产品属性读取路径。它不会修改 Android 版本、SDK、系统指纹、序列号、IMEI、
Android ID、硬件能力或 Play Integrity 状态，也不包含 Magisk 隐藏或检测绕过功能。

## 要求

- Android 15（API 35）
- Magisk 27.0 或更高版本
- 已在 Magisk 设置中启用 Zygisk
- ARM64 或 ARM32 应用进程

## 构建

本机默认使用 Android NDK r28.2，也可以显式指定 NDK r27 或更高版本：

```bash
ANDROID_NDK_ROOT=/path/to/android-ndk ./scripts/build.sh
```

构建过程会先执行全部主机测试，再编译两个 ABI 并验证 ZIP。输出文件为：

```text
out/s26spoof-v1.0.0.zip
```

## 安装与验证

1. 打开 Magisk，确认 Zygisk 已启用。
2. 在“模块”页面选择“从本地安装”，选择 `s26spoof-v1.0.0.zip`。
3. 重启手机。
4. 清理旧日志并重新启动 IT之家：

```bash
adb logcat -c
adb shell am force-stop com.ruanmei.ithome
adb shell monkey -p com.ruanmei.ithome 1
adb logcat -d -s S26Spoof
```

完整成功日志应包含 `java=1 native=1 native_get=1 native_read_callback=1 fields=5/5`
以及五个目标身份值。再用一个非目标设备信息应用确认它仍显示手机的真实型号。

## 停用

在 Magisk 的模块页面停用或删除本模块并重启。模块不写入全局系统属性，停用后不
会留下持久化的机型修改。

## 已知限制

Zygisk 的 PLT Hook 只覆盖进程专门化阶段已经加载的 ELF 调用方。应用稍后动态加载
并直接调用 bionic 属性 API 的原生库可能绕过原生属性 Hook；常规 Java 属性读取与
`android.os.Build` 字段仍由另外两层处理。`Build.DEVICE` 和 `Build.PRODUCT` 使用
项目内的一致值，因为三星公开支持资料没有披露国行 S26 Ultra 的这两个生产字段。
