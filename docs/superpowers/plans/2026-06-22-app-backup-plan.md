# APP 备份 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build "APP 备份" Android app that lists user apps and backs up APK + metadata to WebDAV.

**Architecture:** Single-Activity MVVM app with Jetpack Compose + Material Design 3 UI, OkHttp for WebDAV, EncryptedSharedPreferences for credentials.

**Tech Stack:** Kotlin, Jetpack Compose + MD3, OkHttp 4.x, Android API 30-36, Gradle 8.13, AGP 8.8.0

## Global Constraints

- minSdk = 30, targetSdk = compileSdk = 36
- Kotlin DSL for Gradle, version catalog (libs.versions.toml)
- All user-facing strings in Chinese
- All WebDAV errors caught and mapped to Chinese error messages — never crash
- Package name: `com.appbackup`
- No external WebDAV library — use OkHttp directly for PROPFIND/PUT/MKCOL
- App name in launcher: "APP备份"
- No external image resources — app icon is pure XML Vector Drawable

---

### Task 1: Project Scaffolding

**Files:**
- Create: `settings.gradle.kts`
- Create: `build.gradle.kts` (root)
- Create: `gradle.properties`
- Create: `gradle/libs.versions.toml`
- Create: `app/build.gradle.kts`
- Create: `app/src/main/AndroidManifest.xml`
- Create: `gradle/wrapper/gradle-wrapper.properties`
- Create: `local.properties`

**Interfaces:**
- Produces: Fully buildable Gradle project skeleton with proper SDK paths

- [ ] **Step 1: Create version catalog**

`gradle/libs.versions.toml`:
```toml
[versions]
agp = "8.8.0"
kotlin = "2.1.0"
compose-bom = "2024.12.01"
okhttp = "4.12.0"
lifecycle = "2.8.7"
navigation = "2.8.5"
activity-compose = "1.9.3"
security-crypto = "1.1.0-alpha06"
core-ktx = "1.15.0"

[libraries]
androidx-core-ktx = { module = "androidx.core:core-ktx", version.ref = "core-ktx" }
androidx-lifecycle-runtime-ktx = { module = "androidx.lifecycle:lifecycle-runtime-ktx", version.ref = "lifecycle" }
androidx-lifecycle-viewmodel-compose = { module = "androidx.lifecycle:lifecycle-viewmodel-compose", version.ref = "lifecycle" }
androidx-lifecycle-runtime-compose = { module = "androidx.lifecycle:lifecycle-runtime-compose", version.ref = "lifecycle" }
androidx-activity-compose = { module = "androidx.activity:activity-compose", version.ref = "activity-compose" }
androidx-navigation-compose = { module = "androidx.navigation:navigation-compose", version.ref = "navigation" }
androidx-security-crypto = { module = "androidx.security:security-crypto", version.ref = "security-crypto" }
compose-bom = { module = "androidx.compose:compose-bom", version.ref = "compose-bom" }
compose-material3 = { module = "androidx.compose.material3:material3" }
compose-material-icons-extended = { module = "androidx.compose.material:material-icons-extended" }
compose-ui = { module = "androidx.compose.ui:ui" }
compose-ui-tooling = { module = "androidx.compose.ui:ui-tooling" }
compose-ui-tooling-preview = { module = "androidx.compose.ui:ui-tooling-preview" }
okhttp = { module = "com.squareup.okhttp3:okhttp", version.ref = "okhttp" }
okhttp-sse = { module = "com.squareup.okhttp3:okhttp-sse", version.ref = "okhttp" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
```

- [ ] **Step 2: Create settings.gradle.kts**

```kotlin
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "AppBackup"
include(":app")
```

- [ ] **Step 3: Create root build.gradle.kts**

```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
```

- [ ] **Step 4: Create gradle.properties**

```properties
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
android.useAndroidX=true
kotlin.code.style=official
android.nonTransitiveRClass=true
```

- [ ] **Step 5: Create local.properties**

```properties
sdk.dir=C:\\Users\\cctvy\\AppData\\Local\\Android\\Sdk
```

