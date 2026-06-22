package com.appkitz.installer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

/**
 * Foreground service that performs the actual split/single APK installation.
 *
 * The install work lives here (instead of in [InstallActivity]) so that the
 * [PackageInstaller] result broadcast is received by a process-owned component
 * whose lifetime is independent of any Activity. Previously the result
 * [PendingIntent] targeted a receiver registered by the Activity; once the
 * system's install-confirmation UI covered the Activity it could be destroyed
 * and unregister the receiver, leaving the commit result with nowhere to go —
 * which manifested as a crash ("安装失败 [1]") or a hard process crash.
 *
 * This mirrors the approach used by AppManager (muntashirakon): the install
 * runs in a foreground service and the result is handled by a manifest
 * registered receiver.
 */
class InstallService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification("正在安装..."))

        val filePath = intent?.getStringExtra(EXTRA_FILE_PATH)
        val file = filePath?.let { File(it) }
        if (file == null || !file.exists()) {
            notifyFailure("安装文件不存在")
            stopSelfSafely()
            return START_NOT_STICKY
        }

        @Suppress("DEPRECATION")
        val holder: SplitEntriesHolder? =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getSerializableExtra(EXTRA_SELECTED, SplitEntriesHolder::class.java)
            } else {
                intent.getSerializableExtra(EXTRA_SELECTED) as? SplitEntriesHolder
            }
        val selected: List<SplitEntry> = holder?.entries ?: emptyList()

        scope.launch {
            installApks(file, selected)
        }
        return START_NOT_STICKY
    }

    private fun installApks(file: File, selected: List<SplitEntry>) {
        var session: PackageInstaller.Session? = null
        try {
            if (selected.isEmpty()) {
                notifyFailure("请选择至少一个 APK")
                stopSelfSafely()
                return
            }

            val packageInstaller = packageManager.packageInstaller
            val sessionParams = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
            sessionParams.setInstallerPackageName(packageName)

            val isSingleApk = selected.size == 1 && selected[0].fileName == file.name

            // Set the session size so the installer can report progress accurately.
            if (isSingleApk) {
                sessionParams.setSize(file.length())
            } else {
                ZipFile(file).use { zip ->
                    val missingEntry = selected.firstOrNull { findZipEntry(zip, it.fileName) == null }
                    if (missingEntry != null) {
                        notifyFailure("安装包中缺少 ${missingEntry.fileName}")
                        stopSelfSafely()
                        return
                    }
                    var totalSize = 0L
                    var allSizesKnown = true
                    for (entry in selected) {
                        val size = findZipEntry(zip, entry.fileName)?.size ?: -1L
                        if (size <= 0L) {
                            allSizesKnown = false
                            break
                        }
                        totalSize += size
                    }
                    if (allSizesKnown && totalSize > 0L) {
                        sessionParams.setSize(totalSize)
                    }
                }
            }

            // Resolve the target package name from the base APK.
            val baseEntry = selected.find { it.type == "base" }
            if (baseEntry != null) {
                if (isSingleApk) {
                    val pkgInfo = packageManager.getPackageArchiveInfo(file.absolutePath, 0)
                    if (pkgInfo != null) {
                        sessionParams.setAppPackageName(pkgInfo.packageName)
                    }
                } else {
                    var found = false
                    ZipFile(file).use { zip ->
                        val ze = findZipEntry(zip, baseEntry.fileName)
                        if (ze != null) {
                            val tempBase = File(cacheDir, "tmp_base_${System.nanoTime()}.apk")
                            try {
                                zip.getInputStream(ze).use { input ->
                                    FileOutputStream(tempBase).use { output -> input.copyTo(output) }
                                }
                                val pkgInfo = packageManager.getPackageArchiveInfo(tempBase.absolutePath, 0)
                                if (pkgInfo != null) {
                                    sessionParams.setAppPackageName(pkgInfo.packageName)
                                    found = true
                                }
                            } finally {
                                tempBase.delete()
                            }
                        }
                        if (!found) {
                            val entries = zip.entries()
                            while (entries.hasMoreElements()) {
                                val e = entries.nextElement()
                                if (e.isDirectory) continue
                                val name = e.name.substringAfterLast('/')
                                if (!name.endsWith(".apk", ignoreCase = true)) continue
                                val tempBase = File(cacheDir, "tmp_base_${System.nanoTime()}.apk")
                                try {
                                    zip.getInputStream(e).use { input ->
                                        FileOutputStream(tempBase).use { output -> input.copyTo(output) }
                                    }
                                    val pkgInfo = packageManager.getPackageArchiveInfo(tempBase.absolutePath, 0)
                                    if (pkgInfo != null) {
                                        sessionParams.setAppPackageName(pkgInfo.packageName)
                                        break
                                    }
                                } finally {
                                    tempBase.delete()
                                }
                            }
                        }
                    }
                }
            }

            val sessionId = packageInstaller.createSession(sessionParams)
            session = packageInstaller.openSession(sessionId)

            if (isSingleApk) {
                file.inputStream().use { input ->
                    session.openWrite(file.name, 0, file.length()).use { out ->
                        input.copyTo(out)
                        session.fsync(out)
                    }
                }
            } else {
                ZipFile(file).use { zip ->
                    val usedNames = mutableSetOf<String>()
                    var writtenCount = 0
                    for (entry in selected) {
                        val ze = findZipEntry(zip, entry.fileName)
                            ?: throw IllegalStateException("Missing APK entry: ${entry.fileName}")
                        val sessionName = buildSessionFileName(entry.fileName, usedNames)
                        var actualSize = ze.size
                        if (actualSize < 0) {
                            val tempFile = File(cacheDir, "tmp_${System.nanoTime()}")
                            try {
                                zip.getInputStream(ze).use { input ->
                                    FileOutputStream(tempFile).use { output -> input.copyTo(output) }
                                }
                                actualSize = tempFile.length()
                                tempFile.inputStream().use { input ->
                                    session.openWrite(sessionName, 0, actualSize).use { out ->
                                        input.copyTo(out)
                                        session.fsync(out)
                                    }
                                }
                            } finally {
                                tempFile.delete()
                            }
                        } else {
                            zip.getInputStream(ze).use { input ->
                                session.openWrite(sessionName, 0, actualSize).use { out ->
                                    input.copyTo(out)
                                    session.fsync(out)
                                }
                            }
                        }
                        writtenCount++
                    }
                    if (writtenCount != selected.size) {
                        throw IllegalStateException("Only wrote $writtenCount of ${selected.size} APK entries")
                    }
                }
            }

            // The result broadcast is delivered to the manifest-registered
            // InstallResultReceiver, which is process-owned and independent of
            // any Activity lifetime. FLAG_UPDATE_CURRENT so reusing the request
            // code updates the existing PendingIntent. FLAG_MUTABLE is required
            // by the system since Android S (it attaches the EXTRA_STATUS data).
            val resultIntent = Intent(ACTION_INSTALL_RESULT).apply {
                setPackage(packageName)
                addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
            }
            val pendingIntentFlags = PendingIntent.FLAG_UPDATE_CURRENT or
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
            val pendingIntent = PendingIntent.getBroadcast(
                this, sessionId, resultIntent, pendingIntentFlags
            )
            session.commit(pendingIntent.intentSender)
        } catch (e: Throwable) {
            Log.e(TAG, "installApks failed", e)
            try { session?.abandon() } catch (_: Exception) {}
            val msg = e.message
            notifyFailure(if (msg != null) "${e::class.java.simpleName}: $msg" else e::class.java.simpleName)
            stopSelfSafely()
        }
    }

    private fun notifyFailure(message: String) {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(
            NOTIFICATION_ID + 1,
            buildNotification("安装失败：$message", error = true)
        )
    }

    private fun buildNotification(text: String, error: Boolean = false): Notification {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            nm.getNotificationChannel(CHANNEL_ID) == null
        ) {
            val channel = NotificationChannel(
                CHANNEL_ID, "应用安装", NotificationManager.IMPORTANCE_LOW
            ).apply { setShowBadge(false) }
            nm.createNotificationChannel(channel)
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Appkitz 安装")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(!error)
            .setSilent(!error)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun stopSelfSafely() {
        try {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } catch (_: Exception) {}
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    companion object {
        private const val TAG = "InstallService"
        private const val CHANNEL_ID = "appkitz_install"
        private const val NOTIFICATION_ID = 4271

        const val ACTION_INSTALL_RESULT = "com.appkitz.INSTALL_RESULT"
        const val EXTRA_FILE_PATH = "extra_file_path"
        const val EXTRA_SELECTED = "extra_selected"

        fun start(context: Context, file: File, selected: List<SplitEntry>) {
            val intent = Intent(context, InstallService::class.java).apply {
                putExtra(EXTRA_FILE_PATH, file.absolutePath)
                putExtra(EXTRA_SELECTED, SplitEntriesHolder(ArrayList(selected)))
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }
}

/**
 * Serializable wrapper around the selected [SplitEntry] list, since [SplitEntry]
 * itself is kept as a lightweight data class. Carried through the service Intent.
 */
class SplitEntriesHolder(val entries: ArrayList<SplitEntry>) : java.io.Serializable {
    companion object { private const val serialVersionUID = 1L }
}

private fun buildSessionFileName(fileName: String, usedNames: MutableSet<String>): String {
    val cleanName = fileName.substringAfterLast('/').ifBlank { "split.apk" }
    if (usedNames.add(cleanName)) return cleanName

    val baseName = cleanName.substringBeforeLast('.', cleanName)
    val extension = cleanName.substringAfterLast('.', "")
    var index = 1
    while (true) {
        val candidate = if (extension.isEmpty()) {
            "${baseName}_$index"
        } else {
            "${baseName}_$index.$extension"
        }
        if (usedNames.add(candidate)) return candidate
        index++
    }
}

private fun findZipEntry(zip: ZipFile, fileName: String): ZipEntry? {
    zip.getEntry(fileName)?.let { return it }
    val entries = zip.entries()
    while (entries.hasMoreElements()) {
        val e = entries.nextElement()
        if (e.isDirectory) continue
        if (e.name.endsWith("/$fileName") || e.name == fileName) {
            return e
        }
    }
    return null
}
