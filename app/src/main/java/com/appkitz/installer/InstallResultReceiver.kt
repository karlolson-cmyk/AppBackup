// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2020-2024 Muntashir Al-Islam (AppManager)
// Copyright (C) 2025 Appkitz Contributors

package com.appkitz.installer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInstaller
import android.util.Log
import androidx.core.content.IntentCompat

/**
 * Receives the result of a PackageInstaller session commit.
 * Ported from AppManager's PackageInstallerCompat broadcast handling.
 */
class InstallResultReceiver(
    private val onResult: (InstallResult) -> Unit
) : BroadcastReceiver() {

    companion object {
        private const val TAG = "InstallResultReceiver"
        private const val EXTRA_LEGACY_STATUS = "android.content.pm.extra.LEGACY_STATUS"
        const val ACTION_INSTALL_RESULT = "com.appkitz.installer.INSTALL_RESULT"

        fun createIntentFilter(): IntentFilter =
            IntentFilter().apply { addAction(ACTION_INSTALL_RESULT) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (!intent.hasExtra(PackageInstaller.EXTRA_STATUS)) {
            Log.w(TAG, "Ignoring broadcast without EXTRA_STATUS")
            return
        }

        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, Int.MIN_VALUE)
        val legacyStatus = intent.getIntExtra(EXTRA_LEGACY_STATUS, Int.MIN_VALUE)
        val statusMessage = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
        val confirmIntent = IntentCompat.getParcelableExtra(
            intent, Intent.EXTRA_INTENT, Intent::class.java
        )

        val extras = intent.extras
        val extrasStr = extras?.keySet()?.joinToString { "$it=${extras.get(it)}" } ?: "null"
        Log.i(
            TAG,
            "Received install result: status=$status, legacy=$legacyStatus, " +
                "msg=$statusMessage, extras={$extrasStr}"
        )

        if (status == InstallResult.STATUS_PENDING_USER_ACTION && confirmIntent != null) {
            // A legacy install failure can also use -1. Treat -1 as pending
            // user action only when the system supplied a confirmation intent.
            Log.i(TAG, "Launching user confirmation activity")
            confirmIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(confirmIntent)
            return
        }

        if (confirmIntent != null) {
            Log.w(TAG, "Found confirmation intent with status=$status, launching anyway")
            confirmIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(confirmIntent)
            return
        }

        val result: InstallResult = when (status) {
            InstallResult.STATUS_SUCCESS -> InstallResult.Success
            else -> {
                val failureStatus = if (legacyStatus != Int.MIN_VALUE) legacyStatus else status
                val msg = statusMessage ?: statusToString(failureStatus)
                Log.w(TAG, "Install failed: status=$failureStatus, message=$msg")
                InstallResult.Failure(failureStatus, msg)
            }
        }
        onResult(result)
    }

    private fun statusToString(status: Int): String = when (status) {
        InstallResult.STATUS_FAILURE_BLOCKED -> "BLOCKED"
        InstallResult.STATUS_FAILURE_ABORTED -> "ABORTED"
        InstallResult.STATUS_FAILURE_INVALID -> "INVALID"
        InstallResult.STATUS_FAILURE_CONFLICT -> "CONFLICT"
        InstallResult.STATUS_FAILURE_STORAGE -> "STORAGE"
        InstallResult.STATUS_FAILURE_INCOMPATIBLE -> "INCOMPATIBLE"
        else -> describePackageManagerStatus(status)
    }

    private fun describePackageManagerStatus(status: Int): String {
        val description = when (status) {
            -1 -> "ALREADY_EXISTS"
            -2 -> "INVALID_APK"
            -3 -> "INVALID_URI"
            -4 -> "INSUFFICIENT_STORAGE"
            -5 -> "DUPLICATE_PACKAGE"
            -6 -> "NO_SHARED_USER"
            -7 -> "UPDATE_INCOMPATIBLE"
            -8 -> "SHARED_USER_INCOMPATIBLE"
            -9 -> "MISSING_SHARED_LIBRARY"
            -10 -> "REPLACE_COULDNT_DELETE"
            -11 -> "DECRYPT_ERROR"
            -12 -> "OLDER_SDK"
            -13 -> "CONFLICTING_PROVIDER"
            -14 -> "NEWER_SDK"
            -15 -> "MISSING_SHARED_LIBRARY"
            -16 -> "RESTRICTED_PERMISSIONS"
            -17 -> "CANNOT_INSTALL_ON_DATA"
            -18 -> "CANNOT_INSTALL_ON_SHARED"
            -19 -> "INSUFFICIENT_STORAGE_DATA"
            -20 -> "INSUFFICIENT_STORAGE_CACHE"
            -21 -> "FAILED_INTERNAL_ERROR"
            -22 -> "USER_RESTRICTED"
            -23 -> "OTHER"
            -24 -> "INSUFFICIENT_STORAGE_SHARED"
            -25 -> "MEDIA_UNAVAILABLE"
            -26 -> "VERIFICATION_FAILURE"
            -27 -> "VERIFICATION_TIMEOUT"
            -28 -> "FAILED_VERIFICATION_FAILURE"
            -29 -> "FAILED_DEXTER"
            -30 -> "FAILED_DEX_OPT"
            -31 -> "FAILED_DEX_OPT_CACHE"
            -32 -> "INSUFFICIENT_STORAGE_VERIFICATION"
            -100 -> "INCOMPATIBLE_WITH_EXISTING"
            -101 -> "INCOMPATIBLE_WITH_EXISTING_REINSTALL"
            -102 -> "CANNOT_OVERRIDE_INSTALLATION"
            -103 -> "CANNOT_DOWNGRADE"
            -104 -> "CANNOT_FORWARD_LOCKED"
            -105 -> "CANNOT_MOVE_TO_MEDIA"
            -106 -> "CANNOT_MOVE_TO_FORWARD_LOCKED"
            -107 -> "INVALID_INSTALL_LOCATION"
            -108 -> "INVALID_INSTALLER_PACKAGE"
            -109 -> "INCOMPATIBLE_WITH_VERIFICATION"
            -110 -> "INTERNAL_ERROR"
            -111 -> "USER_RESTRICTED"
            -112 -> "INCOMPATIBLE_WITH_SHARED_USER"
            -113 -> "CANNOT_SILENT_INSTALL"
            -114 -> "CANNOT_INSTALL_WHEN_CORE_USER"
            -115 -> "CANNOT_INSTALL_WHEN_DEVICE_DEVELOPER"
            -116 -> "CANNOT_INSTALL_WHEN_DEVICE_DEVELOPER_REINSTALL"
            -117 -> "CANNOT_INSTALL_WHEN_DEVICE_DEVELOPER_DOWNGRADE"
            -118 -> "CANNOT_INSTALL_WHEN_DEVICE_DEVELOPER_REINSTALL_DOWNGRADE"
            -119 -> "CANNOT_INSTALL_WHEN_DEVICE_DEVELOPER_DOWNGRADE_REINSTALL"
            -120 -> "CANNOT_INSTALL_WHEN_DEVICE_DEVELOPER_REINSTALL_DOWNGRADE_REINSTALL"
            else -> null
        }
        return if (description != null) "FAILURE($status: $description)" else "FAILURE($status)"
    }
}
