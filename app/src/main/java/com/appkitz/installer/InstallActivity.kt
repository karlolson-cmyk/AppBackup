package com.appkitz.installer

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.appkitz.ui.theme.AppkitzTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

/**
 * Thin entry-point Activity launched by the package / .apks / .xapk intent
 * filters. It only:
 *   1. Copies the shared file into the cache,
 *   2. Parses the split layout (so the user can pick/drop splits),
 *   3. Hands the actual installation off to [InstallService] and finishes.
 *
 * All PackageInstaller result handling lives in [InstallResultReceiver]
 * (manifest-registered) and [InstallService] (foreground service), so this
 * Activity can be safely destroyed at any point without breaking the install.
 */
class InstallActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val uri = when (intent?.action) {
            Intent.ACTION_SEND ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                    intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                else
                    @Suppress("DEPRECATION") intent.getParcelableExtra(Intent.EXTRA_STREAM)
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
}

@Composable
fun InstallScreen(uri: Uri, onDone: () -> Unit) {
    val context = LocalContext.current
    var cacheFile by remember { mutableStateOf<File?>(null) }
    var entries by remember { mutableStateOf<List<SplitEntry>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var showPermissionDialog by remember { mutableStateOf(false) }
    var launching by remember { mutableStateOf(false) }

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
                    FileOutputStream(file).use { output -> input.copyTo(output) }
                }
                if (file.length() == 0L) {
                    file.delete()
                    error = "文件为空"
                    return@withContext
                }
                cacheFile = file
                entries = if (SplitApkParser.isSplitPackage(file)) {
                    val parsedEntries = SplitApkParser.parse(file)
                    if (parsedEntries.isEmpty()) {
                        file.delete()
                        error = "安装包中未找到 APK 文件"
                        return@withContext
                    }
                    parsedEntries
                } else {
                    listOf(SplitEntry(file.name, "base", "基础 APK", isRequired = true, isDefault = true))
                }
            } catch (e: Exception) {
                error = e.message ?: "解析失败"
            }
        }
    }

    if (showPermissionDialog) {
        AlertDialog(
            onDismissRequest = onDone,
            title = { Text("需要权限") },
            text = { Text("安装应用需要开启「安装未知应用」权限。\n请前往：设置 → 特殊权限 → 安装未知应用 → Appkitz → 允许") },
            confirmButton = {
                TextButton(onClick = {
                    Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).also {
                        it.data = android.net.Uri.parse("package:${context.packageName}")
                        context.startActivity(it)
                    }
                    showPermissionDialog = false
                }) { Text("去设置") }
            },
            dismissButton = {
                TextButton(onClick = { showPermissionDialog = false; onDone() }) { Text("取消") }
            }
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
        if (!launching) {
            InstallDialog(
                entries = apkEntries,
                onInstall = { selected ->
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                        !context.packageManager.canRequestPackageInstalls()
                    ) {
                        showPermissionDialog = true
                        return@InstallDialog
                    }
                    val file = cacheFile
                    if (file == null || !file.exists()) {
                        Toast.makeText(context, "文件不存在", Toast.LENGTH_SHORT).show()
                        return@InstallDialog
                    }
                    // Hand the work to the foreground service and let the
                    // system install confirmation UI take over.
                    launching = true
                    InstallService.start(context, file, selected)
                    onDone() // finish this Activity; the service + receiver carry on
                },
                onDismiss = onDone
            )
        } else {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
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
    val context = LocalContext.current
    val defaults = remember(entries) { computeSmartDefaults(context, entries) }
    var selected by remember { mutableStateOf(defaults) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择安装选项") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
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

private const val DENSITY_UNKNOWN = Integer.MAX_VALUE
private val DENSITY_VALUES = mapOf(
    "ldpi" to 120, "mdpi" to 160, "hdpi" to 240, "xhdpi" to 320,
    "xxhdpi" to 480, "xxxhdpi" to 640, "tvdpi" to 213
)

private fun computeSmartDefaults(context: android.content.Context, entries: List<SplitEntry>): Set<SplitEntry> {
    val result = mutableSetOf<SplitEntry>()
    val deviceAbis = Build.SUPPORTED_ABIS.toSet()
    val deviceDensity = context.resources.displayMetrics.densityDpi
    val deviceLocale = Locale.getDefault().language

    var bestAbi: SplitEntry? = null
    var bestDensity: SplitEntry? = null
    var bestDensityDiff = DENSITY_UNKNOWN
    var bestLocale: SplitEntry? = null
    val seenFeature = mutableSetOf<String>()

    for (entry in entries) {
        if (entry.isRequired) {
            result.add(entry)
            seenFeature.add(entry.type)
            continue
        }

        when (entry.type) {
            "base" -> {
                result.add(entry)
                seenFeature.add(entry.type)
            }
            "abi" -> {
                val match = deviceAbis.any { abi ->
                    entry.label.contains(abi, ignoreCase = true)
                }
                if (match && bestAbi == null) {
                    bestAbi = entry
                }
            }
            "density" -> {
                val entryDensity = DENSITY_VALUES.entries.find { d ->
                    entry.label.contains(d.key, ignoreCase = true)
                }?.value ?: 0
                if (entryDensity > 0) {
                    val diff = kotlin.math.abs(entryDensity - deviceDensity)
                    if (diff < bestDensityDiff) {
                        bestDensityDiff = diff
                        bestDensity = entry
                    }
                }
            }
            "locale" -> {
                if (entry.label.startsWith(deviceLocale, ignoreCase = true)) {
                    bestLocale = entry
                }
            }
            else -> {
                if (entry.type !in seenFeature) {
                    result.add(entry)
                    seenFeature.add(entry.type)
                }
            }
        }
    }

    bestAbi?.let { result.add(it) }
    bestDensity?.let { result.add(it) }
    bestLocale?.let { result.add(it) }

    return result
}