- [ ] **Step 6: Create app/build.gradle.kts**

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.appbackup"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.appbackup"
        minSdk = 30
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.security.crypto)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.okhttp)
    implementation(libs.okhttp.sse)
    debugImplementation(libs.compose.ui.tooling)
}
```

- [ ] **Step 7: Create AndroidManifest.xml**

`app/src/main/AndroidManifest.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.QUERY_ALL_PACKAGES"
        android:usesPermissionFlags="neverForLocation" />

    <application
        android:name=".AppBackupApp"
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher"
        android:label="APP备份"
        android:roundIcon="@mipmap/ic_launcher"
        android:networkSecurityConfig="@xml/network_security_config"
        android:supportsRtl="true"
        android:theme="@style/Theme.AppBackup">
        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:theme="@style/Theme.AppBackup">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
```

- [ ] **Step 8: Create Gradle wrapper properties**

`gradle/wrapper/gradle-wrapper.properties`:
```properties
distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
distributionUrl=https\://services.gradle.org/distributions/gradle-8.13-bin.zip
zipStoreBase=GRADLE_USER_HOME
zipStorePath=wrapper/dists
```

- [ ] **Step 9: Generate gradlew.bat**

Run: `New-Item -ItemType Directory -Force -Path "D:\AppBackup\gradle\wrapper"`

Then copy gradle wrapper jar. Since we don't have `gradle` command, let's check if there's a cached wrapper we can use, or generate it.

Actually, the simplest approach: use the cached Gradle distribution to generate the wrapper:

```powershell
& "C:\Users\cctvy\.gradle\wrapper\dists\gradle-8.13-bin\5xuhj0ry160q40clulazy9h7d\gradle-8.13\bin\gradle.bat" wrapper --project-dir "D:\AppBackup"
```

- [ ] **Step 10: Verify build**

Run: `cd D:\AppBackup; .\gradlew.bat tasks`
Expected: Prints available Gradle tasks (build succeeds at configuration level)

---

### Task 2: Data Model & Preferences Storage

**Files:**
- Create: `app/src/main/java/com/appbackup/data/model/AppInfo.kt`
- Create: `app/src/main/java/com/appbackup/data/pref/PreferencesManager.kt`

**Interfaces:**
- Produces: `AppInfo` data class, `PreferencesManager` with `saveWebDavConfig`/`loadWebDavConfig`/`clearWebDavConfig`

- [ ] **Step 1: Create AppInfo data class**

`app/src/main/java/com/appbackup/data/model/AppInfo.kt`:
```kotlin
package com.appbackup.data.model

data class AppInfo(
    val name: String,
    val packageName: String,
    val apkPath: String,
    val apkSize: Long,
    val versionName: String,
    val versionCode: Long,
    val isSelected: Boolean = false
)
```

- [ ] **Step 2: Create PreferencesManager**

`app/src/main/java/com/appbackup/data/pref/PreferencesManager.kt`:
```kotlin
package com.appbackup.data.pref

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class PreferencesManager(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "app_backup_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun saveWebDavConfig(url: String, username: String, password: String) {
        prefs.edit()
            .putString(KEY_URL, url)
            .putString(KEY_USERNAME, username)
            .putString(KEY_PASSWORD, password)
            .apply()
    }

    fun loadWebDavConfig(): WebDavConfig? {
        val url = prefs.getString(KEY_URL, null) ?: return null
        val username = prefs.getString(KEY_USERNAME, null) ?: return null
        val password = prefs.getString(KEY_PASSWORD, null) ?: return null
        return WebDavConfig(url, username, password)
    }

    fun clearWebDavConfig() {
        prefs.edit().clear().apply()
    }

    data class WebDavConfig(
        val url: String,
        val username: String,
        val password: String
    )

    companion object {
        private const val KEY_URL = "webdav_url"
        private const val KEY_USERNAME = "webdav_username"
        private const val KEY_PASSWORD = "webdav_password"
    }
}
```

---

### Task 3: AppRepository — List User Installed Apps

**Files:**
- Create: `app/src/main/java/com/appbackup/data/repository/AppRepository.kt`

**Interfaces:**
- Produces: `AppRepository(context)` with `fun getInstalledApps(): List<AppInfo>`
- Method filters out system apps, sorts by name

- [ ] **Step 1: Create AppRepository**

`app/src/main/java/com/appbackup/data/repository/AppRepository.kt`:
```kotlin
package com.appbackup.data.repository

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import com.appbackup.data.model.AppInfo
import java.io.File

