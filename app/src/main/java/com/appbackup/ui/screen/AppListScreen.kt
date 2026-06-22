package com.appbackup.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.appbackup.data.model.AppInfo
import com.appbackup.ui.component.AppCard
import com.appbackup.ui.component.BackupProgressDialog
import com.appbackup.viewmodel.AppListViewModel
import com.appbackup.viewmodel.BackupState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppListScreen(
    viewModel: AppListViewModel,
    onConfigureWebDav: () -> Unit
) {
    val appList by viewModel.appList.collectAsState()
    val backupState by viewModel.backupState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadApps()
    }

    when (val state = backupState) {
        is BackupState.InProgress -> {
            BackupProgressDialog(
                message = state.currentApp,
                progress = state.progress,
                onCancel = { viewModel.resetBackupState() }
            )
        }
        is BackupState.Completed -> {
            AlertDialog(
                onDismissRequest = { viewModel.resetBackupState() },
                title = { Text("备份完成") },
                text = {
                    Column {
                        state.results.forEach { (app, result) ->
                            Text("${app.name}: $result")
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { viewModel.resetBackupState() }) {
                        Text("确定")
                    }
                }
            )
        }
        is BackupState.Error -> {
            AlertDialog(
                onDismissRequest = { viewModel.resetBackupState() },
                title = { Text("备份出错") },
                text = { Text(state.message) },
                confirmButton = {
                    TextButton(onClick = { viewModel.resetBackupState() }) {
                        Text("确定")
                    }
                }
            )
        }
        else -> {}
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("APP 备份") },
                actions = {
                    IconButton(onClick = onConfigureWebDav) {
                        Icon(Icons.Default.Settings, contentDescription = "配置")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { viewModel.backupSelected() },
                icon = { Icon(Icons.Default.CloudUpload, contentDescription = null) },
                text = { Text("备份选中到 WebDAV") }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val selectedCount = appList.count { it.isSelected }
                Checkbox(
                    checked = selectedCount == appList.size && appList.isNotEmpty(),
                    onCheckedChange = { viewModel.selectAll(it) }
                )
                Text("全选（已选 $selectedCount/${appList.size}）")
            }
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(appList, key = { it.packageName }) { app ->
                    AppCard(
                        app = app,
                        onToggle = { viewModel.toggleSelect(app.packageName) }
                    )
                }
            }
        }
    }
}
