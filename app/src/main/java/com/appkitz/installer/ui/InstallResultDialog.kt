// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2020-2024 Muntashir Al-Islam (AppManager)
// Copyright (C) 2025 Appkitz Contributors

package com.appkitz.installer.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.appkitz.R
import com.appkitz.installer.InstallResult

@Composable
fun InstallResultDialog(
    result: InstallResult,
    packageName: String?,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    when (result) {
        is InstallResult.Success -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text(stringResource(R.string.install_success)) },
                text = {},
                confirmButton = {
                    TextButton(onClick = {
                        // Try to launch the installed app
                        packageName?.let { pkg ->
                            val launchIntent = context.packageManager
                                .getLaunchIntentForPackage(pkg)
                            if (launchIntent != null) {
                                context.startActivity(launchIntent)
                            }
                        }
                        onDismiss()
                    }) {
                        Text(stringResource(R.string.open))
                    }
                },
                dismissButton = {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.close))
                    }
                }
            )
        }

        is InstallResult.Failure -> {
            val messageResId = when (result.statusCode) {
                InstallResult.STATUS_FAILURE_BLOCKED ->
                    R.string.install_status_blocked
                InstallResult.STATUS_FAILURE_ABORTED ->
                    R.string.install_status_aborted
                InstallResult.STATUS_FAILURE_INVALID ->
                    R.string.install_status_invalid
                InstallResult.STATUS_FAILURE_CONFLICT ->
                    R.string.install_status_conflict
                InstallResult.STATUS_FAILURE_STORAGE ->
                    R.string.install_status_storage
                InstallResult.STATUS_FAILURE_INCOMPATIBLE ->
                    R.string.install_status_incompatible
                InstallResult.STATUS_FAILURE_SESSION_CREATE ->
                    R.string.install_status_session_create
                InstallResult.STATUS_FAILURE_SESSION_WRITE ->
                    R.string.install_status_session_write
                InstallResult.STATUS_FAILURE_SESSION_COMMIT ->
                    R.string.install_status_session_commit
                InstallResult.STATUS_FAILURE_SESSION_ABANDON ->
                    R.string.install_status_session_abandon
                else -> R.string.install_status_generic
            }
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text(stringResource(R.string.install_failed)) },
                text = {
                    Column {
                        Text(
                            text = stringResource(messageResId),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        // Show the specific error message from the system
                        if (!result.message.isBlank()) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = result.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.close))
                    }
                }
            )
        }

        else -> { /* Idle, InProgress — not shown as result dialog */ }
    }
}
