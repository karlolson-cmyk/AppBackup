// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2020-2024 Muntashir Al-Islam (AppManager)
// Copyright (C) 2025 Appkitz Contributors

package com.appkitz.installer

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.core.content.IntentCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.appkitz.R
import com.appkitz.installer.ui.InstallProgressDialog
import com.appkitz.installer.ui.InstallResultDialog
import com.appkitz.installer.ui.SplitApkChooserScreen
import com.appkitz.installer.viewmodel.InstallerViewModel
import com.appkitz.ui.theme.AppkitzTheme

/**
 * Entry point Activity for the APK installer.
 * Receives VIEW/SEND intents with .apk/.apks/.apkm/.xapk files.
 *
 * Manifest registration includes intent-filters for:
 * - application/vnd.android.package-archive (standard APK MIME)
 * - application/vnd.apkm (APKM MIME)
 * - application/xapk-package-archive (XAPK MIME)
 * - path patterns for .apkm/.apks/.xapk extensions
 */
class InstallerActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val intent = intent ?: run { finish(); return }

        // Extract URI from VIEW or SEND intent
        val uri = intent.data
            ?: IntentCompat.getParcelableExtra(
                intent, Intent.EXTRA_STREAM, Uri::class.java
            )

        val mimeType = intent.type

        if (uri == null) {
            finish()
            return
        }

        setContent {
            AppkitzTheme {
                val viewModel: InstallerViewModel = viewModel()

                // Load the APK file once
                LaunchedEffect(uri) {
                    viewModel.loadApk(uri, mimeType)
                }

                InstallerContent(
                    viewModel = viewModel,
                    onFinish = { finish() }
                )
            }
        }
    }
}

@Composable
private fun InstallerContent(
    viewModel: InstallerViewModel,
    onFinish: () -> Unit
) {
    val state by viewModel.state.collectAsState()

    // Permission launcher for "Install unknown apps" settings
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        viewModel.onPermissionResult()
    }

    // Priority: error > loading > permission > install progress > install result > chooser
    when {
        state.error != null -> {
            AlertDialog(
                onDismissRequest = onFinish,
                title = { Text(stringResource(R.string.parse_error)) },
                text = {
                    Text(
                        text = state.error ?: "",
                        style = MaterialTheme.typography.bodyMedium
                    )
                },
                confirmButton = {
                    TextButton(onClick = onFinish) {
                        Text(stringResource(R.string.close))
                    }
                }
            )
        }

        state.isLoading -> {
            AlertDialog(
                onDismissRequest = {},
                title = { Text(stringResource(R.string.parsing_package)) },
                text = {},
                confirmButton = {}
            )
        }

        state.needsInstallPermission -> {
            AlertDialog(
                onDismissRequest = { viewModel.dismissPermissionRequest() },
                title = { Text(stringResource(R.string.install)) },
                text = {
                    Text(stringResource(R.string.request_install_permission))
                },
                confirmButton = {
                    TextButton(onClick = {
                        val intent = viewModel.getManageUnknownSourcesIntent()
                        permissionLauncher.launch(intent)
                    }) {
                        Text(stringResource(R.string.go_to_settings))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.dismissPermissionRequest() }) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            )
        }

        state.installState is InstallResult.InProgress -> {
            InstallProgressDialog(
                state = state.installState as InstallResult.InProgress,
                onCancel = { viewModel.cancelInstallation() }
            )
        }

        state.installState is InstallResult.Success -> {
            InstallResultDialog(
                result = state.installState,
                packageName = state.packageName,
                onDismiss = {
                    viewModel.resetInstallState()
                    onFinish()
                }
            )
        }

        state.installState is InstallResult.Failure -> {
            InstallResultDialog(
                result = state.installState,
                packageName = state.packageName,
                onDismiss = {
                    viewModel.resetInstallState()
                }
            )
        }

        else -> {
            // Show the split APK chooser
            SplitApkChooserScreen(
                state = state,
                onToggleSplit = { viewModel.toggleSplit(it) },
                onSearchQueryChange = { viewModel.updateSearchQuery(it) },
                onInstall = { viewModel.startInstallation() },
                onDismiss = onFinish
            )
        }
    }
}
