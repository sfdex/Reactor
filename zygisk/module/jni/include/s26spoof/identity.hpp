#pragma once

namespace s26spoof {

struct BuildField {
    const char *name;
    const char *value;
};

inline constexpr char kPackageName[] = "com.ruanmei.ithome";
inline constexpr char kManufacturer[] = "samsung";
inline constexpr char kBrand[] = "samsung";
inline constexpr char kModel[] = "SM-S9480";
inline constexpr char kDevice[] = "s26ultra";
inline constexpr char kProduct[] = "s26ultrachn";

inline constexpr BuildField kBuildFields[] = {
    {"MANUFACTURER", kManufacturer},
    {"BRAND", kBrand},
    {"MODEL", kModel},
    {"DEVICE", kDevice},
    {"PRODUCT", kProduct},
};

}  // namespace s26spoof