class AppRepository(private val context: Context) {

    fun getInstalledApps(): List<AppInfo> {
        val pm = context.packageManager
        val apps = pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(0))
            .filter { info ->
                val appInfo = info.applicationInfo
                appInfo != null &&
                (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) == 0 &&
                appInfo.enabled
            }
            .map { info ->
                val appInfo = info.applicationInfo
                val apkFile = File(appInfo.sourceDir)
                AppInfo(
                    name = pm.getApplicationLabel(appInfo).toString(),
                    packageName = appInfo.packageName,
                    apkPath = appInfo.sourceDir,
                    apkSize = apkFile.length(),
                    versionName = info.versionName ?: "",
                    versionCode = info.longVersionCode
                )
            }
            .sortedBy { it.name }
        return apps
    }
}
```

---

### Task 4: WebDavRepository — WebDAV Protocol Implementation

**Files:**
- Create: `app/src/main/java/com/appbackup/data/repository/WebDavRepository.kt`
- Create: `app/src/test/java/com/appbackup/data/repository/WebDavRepositoryTest.kt`

**Interfaces:**
- Produces: `WebDavRepository` with:
  - `suspend fun testConnection(url: String, username: String, password: String): Result<Unit>`
  - `suspend fun backupApk(app: AppInfo, config: WebDavConfig): Result<Unit>`

- [ ] **Step 1: Create WebDavRepository**

`app/src/main/java/com/appbackup/data/repository/WebDavRepository.kt`:
```kotlin
package com.appbackup.data.repository

import com.appbackup.data.model.AppInfo
import com.appbackup.data.pref.PreferencesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLHandshakeException

