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
