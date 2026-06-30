# Appkitz

[English](#english)

## 功能特点

- **WebDAV 备份** — 将应用上传到自建 WebDAV 服务器
- **本地备份** — 将应用备份到本地存储
- **用户应用 + 系统应用** — 分 tab 展示，支持全选
- **两种备份模式** — 仅备份元数据（名称、包名、版本），或含 APK 文件
- **搜索** — 按应用名和包名实时搜索过滤
- **安装 Split APK** — 支持安装 .apks / .apkm / .xapk 格式安装包
- **长按复制包名** — 快速复制应用包名到剪贴板
- **多语言** — 自动跟随系统语言（简体中文 / English）
- **体验优化** — Material Design 3、自适应主题、120Hz 高刷、加载动画

## 构建

```bash
./gradlew assembleDebug
```

APK 输出：`app/build/outputs/apk/debug/app-debug.apk`

## 致谢

- [AppManager](https://github.com/muntashirakon/AppManager) — Split APK 安装器参考实现

## 许可证

- **WebDAV 备份/恢复模块** — MIT
- **APK 安装模块** (`com.appkitz.installer`) — GPL-3.0-or-later，
  移植自 [AppManager](https://github.com/muntashirakon/AppManager) (GPL-3.0)

---

<a name="english"></a>

# Appkitz

[中文](#)

## Features

- **WebDAV Backup** — Upload apps to your own WebDAV server
- **Local Backup** — Backup apps to local storage
- **User Apps + System Apps** — Separate tabs with select-all support
- **Two Backup Modes** — Metadata only (name, package, version), or with APK file
- **Search** — Real-time filtering by app name and package name
- **Split APK Installer** — Install .apks / .apkm / .xapk packages
- **Long-press Copy** — Copy package name to clipboard
- **Multi-language** — Auto-follows system language (简体中文 / English)
- **UX Optimizations** — Material Design 3, dynamic theming, 120Hz support, loading animation

## Build

```bash
./gradlew assembleDebug
```

APK output: `app/build/outputs/apk/debug/app-debug.apk`

## Credits

- [AppManager](https://github.com/muntashirakon/AppManager) — Reference implementation for Split APK installer

## License

- **WebDAV Backup/Restore module** — MIT
- **APK Installer module** (`com.appkitz.installer`) — GPL-3.0-or-later,
  ported from [AppManager](https://github.com/muntashirakon/AppManager) (GPL-3.0)