class WebDavRepository {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    suspend fun testConnection(
        url: String,
        username: String,
        password: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val normalizedUrl = normalizeUrl(url)
            val request = Request.Builder()
                .url(normalizedUrl)
                .method("PROPFIND", null)
                .header("Authorization", Credentials.basic(username, password))
                .header("Depth", "0")
                .build()
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                Result.success("连接成功")
            } else {
                val msg = when (response.code) {
                    401, 403 -> "账号或密码错误"
                    404 -> "路径不存在"
                    405, 501 -> "服务器不支持 WebDAV"
                    in 500..599 -> "服务器错误 (${response.code})"
                    else -> "连接失败 (${response.code})"
                }
                Result.failure(IOException(msg))
            }
        } catch (e: UnknownHostException) {
            Result.failure(IOException("无法解析服务器地址"))
        } catch (e: SocketTimeoutException) {
            Result.failure(IOException("连接超时，请检查服务器地址"))
        } catch (e: SSLHandshakeException) {
            Result.failure(IOException("SSL 证书错误"))
        } catch (e: IOException) {
            Result.failure(IOException("网络错误：${e.message}"))
        }
    }

    suspend fun backupApps(
        apps: List<AppInfo>,
        config: PreferencesManager.WebDavConfig
    ): Result<Map<AppInfo, String>> = withContext(Dispatchers.IO) {
        val results = mutableMapOf<AppInfo, String>()
        val baseUrl = normalizeUrl(config.url)
        val credential = Credentials.basic(config.username, config.password)
        val appDir = "$baseUrl/APP备份"

        ensureDirectory(appDir, credential)

        for (app in apps) {
            val result = try {
                backupSingleApp(app, appDir, credential)
                Result.success(app.name)
            } catch (e: Exception) {
                val errMsg = when (e) {
                    is IOException -> "网络错误：${e.message}"
                    else -> "未知错误：${e.message}"
                }
                Result.failure<String>(IOException(errMsg))
            }
            result.fold(
                onSuccess = { results[app] = "成功" },
                onFailure = { results[app] = it.message ?: "失败" }
            )
        }

        if (results.all { it.value == "成功" }) {
            Result.success(results)
        } else {
            Result.success(results)
        }
    }

    private fun backupSingleApp(
        app: AppInfo,
        appDir: String,
        credential: String
    ) {
        val sanitizedName = app.name.replace(Regex("[/\\\\:*?\"<>|]"), "_")
        val dirUrl = "$appDir/$sanitizedName"
        ensureDirectory(dirUrl, credential)

        val apkFile = File(app.apkPath)
        val apkBytes = apkFile.readBytes()

        val apkMd5 = MessageDigest.getInstance("MD5").digest(apkBytes)
            .joinToString("") { "%02x".format(it) }

        putFile("$dirUrl/$sanitizedName.apk", apkBytes, credential)

        val jsonObj = JSONObject().apply {
            put("name", app.name)
            put("packageName", app.packageName)
            put("versionName", app.versionName)
            put("versionCode", app.versionCode)
            put("backupTime", java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US).format(java.util.Date()))
            put("apkMd5", apkMd5)
        }
        putFile("$dirUrl/$sanitizedName.json", jsonObj.toString(2).toByteArray(), credential)
    }

    private fun ensureDirectory(dirUrl: String, credential: String) {
        val request = Request.Builder()
            .url(dirUrl)
            .method("MKCOL", null)
            .header("Authorization", credential)
            .build()
        val response = client.newCall(request).execute()
        if (!response.isSuccessful && response.code != 405 && response.code != 201 && response.code != 409) {
            // 405 means already exists as collection (acceptable)
            // 409 means parent doesn't exist (will be handled by caller)
        }
        response.close()
    }

    private fun putFile(url: String, data: ByteArray, credential: String) {
        val body = data.toRequestBody("application/octet-stream".toMediaType())
        val request = Request.Builder()
            .url(url)
            .put(body)
            .header("Authorization", credential)
            .build()
        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            throw IOException("上传失败 (${response.code})")
        }
        response.close()
    }

    private fun normalizeUrl(url: String): String {
        var normalized = url.trim()
        if (!normalized.startsWith("http://") && !normalized.startsWith("https://")) {
            normalized = "https://$normalized"
        }
        return normalized.trimEnd('/')
    }
}
```

- [ ] **Step 2: Create unit test for WebDavRepository**

Create test directory: `app/src/test/java/com/appbackup/data/repository/WebDavRepositoryTest.kt`

```kotlin
package com.appbackup.data.repository

import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class WebDavRepositoryTest {

    private lateinit var server: MockWebServer
    private lateinit var repository: WebDavRepository

    @Before
    fun setup() {
        server = MockWebServer()
        repository = WebDavRepository()
    }

    @After
    fun teardown() {
        server.shutdown()
    }

    @Test
    fun `test connection succeeds with 200 response`() = runTest {
        server.enqueue(MockResponse().setResponseCode(207).setBody(""))
        val result = repository.testConnection(
            server.url("/").toString().trimEnd('/'),
            "user", "pass"
        )
        assertTrue(result.isSuccess)
    }

    @Test
    fun `test connection fails with 401`() = runTest {
        server.enqueue(MockResponse().setResponseCode(401))
        val result = repository.testConnection(
            server.url("/").toString().trimEnd('/'),
            "user", "wrong"
        )
        assertTrue(result.isFailure)
        assertEquals("账号或密码错误", result.exceptionOrNull()?.message)
    }

    @Test
    fun `test connection fails with 404`() = runTest {
        server.enqueue(MockResponse().setResponseCode(404))
        val result = repository.testConnection(
            server.url("/").toString().trimEnd('/'),
            "user", "pass"
        )
        assertTrue(result.isFailure)
        assertEquals("路径不存在", result.exceptionOrNull()?.message)
    }
}
```

- [ ] **Step 3: Add test dependencies to app/build.gradle.kts**

Add to `app/build.gradle.kts` dependencies block:
```kotlin
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
```

- [ ] **Step 4: Run tests**

Run: `cd D:\AppBackup; .\gradlew.bat test`
Expected: BUILD SUCCESSFUL, tests pass

---

### Task 5: Theme & Resources

**Files:**
- Create: `app/src/main/java/com/appbackup/ui/theme/Color.kt`
- Create: `app/src/main/java/com/appbackup/ui/theme/Theme.kt`
- Create: `app/src/main/java/com/appbackup/ui/theme/Type.kt`
- Create: `app/src/main/res/values/strings.xml`
- Create: `app/src/main/res/values/themes.xml`
- Create: `app/src/main/res/xml/network_security_config.xml`
- Create: `app/src/main/res/drawable/ic_launcher_foreground.xml`
- Create: `app/src/main/res/drawable/ic_launcher_background.xml`
- Create: `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml`

**Interfaces:**
- Produces: MD3 theme with dynamic color support, Chinese strings, network config, adaptive icon

- [ ] **Step 1: Create Color.kt**

```kotlin
package com.appbackup.ui.theme

