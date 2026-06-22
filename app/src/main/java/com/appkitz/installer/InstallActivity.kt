package com.appkitz.installer

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.appkitz.ui.theme.AppkitzTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipFile

class InstallActivity : ComponentActivity() {

    companion object {
        const val ACTION_INSTALL_RESULT = "com.appkitz.INSTALL_RESULT"
    }

    private val installReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
            val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
            val toast = if (status == PackageInstaller.STATUS_SUCCESS) {
                "安装成功"
            } else {
                "安装失败: ${message ?: "未知错误"}"
            }
            Toast.makeText(context, toast, Toast.LENGTH_LONG).show()
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        registerReceiver(installReceiver, IntentFilter(ACTION_INSTALL_RESULT), if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) RECEIVER_NOT_EXPORTED else 0)

        val uri = when (intent?.action) {
            Intent.ACTION_SEND -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java) else intent.getParcelableExtra(Intent.EXTRA_STREAM)
            else -> intent?.data
        } ?: run {
            Toast.makeText(this, "未指定文件", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setContent {
            AppkitzTheme {
                InstallScreen(uri = uri, onDone = { finish() })
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try { unregisterReceiver(installReceiver) } catch (_: Exception) {}
    }
}

@Composable
fun InstallScreen(uri: Uri, onDone: () -> Unit) {
    val context = LocalContext.current
    var cacheFile by remember { mutableStateOf<File?>(null) }
    var entries by remember { mutableStateOf<List<SplitEntry>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var installing by remember { mutableStateOf(false) }
    var installError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(uri) {
        withContext(Dispatchers.IO) {
            try {
                val ext = uri.pathSegments.lastOrNull()?.substringAfterLast('.', "") ?: ""
                val cacheName = "install_${System.nanoTime()}" + if (ext.isNotEmpty()) ".$ext" else ""
                val file = File(context.cacheDir, cacheName)
                val inputStream = context.contentResolver.openInputStream(uri)
                if (inputStream == null) {
                    error = "无法读取文件"
                    return@withContext
                }
                inputStream.use { input ->
                    FileOutputStream(file).use { output ->
                        input.copyTo(output)
                    }
                }
                if (file.length() == 0L) {
                    file.delete()
                    error = "文件为空"
                    return@withContext
                }
                cacheFile = file
                entries = if (SplitApkParser.isSplitPackage(file)) {
                    SplitApkParser.parse(file)
                } else {
                    listOf(SplitEntry(file.name, "base", "基础 APK", isRequired = true, isDefault = true))
                }
            } catch (e: Exception) {
                error = e.message ?: "解析失败"
            }
        }
    }

    installError?.let {
        AlertDialog(
            onDismissRequest = onDone,
            title = { Text("安装失败") },
            text = { Text(it) },
            confirmButton = { TextButton(onClick = onDone) { Text("确定") } }
        )
        return
    }

    error?.let {
        AlertDialog(
            onDismissRequest = onDone,
            title = { Text("错误") },
            text = { Text(it) },
            confirmButton = { TextButton(onClick = onDone) { Text("确定") } }
        )
        return
    }

    entries?.let { apkEntries ->
        if (!installing) {
            InstallDialog(
                entries = apkEntries,
                onInstall = { selected ->
                    installing = true
                    val file = cacheFile
                    if (file == null) {
                        installError = "文件不存在"
                        installing = false
                        return@InstallDialog
                    }
                    scope.launch(Dispatchers.IO) {
                        installApks(context, file, selected) { msg ->
                            installError = msg
                            installing = false
                        }
                    }
                },
                onDismiss = onDone
            )
        } else {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(16.dp))
                    Text("正在安装...")
                }
            }
        }
    } ?: run {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    }
}

@Composable
fun InstallDialog(
    entries: List<SplitEntry>,
    onInstall: (List<SplitEntry>) -> Unit,
    onDismiss: () -> Unit
) {
    var selected by remember { mutableStateOf(entries.filter { it.isDefault }.toSet()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择安装选项") },
        text = {
            Column {
                entries.forEach { entry ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 4.dp)
                    ) {
                        Checkbox(
                            checked = entry in selected,
                            onCheckedChange = { checked ->
                                selected = if (checked) selected + entry else selected - entry
                            },
                            enabled = !entry.isRequired
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(entry.label, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onInstall(selected.toList()) }) { Text("安装") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

private fun installApks(context: Context, file: File, selected: List<SplitEntry>, onError: (String) -> Unit) {
    if (!file.exists()) {
        onError("文件不存在")
        return
    }

    var session: PackageInstaller.Session? = null
    var tempBaseApk: File? = null
    try {
        val packageInstaller = context.packageManager.packageInstaller
        val sessionParams = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)

        val baseEntry = selected.find { it.type == "base" }
        if (baseEntry != null) {
            ZipFile(file).use { zip ->
                var ze = zip.getEntry(baseEntry.fileName)
                if (ze == null) {
                    val entries = zip.entries()
                    while (entries.hasMoreElements()) {
                        val e = entries.nextElement()
                        if (e.name.endsWith("/${baseEntry.fileName}") || e.name == baseEntry.fileName) {
                            ze = e
                            break
                        }
                    }
                }
                if (ze != null) {
                    tempBaseApk = File(context.cacheDir, "base_${System.nanoTime()}.apk")
                    zip.getInputStream(ze).use { input ->
                        FileOutputStream(tempBaseApk!!).use { output ->
                            input.copyTo(output)
                        }
                    }
                    val pkgInfo = context.packageManager.getPackageArchiveInfo(tempBaseApk!!.absolutePath, 0)
                    if (pkgInfo != null) {
                        sessionParams.setAppPackageName(pkgInfo.packageName)
                    }
                }
            }
        }

        val sessionId = packageInstaller.createSession(sessionParams)
        session = packageInstaller.openSession(sessionId)

        ZipFile(file).use { zip ->
            for (entry in selected) {
                var ze = zip.getEntry(entry.fileName)
                if (ze == null) {
                    val entries = zip.entries()
                    while (entries.hasMoreElements()) {
                        val e = entries.nextElement()
                        if (e.name.endsWith("/${entry.fileName}") || e.name == entry.fileName) {
                            ze = e
                            break
                        }
                    }
                }
                if (ze == null) continue
                var actualSize = ze.size
                if (actualSize < 0) {
                    val tempFile = File(context.cacheDir, "tmp_${System.nanoTime()}")
                    zip.getInputStream(ze).use { input ->
                        FileOutputStream(tempFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                    actualSize = tempFile.length()
                    tempFile.inputStream().use { input ->
                        session.openWrite(entry.fileName, 0, actualSize).use { out ->
                            input.copyTo(out)
                            session.fsync(out)
                        }
                    }
                    tempFile.delete()
                } else {
                    zip.getInputStream(ze).use { input ->
                        session.openWrite(entry.fileName, 0, actualSize).use { out ->
                            input.copyTo(out)
                            session.fsync(out)
                        }
                    }
                }
            }
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context, sessionId,
            Intent(InstallActivity.ACTION_INSTALL_RESULT),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        session.commit(pendingIntent.intentSender)
    } catch (e: Exception) {
        try { session?.abandon() } catch (_: Exception) {}
        onError(e.message ?: "未知错误")
    } finally {
        tempBaseApk?.delete()
    }
}