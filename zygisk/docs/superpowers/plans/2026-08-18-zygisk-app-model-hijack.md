# Zygisk 动态机型伪装系统与控制 App (GMBioreactor) 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 构建一个基于 Zygisk 的动态设备型号伪装系统，并开发配套的 Material 3 + Navigation 3 Android 控制应用 (`GMBioreactor`)，实现从 `MobileModels` 数据集选型、自定义机型、为指定 App 独立绑定机型并通过 Root 动态通知 Zygisk 模块 Hook 生效。

**Architecture:** 
- **Zygisk 模块 (`ZyModule`)**：在 `preAppSpecialize` 通过 `api->connectCompanion()` 向 Root 伴侣守护进程查询当前进程（及 `pkg:*` 子进程）绑定的机型配置。命中则动态安装 Java `SystemProperties.native_get` Hook、Native bionic PLT Hook，并在 `postAppSpecialize` 动态注入 `Build` 5 项静态字段；未命中则调用 `DLCLOSE_MODULE_LIBRARY` 自动卸载。
- **控制 App (`GMBioreactor`)**：基于 Jetpack Compose + Navigation 3 构建，内置 `MobileModels` 常用机型数据库，提供应用管理、机型预设库、应用机型绑定选择器与设置页。通过 Root (`su`) 将配置原子写入 `/data/adb/gmbioreactor/config.json` 并支持一键 `am force-stop` 立即生效。

**Tech Stack:** C++17, Android NDK r28, Zygisk API v5, Kotlin, Jetpack Compose, Material 3, Navigation 3, Root Shell (`su`).

## Global Constraints

- 5 项核心机型标识：`MANUFACTURER`, `BRAND`, `MODEL`, `DEVICE`, `PRODUCT`。
- 覆盖 6 组属性前缀：`ro.product.`, `ro.product.system.`, `ro.product.vendor.`, `ro.product.product.`, `ro.product.odm.`, `ro.product.system_ext.`。
- 配置文件标准路径：`/data/adb/gmbioreactor/config.json`。
- 非目标进程与 `system_server` 严格执行 `DLCLOSE_MODULE_LIBRARY` 卸载，保持系统原生性能与隔离。

---

## File Structure

```text
ZyModule/
├── module/
│   ├── module.prop
│   └── jni/
│       ├── Android.mk
│       ├── Application.mk
│       ├── zygisk.hpp
│       ├── include/s26spoof/
│       │   ├── profile.hpp            (NEW: DeviceProfile 结构体与常量)
│       │   ├── config_parser.hpp      (NEW: 轻量级 JSON 配置解析器)
│       │   ├── companion.hpp          (NEW: Root 伴侣进程与 Socket 协议)
│       │   ├── core.hpp               (MOD: 动态 profile 属性查找)
│       │   ├── build_fields.hpp       (MOD: 动态 JNI Build 字段写入)
│       │   ├── property_hooks.hpp     (MOD: 动态属性 Hook 包装)
│       │   ├── native_hooks.hpp       (不变: PLT Hook 注册)
│       │   └── maps.hpp               (不变: /proc/self/maps 解析)
│       ├── config_parser.cpp          (NEW: JSON 解析实现)
│       ├── companion.cpp              (NEW: 伴侣进程 IPC 处理与缓存)
│       ├── core.cpp                   (MOD: 动态 profile 属性映射)
│       ├── build_fields.cpp           (MOD: 动态 JNI 写入)
│       ├── property_hooks.cpp         (MOD: 动态属性拦截)
│       ├── native_hooks.cpp           (不变: bionic PLT 拦截)
│       ├── maps.cpp                   (不变)
│       └── module.cpp                 (MOD: 伴侣连接与生命周期协调)
└── tests/native/
    ├── config_parser_test.cpp         (NEW: JSON 解析测试)
    ├── companion_test.cpp             (NEW: 伴侣 IPC 协议与匹配测试)
    ├── core_test.cpp                  (MOD: 动态属性查找测试)
    ├── build_fields_test.cpp          (MOD: 动态字段写入测试)
    └── ...

GMBioreactor/
└── app/
    ├── build.gradle.kts               (MOD: 引入 Navigation 3 等依赖)
    └── src/main/
        ├── assets/
        │   └── models.json            (NEW: MobileModels 结构化数据集)
        ├── AndroidManifest.xml        (MOD: 声明权限与主 Activity)
        └── java/com/sfdex/gmbioreactor/
            ├── data/
            │   ├── model/
            │   │   ├── DeviceProfile.kt       (NEW: 机型数据结构)
            │   │   ├── AppSpoofConfig.kt      (NEW: 应用配置数据结构)
            │   │   └── BrandModels.kt         (NEW: 品牌系列分类模型)
            │   ├── repository/
            │   │   ├── ModelRepository.kt     (NEW: 机型库与自定义预设存储)
            │   │   ├── AppListRepository.kt   (NEW: 已安装应用列表扫描)
            │   │   └── ConfigRepository.kt    (NEW: JSON 配置转换与本地缓存)
            │   └── root/
            │       └── RootEngine.kt          (NEW: su 管道执行、配置原子写入与 force-stop)
            ├── ui/
            │   ├── theme/                     (现有: Color, Theme, Type)
            │   ├── navigation/
            │   │   ├── AppRoute.kt            (NEW: Navigation 3 路由定义)
            │   │   └── AppNavigation.kt       (NEW: Navigation 3 导航容器)
            │   ├── viewmodel/
            │   │   ├── AppListViewModel.kt    (NEW: 主页应用列表与状态)
            │   │   └── ModelLibraryViewModel.kt (NEW: 机型库与编辑状态)
            │   └── screens/
            │       ├── AppListScreen.kt       (NEW: 应用列表主界面与状态卡片)
            │       ├── ModelLibraryScreen.kt  (NEW: 机型库浏览与管理)
            │       ├── ModelPickerScreen.kt   (NEW: 为指定 App 选型/微调)
            │       └── SettingsScreen.kt      (NEW: 设置与诊断说明)
            └── MainActivity.kt                (MOD: 承载 AppNavigation)
```