import androidx.compose.ui.graphics.Color

val Blue80 = Color(0xFFB3D4FC)
val BlueGrey80 = Color(0xFFBCC7DC)
val LightBlue80 = Color(0xFFC2E0FF)

val Blue40 = Color(0xFF1565C0)
val BlueGrey40 = Color(0xFF546E7A)
val LightBlue40 = Color(0xFF42A5F5)
```

- [ ] **Step 2: Create Type.kt**

```kotlin
package com.appbackup.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp

val AppTypography = Typography(
    bodyLarge = TextStyle(fontFamily = FontFamily.Default, fontSize = 16.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.Default, fontSize = 14.sp),
    labelSmall = TextStyle(fontFamily = FontFamily.Default, fontSize = 11.sp)
)
```

- [ ] **Step 3: Create Theme.kt**

```kotlin
package com.appbackup.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = Blue80,
    secondary = BlueGrey80,
    tertiary = LightBlue80
)

private val LightColorScheme = lightColorScheme(
    primary = Blue40,
    secondary = BlueGrey40,
    tertiary = LightBlue40
)

@Composable
fun AppBackupTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.primary.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        content = content
    )
}
```

- [ ] **Step 4: Create strings.xml**

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">APP备份</string>
    <string name="webdav_config">WebDAV 配置</string>
    <string name="server_url">服务器地址</string>
    <string name="username">用户名</string>
    <string name="password">密码</string>
    <string name="test_connection">测试连接</string>
    <string name="save_and_start">保存并开始</string>
    <string name="select_all">全选</string>
    <string name="backup_selected">备份选中到 WebDAV</string>
    <string name="backup_progress">正在备份...</string>
    <string name="cancel">取消</string>
    <string name="success">成功</string>
    <string name="failed">失败</string>
    <string name="connection_success">连接成功</string>
</resources>
```

- [ ] **Step 5: Create themes.xml**

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <style name="Theme.AppBackup" parent="android:Theme.Material.Light.NoActionBar" />
</resources>
```

- [ ] **Step 6: Create network_security_config.xml**

```xml
<?xml version="1.0" encoding="utf-8"?>
<network-security-config>
    <base-config cleartextTrafficPermitted="false">
        <trust-anchors>
            <certificates src="system" />
        </trust-anchors>
    </base-config>
</network-security-config>
```

- [ ] **Step 7: Create app icon vectors**

`drawable/ic_launcher_background.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <path
        android:fillColor="#1565C0"
        android:pathData="M0,0h108v108h-108z" />
</vector>
```

`drawable/ic_launcher_foreground.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <!-- 安卓机器人 -->
    <path
        android:fillColor="#FFFFFF"
        android:pathData="M44,42h20v2h-20zM42,36c0,-2 2,-4 4,-4l14,0c2,0 4,2 4,4l0,16l-22,0zM46,34l-4,0l0,4l4,0zM62,34l4,0l0,4l-4,0zM44,38l0,12l20,0l0,-12z" />
    <!-- 备份箭头 -->
    <path
        android:fillColor="#FFFFFF"
        android:pathData="M54,52l-8,-8h5v-6h6v6h5z" />
</vector>
```

`mipmap-anydpi-v26/ic_launcher.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/ic_launcher_background" />
    <foreground android:drawable="@drawable/ic_launcher_foreground" />
