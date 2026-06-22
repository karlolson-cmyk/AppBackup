package com.appbackup.ui.screen

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.appbackup.data.model.AppInfo
import com.appbackup.ui.component.AppCard
import com.appbackup.ui.component.BackupProgressDialog
import com.appbackup.viewmodel.AppListViewModel
import com.appbackup.viewmodel.BackupState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppListScreen(
    viewModel: AppListViewModel,
    onConfigureWebDav: () -> Unit
) {
    val fullAppList by viewModel.appList.collectAsState()
    val filteredAppList by viewModel.filteredAppList.collectAsState()
    val backupState by viewModel.backupState.collectAsState()

    var isSearchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var isFabVisible by remember { mutableStateOf(true) }

    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.loadApps()
    }

    var previousTotalScroll by remember { mutableIntStateOf(0) }
    LaunchedEffect(listState) {
        snapshotFlow {
            val info = listState.layoutInfo.visibleItemsInfo.firstOrNull()
            if (info != null) info.index * 10000 + info.offset else 0
        }.collect { current ->
            if (current > previousTotalScroll) {
                isFabVisible = false
            } else if (current < previousTotalScroll) {
                isFabVisible = true
            }
            previousTotalScroll = current
        }
    }

    fun copyPackageName(pkg: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("package_name", pkg))
        scope.launch {
            val showJob = launch {
                snackbarHostState.showSnackbar("已复制包名：$pkg", duration = SnackbarDuration.Indefinite)
            }
            delay(2000)
            snackbarHostState.currentSnackbarData?.dismiss()
            showJob.join()
        }
    }

    when (val state = backupState) {
        is BackupState.InProgress -> {
            BackupProgressDialog(
                message = state.currentApp,
                progress = state.progress,
                onCancel = { viewModel.cancelBackup() }
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

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            snackbarHost = {
                Box(Modifier.fillMaxSize()) {
                    SnackbarHost(
                        hostState = snackbarHostState,
                        modifier = Modifier.align(Alignment.TopCenter)
                    )
                }
            },
            topBar = {
                TopAppBar(
                    title = {
                        if (isSearchActive) {
                            TextField(
                                value = searchQuery,
                                onValueChange = { query ->
                                    searchQuery = query
                                    viewModel.setSearchQuery(query)
                                },
                                placeholder = { Text("搜索应用...") },
                                singleLine = true,
                                colors = TextFieldDefaults.colors(
                                    unfocusedContainerColor = Color.Transparent,
                                    focusedContainerColor = Color.Transparent
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            Text("APP 备份")
                        }
                    },
                    actions = {
                        if (isSearchActive) {
                            IconButton(onClick = {
                                isSearchActive = false
                                searchQuery = ""
                                viewModel.setSearchQuery("")
                            }) {
                                Icon(Icons.Default.Close, contentDescription = "关闭搜索")
                            }
                        } else {
                            IconButton(onClick = { isSearchActive = true }) {
                                Icon(Icons.Default.Search, contentDescription = "搜索")
                            }
                            IconButton(onClick = onConfigureWebDav) {
                                Icon(Icons.Default.Settings, contentDescription = "配置")
                            }
                        }
                    }
                )
            },
            floatingActionButton = {
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    AnimatedVisibility(
                        visible = isFabVisible,
                        enter = slideInVertically { it },
                        exit = slideOutVertically { it }
                    ) {
                        ExtendedFloatingActionButton(
                            onClick = { viewModel.backupSelectedNoApk() },
                            icon = { Icon(Icons.Default.Cloud, contentDescription = null) },
                            text = { Text("备份到 WebDAV") }
                        )
                    }
                    AnimatedVisibility(
                        visible = isFabVisible,
                        enter = slideInVertically { it },
                        exit = slideOutVertically { it }
                    ) {
                        ExtendedFloatingActionButton(
                            onClick = { viewModel.backupSelected() },
                            icon = { Icon(Icons.Default.CloudUpload, contentDescription = null) },
                            text = { Text("备份到 WebDAV（含 APK）") }
                        )
                    }
                }
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
                    val fullSelectedCount = fullAppList.count { it.isSelected }
                    Checkbox(
                        checked = fullSelectedCount == fullAppList.size && fullAppList.isNotEmpty(),
                        onCheckedChange = { viewModel.selectAll(it) }
                    )
                    Text("全选（已选 $fullSelectedCount/${fullAppList.size}）")
                }
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val displayList = if (isSearchActive) filteredAppList else fullAppList
                    items(displayList, key = { it.packageName }) { app ->
                        AppCard(
                            app = app,
                            onToggle = { viewModel.toggleSelect(app.packageName) },
                            onLongClick = { copyPackageName(app.packageName) }
                        )
                    }
                }
            }
        }
    }
}