---

## Tasks

### Task 1: Zygisk C++ 动态配置结构与 JSON 解析器

**Files:**
- Create: `ZyModule/module/jni/include/s26spoof/profile.hpp`
- Create: `ZyModule/module/jni/include/s26spoof/config_parser.hpp`
- Create: `ZyModule/module/jni/config_parser.cpp`
- Create: `ZyModule/tests/native/config_parser_test.cpp`
- Modify: `ZyModule/scripts/test-host.sh:10-18`

**Interfaces:**
- Produces: `struct DeviceProfile`, `struct AppConfigEntry`, `bool parse_config_json(const char *json, std::unordered_map<std::string, AppConfigEntry> *out_packages)`

- [ ] **Step 1: Write failing test for JSON config parsing**
- [ ] **Step 2: Run test to verify it fails**
- [ ] **Step 3: Implement DeviceProfile and zero-dependency JSON parser**
- [ ] **Step 4: Run test to verify it passes**
- [ ] **Step 5: Commit**

---

### Task 2: 重构 Zygisk 核心拦截层以支持动态 Profile

**Files:**
- Modify: `ZyModule/module/jni/include/s26spoof/core.hpp`
- Modify: `ZyModule/module/jni/core.cpp`
- Modify: `ZyModule/module/jni/include/s26spoof/build_fields.hpp`
- Modify: `ZyModule/module/jni/build_fields.cpp`
- Modify: `ZyModule/module/jni/include/s26spoof/property_hooks.hpp`
- Modify: `ZyModule/module/jni/property_hooks.cpp`
- Modify: `ZyModule/tests/native/core_test.cpp`
- Modify: `ZyModule/tests/native/build_fields_test.cpp`
- Modify: `ZyModule/tests/native/property_hooks_test.cpp`
- Modify: `ZyModule/tests/native/java_property_hook_test.cpp`

**Interfaces:**
- Produces: `const char *find_property_override(const DeviceProfile &profile, const char *key)`
- Produces: `BuildWriteResult write_build_fields(JNIEnv *env, const DeviceProfile &profile)`
- Produces: `void set_active_profile(const DeviceProfile *profile)`

- [ ] **Step 1: Update unit tests with dynamic profile inputs**
- [ ] **Step 2: Run tests to verify compilation/test failure**
- [ ] **Step 3: Refactor core.cpp, build_fields.cpp, and property_hooks.cpp**
- [ ] **Step 4: Run all host tests to verify they pass**
- [ ] **Step 5: Commit**

---

### Task 3: 实现 Root 伴侣守护进程 (`companion.cpp`) 与 Zygisk 模块生命周期

**Files:**
- Create: `ZyModule/module/jni/include/s26spoof/companion.hpp`
- Create: `ZyModule/module/jni/companion.cpp`
- Modify: `ZyModule/module/jni/module.cpp`
- Modify: `ZyModule/module/jni/Android.mk`
- Create: `ZyModule/tests/native/companion_test.cpp`

**Interfaces:**
- Produces: `void companion_handler(int socket_fd)`
- Produces: `bool query_process_profile(zygisk::Api *api, const char *process_name, DeviceProfile *out_profile)`

- [ ] **Step 1: Write companion IPC protocol test**
- [ ] **Step 2: Run test to verify it fails**
- [ ] **Step 3: Implement companion_handler and module.cpp Zygisk integration**
- [ ] **Step 4: Run all host tests and build module ZIP**
- [ ] **Step 5: Commit**

---

### Task 4: GMBioreactor 依赖配置与 MobileModels 数据集构建

