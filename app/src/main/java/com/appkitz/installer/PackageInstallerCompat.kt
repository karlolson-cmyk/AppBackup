// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2020-2024 Muntashir Al-Islam (AppManager)
// Copyright (C) 2025 Appkitz Contributors

package com.appkitz.installer

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat
import com.appkitz.installer.apk.ApkFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import kotlin.coroutines.resume

/**
 * Handles no-root APK installation via Android's PackageInstaller session API.
 * Ported from AppManager's PackageInstallerCompat (no-root path).
 */
class PackageInstallerCompat(private val context: Context) {

    companion object {
        private const val TAG = "PkgInstallerCompat"
        private const val BUFFER_SIZE = 8192

        // Hidden PackageManager install flags used by AppManager's installer path.
        private const val INSTALL_REPLACE_EXISTING = 0x00000002
        private const val INSTALL_ALLOW_TEST = 0x00000004
        private const val INSTALL_FROM_ADB = 0x00000020
        private const val INSTALL_REQUEST_DOWNGRADE = 0x00000080
        private const val INSTALL_FULL_APP = 0x00004000
        private const val INSTALL_ALLOW_DOWNGRADE = 0x00100000
        private const val INSTALL_BYPASS_LOW_TARGET_SDK_BLOCK = 0x01000000
    }

    private val appContext = context.applicationContext
    private var session: PackageInstaller.Session? = null
    private var receiver: InstallResultReceiver? = null
    private var receiverRegistered = false

    /** Whether the user has granted "Install unknown apps" permission. */
    fun canRequestPackageInstalls(): Boolean {
        return appContext.packageManager.canRequestPackageInstalls()
    }