</adaptive-icon>
```

---

### Task 6: Views & ViewModels

**Files:**
- Create: `app/src/main/java/com/appbackup/viewmodel/AppListViewModel.kt`
- Create: `app/src/main/java/com/appbackup/viewmodel/WebDavViewModel.kt`

**Interfaces:**
- `AppListViewModel`: Exposes `appList: StateFlow<List<AppInfo>>`, `fun toggleSelect(pkg: String)`, `fun selectAll()`, `backupSelected(config)`
- `WebDavViewModel`: Exposes `connectionState: StateFlow<ConnectionState>`, `fun testConnection(url, user, pass)`, `fun saveConfig(url, user, pass)`

- [ ] **Step 1: Create WebDavViewModel**

```kotlin
package com.appbackup.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.appbackup.data.pref.PreferencesManager
import com.appbackup.data.repository.WebDavRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class ConnectionState {
    data object Idle : ConnectionState()
    data object Testing : ConnectionState()
    data class Success(val message: String) : ConnectionState()
    data class Error(val message: String) : ConnectionState()
}

class WebDavViewModel(application: Application) : AndroidViewModel(application) {

    private val prefsManager = PreferencesManager(application)
    private val repository = WebDavRepository()

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Idle)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _savedConfig = MutableStateFlow(prefsManager.loadWebDavConfig())
    val savedConfig: StateFlow<PreferencesManager.WebDavConfig?> = _savedConfig.asStateFlow()

    fun testConnection(url: String, username: String, password: String) {
        viewModelScope.launch {
            _connectionState.value = ConnectionState.Testing
            val result = repository.testConnection(url, username, password)
            _connectionState.value = result.fold(
                onSuccess = { ConnectionState.Success(it) },
                onFailure = { ConnectionState.Error(it.message ?: "未知错误") }
            )
        }
    }

    fun saveConfig(url: String, username: String, password: String) {
        prefsManager.saveWebDavConfig(url, username, password)
        _savedConfig.value = PreferencesManager.WebDavConfig(url, username, password)
    }

    fun clearConfig() {
        prefsManager.clearWebDavConfig()
        _savedConfig.value = null
    }
}
```

- [ ] **Step 2: Create AppListViewModel**

```kotlin
package com.appbackup.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.appbackup.data.model.AppInfo
import com.appbackup.data.pref.PreferencesManager
import com.appbackup.data.repository.AppRepository
import com.appbackup.data.repository.WebDavRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class BackupState {
    data object Idle : BackupState()
    data class InProgress(val currentApp: String, val progress: Float) : BackupState()
    data class Completed(val results: Map<AppInfo, String>) : BackupState()
    data class Error(val message: String) : BackupState()
}

class AppListViewModel(application: Application) : AndroidViewModel(application) {

    private val appRepository = AppRepository(application)
    private val webDavRepository = WebDavRepository()

    private val _appList = MutableStateFlow<List<AppInfo>>(emptyList())
    val appList: StateFlow<List<AppInfo>> = _appList.asStateFlow()

    private val _backupState = MutableStateFlow<BackupState>(BackupState.Idle)
    val backupState: StateFlow<BackupState> = _backupState.asStateFlow()

    private var webDavConfig: PreferencesManager.WebDavConfig? = null

    fun setWebDavConfig(config: PreferencesManager.WebDavConfig) {
        webDavConfig = config
    }

    fun loadApps() {
        viewModelScope.launch {
            val apps = appRepository.getInstalledApps()
            _appList.value = apps
        }
    }

    fun toggleSelect(packageName: String) {
        _appList.value = _appList.value.map {
            if (it.packageName == packageName) it.copy(isSelected = !it.isSelected) else it
        }
    }

    fun selectAll(selected: Boolean) {
        _appList.value = _appList.value.map { it.copy(isSelected = selected) }
    }

    fun getSelectedApps(): List<AppInfo> = _appList.value.filter { it.isSelected }