**Files:**
- Modify: `GMBioreactor/gradle/libs.versions.toml`
- Modify: `GMBioreactor/app/build.gradle.kts`
- Create: `GMBioreactor/app/src/main/assets/models.json`
- Create: `GMBioreactor/app/src/main/java/com/sfdex/gmbioreactor/data/model/DeviceProfile.kt`
- Create: `GMBioreactor/app/src/main/java/com/sfdex/gmbioreactor/data/model/AppSpoofConfig.kt`
- Create: `GMBioreactor/app/src/main/java/com/sfdex/gmbioreactor/data/model/BrandModels.kt`
- Create: `GMBioreactor/app/src/main/java/com/sfdex/gmbioreactor/data/repository/ModelRepository.kt`

**Interfaces:**
- Produces: `data class DeviceProfile(val name: String, val manufacturer: String, val brand: String, val model: String, val device: String, val product: String)`
- Produces: `class ModelRepository(context: Context)`

- [ ] **Step 1: Configure gradle dependencies and build assets**
- [ ] **Step 2: Implement DeviceProfile models and ModelRepository**
- [ ] **Step 3: Test repository model loading**
- [ ] **Step 4: Commit**

---

### Task 5: Root 执行引擎与配置管理器 (`RootEngine` & `ConfigRepository`)

**Files:**
- Create: `GMBioreactor/app/src/main/java/com/sfdex/gmbioreactor/data/root/RootEngine.kt`
- Create: `GMBioreactor/app/src/main/java/com/sfdex/gmbioreactor/data/repository/ConfigRepository.kt`
- Create: `GMBioreactor/app/src/main/java/com/sfdex/gmbioreactor/data/repository/AppListRepository.kt`

**Interfaces:**
- Produces: `RootEngine.isRootAvailable(): Boolean`, `RootEngine.writeConfigFile(jsonContent: String): Boolean`, `RootEngine.forceStopApp(packageName: String): Boolean`
- Produces: `ConfigRepository.loadConfig()`, `ConfigRepository.saveConfig()`
- Produces: `AppListRepository.getInstalledApps()`

- [ ] **Step 1: Implement RootEngine with safe su shell execution and atomic file write**
- [ ] **Step 2: Implement ConfigRepository and AppListRepository**
- [ ] **Step 3: Unit test JSON config serialization matches Zygisk C++ parser schema**
- [ ] **Step 4: Commit**

---

### Task 6: ViewModels 与 Navigation 3 声明式导航框架

**Files:**
- Create: `GMBioreactor/app/src/main/java/com/sfdex/gmbioreactor/ui/navigation/AppRoute.kt`
- Create: `GMBioreactor/app/src/main/java/com/sfdex/gmbioreactor/ui/navigation/AppNavigation.kt`
- Create: `GMBioreactor/app/src/main/java/com/sfdex/gmbioreactor/ui/viewmodel/AppListViewModel.kt`
- Create: `GMBioreactor/app/src/main/java/com/sfdex/gmbioreactor/ui/viewmodel/ModelLibraryViewModel.kt`

**Interfaces:**
- Produces: Navigation 3 Routes: `AppListRoute`, `ModelLibraryRoute`, `ModelPickerRoute(packageName: String)`, `SettingsRoute`
- Produces: `AppListViewModel` and `ModelLibraryViewModel`

- [ ] **Step 1: Define AppRoute and ViewModels**
- [ ] **Step 2: Set up Navigation 3 navigation host in AppNavigation.kt**
- [ ] **Step 3: Verify ViewModel state transitions**
- [ ] **Step 4: Commit**

---

### Task 7: UI 界面实现 (AppListScreen, ModelLibraryScreen, ModelPickerScreen, SettingsScreen)

**Files:**
- Create: `GMBioreactor/app/src/main/java/com/sfdex/gmbioreactor/ui/screens/AppListScreen.kt`
- Create: `GMBioreactor/app/src/main/java/com/sfdex/gmbioreactor/ui/screens/ModelLibraryScreen.kt`
- Create: `GMBioreactor/app/src/main/java/com/sfdex/gmbioreactor/ui/screens/ModelPickerScreen.kt`
- Create: `GMBioreactor/app/src/main/java/com/sfdex/gmbioreactor/ui/screens/SettingsScreen.kt`
- Modify: `GMBioreactor/app/src/main/java/com/sfdex/gmbioreactor/MainActivity.kt`

**Interfaces:**
- Produces: Modern Material 3 UI screens

- [ ] **Step 1: Implement AppListScreen**
- [ ] **Step 2: Implement ModelLibraryScreen & Custom Model Dialog**
- [ ] **Step 3: Implement ModelPickerScreen**
- [ ] **Step 4: Implement SettingsScreen and update MainActivity**
- [ ] **Step 5: Build and compile Android App**
- [ ] **Step 6: Commit**

---

### Task 8: 全链路联调与验证

**Files:**
- Verify: `ZyModule/scripts/test-host.sh all`
- Verify: `ZyModule/scripts/build.sh`
- Verify: `GMBioreactor/` APK compilation
- Create/Update: `walkthrough.md`

- [ ] **Step 1: Run all ZyModule host test suites**
- [ ] **Step 2: Build Zygisk module release ZIP**
- [ ] **Step 3: Build GMBioreactor APK**
- [ ] **Step 4: Commit and finalize walkthrough**
