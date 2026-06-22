package com.appkitz.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.appkitz.R
import com.appkitz.viewmodel.ConnectionState
import com.appkitz.viewmodel.WebDavViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebDavConfigScreen(
    viewModel: WebDavViewModel,
    onConfigured: () -> Unit
) {
    val savedConfig by viewModel.savedConfig.collectAsState()
    val connectionState by viewModel.connectionState.collectAsState()

    var url by remember { mutableStateOf(savedConfig?.url ?: "") }
    var username by remember { mutableStateOf(savedConfig?.username ?: "") }
    var password by remember { mutableStateOf(savedConfig?.password ?: "") }

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.resetConnectionState()
    }

    LaunchedEffect(connectionState) {
        when (val state = connectionState) {
            is ConnectionState.Success -> {
                snackbarHostState.showSnackbar(state.message)
                viewModel.saveConfig(url, username, password)
                onConfigured()
            }
            is ConnectionState.Error -> {
                snackbarHostState.showSnackbar(state.message)
            }
            else -> {}
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                TopAppBar(title = { Text(stringResource(R.string.webdav_config)) })
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text(stringResource(R.string.server_url)) },
                    placeholder = { Text(stringResource(R.string.server_url_placeholder)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text(stringResource(R.string.username)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(stringResource(R.string.password)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = { viewModel.testConnection(url, username, password) },
                    enabled = connectionState !is ConnectionState.Testing,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (connectionState is ConnectionState.Testing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(stringResource(R.string.test_connection))
                }
                Spacer(Modifier.height(12.dp))
                FilledTonalButton(
                    onClick = {
                        viewModel.saveConfig(url, username, password)
                        onConfigured()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = url.isNotBlank() && username.isNotBlank() && password.isNotBlank()
                ) {
                    Text(stringResource(R.string.save_and_start))
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
        )
    }
}
