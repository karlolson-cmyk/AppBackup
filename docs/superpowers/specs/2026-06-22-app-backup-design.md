# APP 备份 — 设计文档

**日期**: 2026-06-22
**状态**: 已批准

## 概述

Android 应用，用于列出用户安装的应用（排除系统应用），将应用名称、包名和 APK 文件备份到 WebDAV 服务器。

## 技术栈

| 层面 | 选择 |
|------|------|
| 语言 | Kotlin |
| UI | Jetpack Compose + Material Design 3 |
| 架构 | MVVM + Coroutines + StateFlow |
| 网络 | OkHttp（手写 WebDAV PROPFIND/PUT） |
| 持久化 | SharedPreferences（加密存储 WebDAV 凭据） |
| 最低 SDK | API 30 (Android 11) |
| 目标 SDK | API 36 (Android 16) |
| 构建 | Gradle + Kotlin DSL |

## 功能需求

1. **列出用户应用** — 通过 `PackageManager.getInstalledPackages()` 获取，过滤 `FLAG_SYSTEM`
2. **选择应用** — 支持全选/取消/逐个勾选
3. **备份到 WebDAV** — 上传 APK + JSON 元数据
4. **WebDAV 配置** — URL / 用户名 / 密码，加密保存
5. **备份进度** — 实时显示上传进度

## 非功能需求

- 登录后不闪退，所有异常捕获并转译为中文提示
- 支持 Cloudflare SSL 证书（公共 CA，系统 TrustManager 直接信任）
- APK 体积尽量小

## 架构

### 包结构

```
com.appbackup/
├── AppBackupApp.kt              // Application 类
├── MainActivity.kt               // 单 Activity
├── ui/
│   ├── screen/
│   │   ├── AppListScreen.kt      // App 列表页面
│   │   └── WebDavConfigScreen.kt // WebDAV 配置页面
│   ├── theme/
│   │   └── Theme.kt              // MD3 主题
│   └── component/
│       └── AppItem.kt            // 单个 App 卡片组件
├── data/
│   ├── model/
│   │   └── AppInfo.kt            // App 数据类
│   ├── repository/
│   │   ├── AppRepository.kt      // 获取已安装 App
│   │   └── WebDavRepository.kt   // WebDAV 上传逻辑
│   └── pref/
│       └── PreferencesManager.kt // 加密存储
└── viewmodel/
    ├── AppListViewModel.kt       // 列表 + 选中状态
    └── WebDavViewModel.kt        // WebDAV 配置 + 上传
```

### 数据流

```
用户操作 → ViewModel (StateFlow) → Composable UI
         ↕ (挂起函数)
         Repository → PackageManager / OkHttp / SharedPreferences
```

## WebDAV 协议实现

使用 OkHttp 直接构建 WebDAV 请求：

### PROPFIND（检查远程目录）
- `PROPFIND {url}/` → 解析 XML 响应，获取目录列表
- 用于判断远程文件是否已存在（增量备份）

### PUT（上传文件）
- `PUT {url}/{appName}/{appName}.apk` → 上传 APK
- `PUT {url}/{appName}/{appName}.json` → 上传元数据
- `MKCOL {url}/{appName}` → 如果目录不存在则创建

### 错误处理（所有异常捕获，不崩溃）

| 异常类型 | 用户提示 |
|---------|---------|
| UnknownHostException | 无法解析服务器地址 |
| SocketTimeoutException | 连接超时，请检查服务器地址 |
| SSLHandshakeException | SSL 证书错误 |
| HTTP 401/403 | 账号或密码错误 |
| HTTP 404 | 路径不存在 |
| HTTP 405/501 | 服务器不支持 WebDAV |
| HTTP 5xx | 服务器错误 |
| IOException | 网络错误：{具体消息} |

## UI 设计

### 页面 1：WebDAV 配置页
- 服务器地址输入框（带 placeholder）
- 用户名输入框
- 密码输入框（密码模式）
- 「测试连接」按钮 → 显示成功/失败 Snackbar
- 「保存并开始」按钮 → 写入加密 SharedPref，跳转列表页

### 页面 2：App 列表页
- TopAppBar 标题「APP 备份」
- 「全选」复选框 + 已选计数
- LazyColumn 列表，每项：复选框 + 应用图标 + 名称 + 包名 + APK 大小
- 底部悬浮按钮「备份选中到 WebDAV」
- 备份中显示进度 Dialog（进度条 + 当前文件名 + 取消按钮）

### 页面 3：备份结果页（可选，可合并到列表页 Snackbar）
- 显示成功/失败列表

## 远程文件结构

```
{webdav_root}/App Backup/
├── 哔哩哔哩/
│   ├── 哔哩哔哩.apk
│   └── 哔哩哔哩.json
├── 微信/
│   ├── 微信.apk
│   └── 微信.json
└── backup_info.json
```

### app.json 格式
```json
{
  "name": "哔哩哔哩",
  "packageName": "tv.danmaku.bili",
  "versionName": "7.50.0",
  "versionCode": 7500000,
  "backupTime": "2026-06-22T10:30:00Z",
  "apkMd5": "a1b2c3d4e5..."
}
```

## 数据持久化

WebDAV 凭据通过 SharedPreferences 加密存储（使用 Android 的 EncryptedSharedPreferences 或简单 Base64 编码 —— 权衡安全性 vs 减少依赖，选择 EncryptedSharedPreferences 以提升安全性）。

## 应用图标

Adaptive Icon，纯 XML Vector Drawable 实现：
- 背景层：MD3 蓝色渐变
- 前景层：备份箭头 + 安卓样式前景

## 构建输出

使用 Gradle 构建签名 APK：
- debug 签名用于开发测试
- 生成 `app/build/outputs/apk/debug/app-debug.apk`
