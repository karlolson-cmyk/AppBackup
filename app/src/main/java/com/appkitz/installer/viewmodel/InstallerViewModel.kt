// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2020-2024 Muntashir Al-Islam (AppManager)
// Copyright (C) 2025 Appkitz Contributors

package com.appkitz.installer.viewmodel

import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.appkitz.installer.InstallResult
import com.appkitz.installer.PackageInstallerCompat
import com.appkitz.installer.apk.ApkFile
import com.appkitz.installer.splitapk.SplitApkSelector
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel for the APK installer flow.
 * Manages parsing, split selection, and installation execution.
 */
class InstallerViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "InstallerVM"
    }

    private val packageInstallerCompat = PackageInstallerCompat(application)
    private var apkFile: ApkFile? = null
    private var selector: SplitApkSelector? = null
    private var installJob: Job? = null

    private val _state = MutableStateFlow(InstallerState())
    val state: StateFlow<InstallerState> = _state.asStateFlow()

    fun loadApk(uri: Uri, mimeType: String?) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            try {
                val file = ApkFile.createInstance(getApplication(), uri, mimeType)
                apkFile = file
                val entries = file.getEntries()
                val sel = SplitApkSelector(entries)
                selector = sel

                // Check if app is already installed to match existing splits
                val installedSplits = getInstalledSplits(file.getPackageName())
                val initialSelections = sel.getInitialSelections(installedSplits)

                _state.update {
                    it.copy(
                        isLoading = false,
                        entries = entries,
                        selectedNames = initialSelections,
                        disabledNames = sel.disabledEntryNames,
                        packageName = file.getPackageName(),
                        hasObb = file.hasObb()
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse APK", e)
                _state.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "Parse error"
                    )
                }
            }
        }
    }

    fun toggleSplit(name: String) {
        val sel = selector ?: return
        val newSelection = sel.toggle(name) ?: return
        _state.update { it.copy(selectedNames = newSelection) }
    }

    fun updateSearchQuery(query: String) {
        _state.update { it.copy(searchQuery = query) }
    }

    fun startInstallation() {
        if (!packageInstallerCompat.canRequestPackageInstalls()) {
            _state.update { it.copy(needsInstallPermission = true) }
            return
        }
        val file = apkFile ?: return
        val sel = selector ?: return
        val selectedEntries = sel.getSelectedEntries()
        if (selectedEntries.isEmpty()) return

        installJob = viewModelScope.launch {
            _state.update { it.copy(installState = InstallResult.InProgress(0f)) }
            val result = packageInstallerCompat.install(
                apkFile = file,
                selectedEntries = selectedEntries
            ) { progress, currentFile ->
                _state.update {
                    it.copy(installState = InstallResult.InProgress(progress, currentFile))
                }
            }
            _state.update { it.copy(installState = result) }
        }
    }

    fun cancelInstallation() {
        installJob?.cancel()
        packageInstallerCompat.cleanup()
        _state.update { it.copy(installState = InstallResult.Idle) }
    }

    fun onPermissionResult() {
        _state.update { it.copy(needsInstallPermission = false) }
        if (packageInstallerCompat.canRequestPackageInstalls()) {
            startInstallation()
        }
    }

    fun dismissPermissionRequest() {
        _state.update { it.copy(needsInstallPermission = false) }
    }

    /** Returns the intent to open system settings for granting install permission. */
    fun getManageUnknownSourcesIntent(): Intent =
        packageInstallerCompat.getManageUnknownSourcesIntent()

    fun resetInstallState() {
        _state.update { it.copy(installState = InstallResult.Idle) }
    }

    @Suppress("DEPRECATION")
    private fun getInstalledSplits(packageName: String): Set<String>? {
        return try {
            val pm = getApplication<Application>().packageManager
            val flags = PackageManager.MATCH_UNINSTALLED_PACKAGES
            val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(flags.toLong()))
            } else {
                pm.getPackageInfo(packageName, flags)
            }
            info.splitNames?.toSet()
        } catch (e: Exception) {
            null // App not installed or not visible
        }
    }

    override fun onCleared() {
        super.onCleared()
        packageInstallerCompat.cleanup()
        apkFile?.close()
    }
}

data class InstallerState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val entries: List<ApkFile.Entry> = emptyList(),
    val selectedNames: Set<String> = emptySet(),
    val disabledNames: Set<String> = emptySet(),
    val searchQuery: String = "",
    val installState: InstallResult = InstallResult.Idle,
    val packageName: String? = null,
    val hasObb: Boolean = false,
    val needsInstallPermission: Boolean = false
)
