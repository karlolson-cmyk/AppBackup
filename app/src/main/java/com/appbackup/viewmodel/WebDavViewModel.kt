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
