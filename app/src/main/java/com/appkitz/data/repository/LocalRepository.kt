package com.appkitz.data.repository

import android.content.pm.PackageManager
import android.os.Environment
import com.appkitz.data.model.AppInfo
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class LocalRepository(private val pm: PackageManager) {

    private val baseDir: File
        get() {
            val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            return File(downloadDir, "Appkitz")
        }

    fun backupApps(
        apps: List<AppInfo>,
        includeApk: Boolean,
        onProgress: (String, Float) -> Unit
    ): Result<Map<AppInfo, String>> {
        return try {
            baseDir.mkdirs()
            val dateFormat = SimpleDateFormat("yyyy-MM-dd_HHmmss", Locale.getDefault())
            val sessionDir = File(baseDir, dateFormat.format(Date()))
            sessionDir.mkdirs()

            val results = mutableMapOf<AppInfo, String>()
            var completed = 0

            for (app in apps) {
                onProgress(app.name, completed.toFloat() / apps.size)
                val appDir = File(sessionDir, app.name.replace("/", "_").replace(":", "_"))
                appDir.mkdirs()

                val infoFile = File(appDir, "info.txt")
                infoFile.writeText(
                    """
Name: ${app.name}
Package: ${app.packageName}
Version: ${app.versionName}
Version Code: ${app.versionCode}
Type: ${app.type}
APK Size: ${app.apkSize}
""".trimIndent()
                )

                if (includeApk) {
                    try {
                        val ai = pm.getApplicationInfo(app.packageName, 0)
                        val sourceFile = File(ai.publicSourceDir)
                        sourceFile.copyTo(File(appDir, "app.apk"), overwrite = true)
                    } catch (e: Exception) {
                        File(appDir, "app.apk").writeText("APK not available: ${e.message}")
                    }
                }

                results[app] = appDir.absolutePath
                completed++
            }

            onProgress("", 1f)
            Result.success(results)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
