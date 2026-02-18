# Amap Redirect (高德地图重定向)

## 背景
我在 AOSP 风格系统里用 Gemini 助手时发现它不原生支持唤醒高德地图, 语音导航常常会跑去 Google Maps. 所以做了这个模块: 拦截 Google Maps 的导航 Intent, 直接跳到高德, 让国内导航更顺手.

## 功能
- 拦截 Google Maps 的 `geo:` 和 `google.navigation:` Intent
- 解析目的地名称, 解析失败时用坐标反查
- 可选导航模式: 驾车/公交/步行/骑行
- 启动高德后自动结束 Google Maps

## 运行环境
- Android 8.0+ (minSdk 26)
- LSPosed/Xposed 框架
- 已安装 Google Maps
- 已安装高德地图

## 安装与使用
1. 安装 APK
2. 在 LSPosed 中启用模块, 作用域勾选 `com.google.android.apps.maps`
3. 重启或软重启
4. 打开模块设置, 选择导航模式或关闭重定向

## 构建
```bash
./gradlew assembleDebug assembleRelease
```

## 说明
- 仅拦截来自 Google Maps 的 Intent, 其他应用不会被强制跳转
- 反查地址依赖系统 Geocoder, 可能需要网络