    /** Intent to open the system settings page for granting install permission. */
    fun getManageUnknownSourcesIntent(): Intent {
        return Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
            data = Uri.parse("package:${appContext.packageName}")
        }
    }

    /** Check whether the given package is already known to the package manager. */
    private fun isPackageInstalled(packageName: String): Boolean {
        return try {
            val flags = PackageManager.MATCH_UNINSTALLED_PACKAGES
            val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                appContext.packageManager.getPackageInfo(
                    packageName,
                    PackageManager.PackageInfoFlags.of(flags.toLong())
                )
            } else {
                @Suppress("DEPRECATION")
                appContext.packageManager.getPackageInfo(packageName, flags)
            }
            Log.i(TAG, "Package $packageName is known: versionCode=${info.longVersionCode}")
            true
        } catch (e: Exception) {
            Log.i(TAG, "Package $packageName is not visible: ${e.javaClass.simpleName}: ${e.message}")
            false
        }
    }

    /**
     * Install the given [selectedEntries] from [apkFile] as a single multi-split
     * installation session. Suspends until the system reports a result.
     */
    suspend fun install(
        apkFile: ApkFile,
        selectedEntries: List<ApkFile.Entry>,
        onProgress: (Float, String?) -> Unit
    ): InstallResult = withContext(Dispatchers.IO) {
        if (selectedEntries.isEmpty()) {
            return@withContext InstallResult.Failure(
                InstallResult.STATUS_FAILURE_INVALID,
                "No entries selected"
            )
        }

        var attempt = installSession(
            apkFile = apkFile,
            selectedEntries = selectedEntries,
            forceInheritExisting = false,
            onProgress = onProgress
        )

        if (attempt.result.isAlreadyExistsFailure()) {
            Log.w(
                TAG,
                "Retrying with MODE_INHERIT_EXISTING after ALREADY_EXISTS " +
                    "(replaceFlagsApplied=${attempt.replaceFlagsApplied})"
            )
            attempt = installSession(
                apkFile = apkFile,
                selectedEntries = selectedEntries,
                forceInheritExisting = true,
                onProgress = onProgress
            )
        }

        val result = attempt.result
        if (result is InstallResult.Success && apkFile.hasObb()) {
            val obbOk = copyObb(apkFile, apkFile.getPackageName())
            if (!obbOk) {
                Log.w(TAG, "OBB copy failed for ${apkFile.getPackageName()}")
            }
        }

        return@withContext result
    }

    private data class SessionAttempt(
        val result: InstallResult,
        val replaceFlagsApplied: Boolean
    )

    private suspend fun installSession(
        apkFile: ApkFile,
        selectedEntries: List<ApkFile.Entry>,
        forceInheritExisting: Boolean,
        onProgress: (Float, String?) -> Unit
    ): SessionAttempt {
        var replaceFlagsApplied = false
        return try {
            suspendCancellableCoroutine<SessionAttempt> { cont ->
                receiver = InstallResultReceiver { result ->
                    Log.i(TAG, "Received install result: $result")
                    if (cont.isActive) cont.resume(SessionAttempt(result, replaceFlagsApplied))
                }
                cont.invokeOnCancellation {
                    abandon()
                    unregisterReceiver()
                }

                try {
                    registerReceiver()
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to register receiver", e)
                    if (cont.isActive) {
                        cont.resume(
                            SessionAttempt(
                                InstallResult.Failure(
                                    InstallResult.STATUS_FAILURE_SESSION_CREATE,
                                    "${e.javaClass.simpleName}: ${e.message ?: "Receiver registration failed"}"
                                ),
                                replaceFlagsApplied
                            )
                        )
                    }
                    return@suspendCancellableCoroutine
                }

                var sessionId = -1
                try {
                    val installer = appContext.packageManager.packageInstaller
                    val packageName = apkFile.getPackageName()
                    val installed = isPackageInstalled(packageName)

                    cleanOldSessions(installer)

                    val mode = if (forceInheritExisting) {
                        PackageInstaller.SessionParams.MODE_INHERIT_EXISTING
                    } else {
                        PackageInstaller.SessionParams.MODE_FULL_INSTALL
                    }
                    val params = PackageInstaller.SessionParams(mode)

                    // AppManager lets PackageInstaller infer the package name from base.apk.
                    setSize(params, selectedEntries)
                    configurePublicSessionParams(params)
                    replaceFlagsApplied = applyAppManagerInstallFlags(params)

                    Log.i(
                        TAG,
                        "Creating session for $packageName, ${selectedEntries.size} entries, " +
                            "mode=$mode, installed=$installed, replaceFlagsApplied=$replaceFlagsApplied"
                    )
                    sessionId = installer.createSession(params)
                    session = installer.openSession(sessionId)
                    Log.i(TAG, "Session created: id=$sessionId")

                    val total = selectedEntries.size
                    for ((index, entry) in selectedEntries.withIndex()) {
                        if (cont.isCancelled) {
                            abandon()
                            return@suspendCancellableCoroutine
                        }
                        onProgress(index.toFloat() / total, entry.fileName)
                        writeEntryToSession(apkFile, entry)
                        onProgress((index + 1).toFloat() / total, entry.fileName)
                    }
                    Log.i(TAG, "All ${selectedEntries.size} entries written to session $sessionId")

                    val intent = Intent(InstallResultReceiver.ACTION_INSTALL_RESULT).apply {
                        setPackage(appContext.packageName)
                    }
                    val pendingIntent = PendingIntent.getBroadcast(
                        appContext,
                        sessionId,
                        intent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                    )

                    Log.i(TAG, "Committing session $sessionId")
                    session!!.commit(pendingIntent.intentSender)
                } catch (e: Exception) {
                    Log.e(TAG, "Session work failed (sessionId=$sessionId)", e)
                    e.cause?.let { Log.e(TAG, "Exception cause: ${it.javaClass.name}: ${it.message}") }

                    abandon()

                    if (cont.isActive) {
                        val rootCause = unwrapException(e)
                        val statusCode = classifyException(e, rootCause)
                        val errorMsg = buildErrorMessage(e, rootCause)
                        cont.resume(
                            SessionAttempt(
                                InstallResult.Failure(statusCode, errorMsg),
                                replaceFlagsApplied
                            )
                        )
                    }
                }
            }
        } finally {
            closeSession()
            unregisterReceiver()
        }
    }

    private fun registerReceiver() {
        ContextCompat.registerReceiver(
            appContext,
            receiver,
            InstallResultReceiver.createIntentFilter(),
            ContextCompat.RECEIVER_EXPORTED
        )
        receiverRegistered = true
    }

    private fun setSize(
        params: PackageInstaller.SessionParams,
        selectedEntries: List<ApkFile.Entry>
    ) {
        try {
            val totalSize = selectedEntries.sumOf { it.getFileSize() }
            if (totalSize > 0) params.setSize(totalSize)
        } catch (e: Exception) {
            Log.w(TAG, "setSize failed (non-critical)", e)
        }
    }

    private fun configurePublicSessionParams(params: PackageInstaller.SessionParams) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            params.setInstallReason(PackageManager.INSTALL_REASON_USER)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            params.setPackageSource(PackageInstaller.PACKAGE_SOURCE_LOCAL_FILE)
        }
    }

    private fun applyAppManagerInstallFlags(params: PackageInstaller.SessionParams): Boolean {
        val appManagerFlags = buildAppManagerInstallFlags()
        return try {
            val field = params.javaClass.getField("installFlags")
            val existingFlags = field.getInt(params)
            val updatedFlags = existingFlags or appManagerFlags
            field.setInt(params, updatedFlags)
            Log.i(TAG, "Applied AppManager install flags: 0x${updatedFlags.toString(16)}")
            true
        } catch (e: Throwable) {
            Log.w(TAG, "Could not apply hidden AppManager install flags", e)
            false
        }
    }

    private fun buildAppManagerInstallFlags(): Int {
        var flags = INSTALL_REPLACE_EXISTING or INSTALL_ALLOW_TEST or INSTALL_FROM_ADB
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            flags = flags or INSTALL_FULL_APP
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            flags = flags or INSTALL_REQUEST_DOWNGRADE or INSTALL_ALLOW_DOWNGRADE
        } else {
            flags = flags or INSTALL_REQUEST_DOWNGRADE
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            flags = flags or INSTALL_BYPASS_LOW_TARGET_SDK_BLOCK
        }
        return flags
    }

    private fun cleanOldSessions(installer: PackageInstaller) {
        try {
            for (info in installer.mySessions) {
                runCatching { installer.abandonSession(info.sessionId) }
                    .onFailure { Log.w(TAG, "Unable to abandon old session ${info.sessionId}", it) }
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Could not clean old installer sessions", e)
        }
    }

    private fun InstallResult.isAlreadyExistsFailure(): Boolean {
        if (this !is InstallResult.Failure) return false
        return statusCode == -1 || message.contains("ALREADY_EXISTS", ignoreCase = true)
    }

    /**
     * Unwrap a RuntimeException to find the root cause.
     * Android's ExceptionUtils.wrap() wraps IOException in IllegalStateException.
     */
    private fun unwrapException(e: Throwable): Throwable {
        var current: Throwable = e
        var depth = 0
        while (current.cause != null && depth < 5) {
            current = current.cause!!
            depth++
        }
        return current
    }

    /**
     * Classify an exception into the appropriate status code.
     * If the root cause is an IOException, it's a session write error.
     * Otherwise, it's a session create/commit error.
     */
    private fun classifyException(e: Throwable, rootCause: Throwable): Int {
        if (e is IOException || rootCause is IOException) {
            return InstallResult.STATUS_FAILURE_SESSION_WRITE
        }
        if (e is SecurityException || rootCause is SecurityException) {
            return InstallResult.STATUS_FAILURE_SESSION_CREATE
        }
        return InstallResult.STATUS_FAILURE_SESSION_CREATE
    }

    /** Build a detailed error message from the exception chain. */
    private fun buildErrorMessage(e: Throwable, rootCause: Throwable): String {
        val sb = StringBuilder()
        sb.append(e.javaClass.simpleName)
        if (e.message != null) {
            sb.append(": ").append(e.message)
        }
        if (rootCause !== e && rootCause.message != null) {
            sb.append(" (cause: ").append(rootCause.javaClass.simpleName)
                .append(": ").append(rootCause.message).append(")")
        }
        return sb.toString()
    }

    private fun writeEntryToSession(apkFile: ApkFile, entry: ApkFile.Entry) {
        val sourceFile = apkFile.getCachedEntryFile(entry)
        val sessionName = entry.getSessionFileName()
        Log.d(TAG, "Writing entry '${entry.name}' -> '$sessionName' (${sourceFile.length()} bytes)")
        session!!.openWrite(sessionName, 0, sourceFile.length()).use { out ->
            FileInputStream(sourceFile).use { input ->
                val buf = ByteArray(BUFFER_SIZE)
                while (true) {
                    val n = input.read(buf)
                    if (n == -1) break
                    out.write(buf, 0, n)
                }
            }
            session!!.fsync(out)
        }
    }

    private fun ApkFile.Entry.getSessionFileName(): String {
        return if (fileName.endsWith(".apk", ignoreCase = true)) {
            fileName.substringAfterLast('/')
        } else {
            "$name.apk"
        }
    }

    /**
     * Copy OBB files from the ApkFile to /sdcard/Android/obb/<packageName>/.
     * May fail on Android 11+ due to scoped storage restrictions.
     */
    fun copyObb(apkFile: ApkFile, packageName: String): Boolean {
        val obbDir = File(
            Environment.getExternalStorageDirectory(),
            "Android/obb/$packageName"
        )
        return try {
            apkFile.extractObb(obbDir)
            true
        } catch (e: IOException) {
            Log.e(TAG, "OBB extraction failed", e)
            false
        }
    }

    /** Abandon the current session if active. */
    fun abandon() {
        session?.let { s ->
            runCatching { s.abandon() }
            session = null
        }
    }

    private fun closeSession() {
        session?.let { s ->
            runCatching { s.close() }
            session = null
        }
    }

    private fun unregisterReceiver() {
        if (receiverRegistered) {
            runCatching { appContext.unregisterReceiver(receiver) }
            receiverRegistered = false
            receiver = null
        }
    }

    /** Clean up all resources. */
    fun cleanup() {
        abandon()
        unregisterReceiver()
    }
}