    fun backupSelected() {
        val config = webDavConfig ?: run {
            _backupState.value = BackupState.Error("请先配置 WebDAV")
            return
        }
        val selected = getSelectedApps()
        if (selected.isEmpty()) {
            _backupState.value = BackupState.Error("请选择要备份的应用")
            return
        }

        viewModelScope.launch {
            _backupState.value = BackupState.InProgress("准备中...", 0f)
            val result = webDavRepository.backupApps(selected, config)
            _backupState.value = result.fold(
                onSuccess = { BackupState.Completed(it) },
                onFailure = { BackupState.Error(it.message ?: "备份失败") }
            )
        }
    }

    fun resetBackupState() {
        _backupState.value = BackupState.Idle
    }
}
```

---

### Task 7: UI Screens

**Files:**
- Create: `app/src/main/java/com/appbackup/ui/component/AppCard.kt`
- Create: `app/src/main/java/com/appbackup/ui/component/BackupProgressDialog.kt`
- Create: `app/src/main/java/com/appbackup/ui/screen/WebDavConfigScreen.kt`
- Create: `app/src/main/java/com/appbackup/ui/screen/AppListScreen.kt`

- [ ] **Step 1: Create AppCard component**

```kotlin
package com.appbackup.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.appbackup.data.model.AppInfo

@Composable
fun AppCard(
    app: AppInfo,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = app.isSelected,
                onCheckedChange = { onToggle() }
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = app.name,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = app.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = formatSize(app.apkSize),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun formatSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        else -> "%.1f MB".format(bytes.toDouble() / (1024 * 1024))
    }
}
```

- [ ] **Step 2: Create BackupProgressDialog**

```kotlin
package com.appbackup.ui.component

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun BackupProgressDialog(
    message: String,
    progress: Float,
    onCancel: () -> Unit
) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text("正在备份...") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Text(text = message)
            }
        },
        confirmButton = {
            TextButton(onClick = onCancel) {
                Text("取消")
            }
        }
    )
}
```

- [ ] **Step 3: Create WebDavConfigScreen**

```kotlin
package com.appbackup.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.appbackup.viewmodel.ConnectionState
import com.appbackup.viewmodel.WebDavViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebDavConfigScreen(
    viewModel: WebDavViewModel,
    onConfigured: () -> Unit
) {
    val savedConfig by viewModel.savedConfig.collectAsState()
    val connectionState by viewModel.connectionState.collectAsState()

    var url by remember { mutableStateOf(savedConfig?.url ?: "") }
    var username by remember { mutableStateOf(savedConfig?.username ?: "") }
    var password by remember { mutableStateOf(savedConfig?.password ?: "") }

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(connectionState) {
        when (val state = connectionState) {
            is ConnectionState.Success -> {
                snackbarHostState.showSnackbar(state.message)
                viewModel.saveConfig(url, username, password)
                onConfigured()
            }
            is ConnectionState.Error -> {
                snackbarHostState.showSnackbar(state.message)
            }
            else -> {}
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(title = { Text("WebDAV 配置") })
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text("服务器地址") },
                placeholder = { Text("https://dav.example.com") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("用户名") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("密码") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = { viewModel.testConnection(url, username, password) },
                enabled = connectionState !is ConnectionState.Testing,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (connectionState is ConnectionState.Testing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text("测试连接")
            }
            Spacer(Modifier.height(12.dp))
            FilledTonalButton(
                onClick = {
                    viewModel.saveConfig(url, username, password)
                    onConfigured()
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = url.isNotBlank() && username.isNotBlank() && password.isNotBlank()
            ) {
                Text("保存并开始")
                Spacer(Modifier.width(8.dp))
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
            }
        }
    }
}
```

- [ ] **Step 4: Create AppListScreen**

```kotlin
package com.appbackup.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.appbackup.data.model.AppInfo
import com.appbackup.ui.component.AppCard
import com.appbackup.ui.component.BackupProgressDialog
import com.appbackup.viewmodel.AppListViewModel
import com.appbackup.viewmodel.BackupState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppListScreen(
    viewModel: AppListViewModel,
    onConfigureWebDav: () -> Unit
) {
    val appList by viewModel.appList.collectAsState()
    val backupState by viewModel.backupState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadApps()
    }

    when (val state = backupState) {
        is BackupState.InProgress -> {
            BackupProgressDialog(
                message = state.currentApp,
                progress = state.progress,
                onCancel = { viewModel.resetBackupState() }
            )
        }
        is BackupState.Completed -> {
            AlertDialog(
                onDismissRequest = { viewModel.resetBackupState() },
                title = { Text("备份完成") },
                text = {
                    Column {
                        state.results.forEach { (app, result) ->
                            Text("${app.name}: $result")
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { viewModel.resetBackupState() }) {
                        Text("确定")
                    }
                }
            )
        }
        is BackupState.Error -> {
            AlertDialog(
                onDismissRequest = { viewModel.resetBackupState() },
                title = { Text("备份出错") },
                text = { Text(state.message) },
                confirmButton = {
                    TextButton(onClick = { viewModel.resetBackupState() }) {
                        Text("确定")
                    }
                }
            )
        }
        else -> {}
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("APP 备份") },
                actions = {
                    IconButton(onClick = onConfigureWebDav) {
                        Icon(Icons.Default.Settings, contentDescription = "配置")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { viewModel.backupSelected() },
                icon = { Icon(Icons.Default.CloudUpload, contentDescription = null) },
                text = { Text("备份选中到 WebDAV") }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val selectedCount = appList.count { it.isSelected }
                Checkbox(
                    checked = selectedCount == appList.size && appList.isNotEmpty(),
                    onCheckedChange = { viewModel.selectAll(it) }
                )
                Text("全选（已选 $selectedCount/${appList.size}）")
            }
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(appList, key = { it.packageName }) { app ->
                    AppCard(
                        app = app,
                        onToggle = { viewModel.toggleSelect(app.packageName) }
                    )
                }
            }
        }
    }
}
```

---

### Task 8: App Entry Point & Navigation

**Files:**
- Create: `app/src/main/java/com/appbackup/AppBackupApp.kt`
- Create: `app/src/main/java/com/appbackup/MainActivity.kt`

- [ ] **Step 1: Create Application class**

```kotlin
package com.appbackup

