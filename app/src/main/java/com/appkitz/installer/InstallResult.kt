// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2020-2024 Muntashir Al-Islam (AppManager)
// Copyright (C) 2025 Appkitz Contributors

package com.appkitz.installer

/**
 * Represents the state and result of an installation operation.
 * Ported from AppManager's PackageInstallerCompat status handling.
 */
sealed class InstallResult {
    /** No installation has been started yet. */
    data object Idle : InstallResult()

    /** Installation is in progress. [progress] is 0..1, [currentFile] is the name being written. */
    data class InProgress(
        val progress: Float,
        val currentFile: String? = null
    ) : InstallResult()

    /** Installation completed successfully. */
    data object Success : InstallResult()

    /** Installation failed. [statusCode] is the PackageInstaller status code, [message] is a human-readable message. */
    data class Failure(
        val statusCode: Int,
        val message: String
    ) : InstallResult()

    companion object {
        // PackageInstaller status codes (from android.content.pm.PackageInstaller)
        // Source: https://developer.android.com/reference/android/content/pm/PackageInstaller
        const val STATUS_PENDING_USER_ACTION = -1
        const val STATUS_SUCCESS = 0
        const val STATUS_FAILURE = 1
        const val STATUS_FAILURE_BLOCKED = 2
        const val STATUS_FAILURE_ABORTED = 3
        const val STATUS_FAILURE_INVALID = 4
        const val STATUS_FAILURE_CONFLICT = 5
        const val STATUS_FAILURE_STORAGE = 6
        const val STATUS_FAILURE_INCOMPATIBLE = 7

        // Internal status codes (100+ to avoid conflict with PackageInstaller codes)
        const val STATUS_FAILURE_SESSION_CREATE = 100
        const val STATUS_FAILURE_SESSION_WRITE = 101
        const val STATUS_FAILURE_SESSION_COMMIT = 102
        const val STATUS_FAILURE_SESSION_ABANDON = 103
        const val STATUS_FAILURE_OBB = 104
        const val STATUS_FAILURE_INSTALLED = 105
    }
}