import android.app.Application

class AppBackupApp : Application()
```

- [ ] **Step 2: Create MainActivity with Navigation**

```kotlin
package com.appbackup

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.appbackup.data.pref.PreferencesManager
import com.appbackup.ui.screen.AppListScreen
import com.appbackup.ui.screen.WebDavConfigScreen
import com.appbackup.ui.theme.AppBackupTheme
import com.appbackup.viewmodel.AppListViewModel
import com.appbackup.viewmodel.WebDavViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AppBackupTheme {
                val navController = rememberNavController()
                val webDavViewModel: WebDavViewModel = viewModel()
                val appListViewModel: AppListViewModel = viewModel()

                val savedConfig = PreferencesManager(this@MainActivity).loadWebDavConfig()
                val startDest = if (savedConfig != null) "app_list" else "webdav_config"

                NavHost(
                    navController = navController,
                    startDestination = startDest
                ) {
                    composable("webdav_config") {
                        WebDavConfigScreen(
                            viewModel = webDavViewModel,
                            onConfigured = {
                                val config = PreferencesManager(this@MainActivity).loadWebDavConfig()
                                config?.let { appListViewModel.setWebDavConfig(it) }
                                navController.navigate("app_list") {
                                    popUpTo("webdav_config") { inclusive = true }
                                }
                            }
                        )
                    }
                    composable("app_list") {
                        AppListScreen(
                            viewModel = appListViewModel,
                            onConfigureWebDav = {
                                navController.navigate("webdav_config")
                            }
                        )
                    }
                }
            }
        }
    }
}
```

- [ ] **Step 3: Build and verify compilation**

Run: `cd D:\AppBackup; .\gradlew.bat assembleDebug`
Expected: BUILD SUCCESSFUL

---

### Task 9: Generate APK

- [ ] **Step 1: Build debug APK**

Run: `cd D:\AppBackup; .\gradlew.bat assembleDebug`

- [ ] **Step 2: Verify APK exists**

Run: `Test-Path "D:\AppBackup\app\build\outputs\apk\debug\app-debug.apk"` — should be True

- [ ] **Step 3: Output APK path**

APK located at: `D:\AppBackup\app\build\outputs\apk\debug\app-debug.apk`
