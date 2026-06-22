package com.appkitz.installer

import android.os.Build
import org.json.JSONObject
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

data class SplitEntry(
    val fileName: String,
    val type: String,
    val label: String,
    val isRequired: Boolean = false,
    val isDefault: Boolean = false
)

object SplitApkParser {

    private val DEVICE_ABIS = Build.SUPPORTED_ABIS.toSet()
    private val DENSITY_MAP = mapOf(
        "ldpi" to 120, "mdpi" to 160, "hdpi" to 240, "xhdpi" to 320,
        "xxhdpi" to 480, "xxxhdpi" to 640, "tvdpi" to 213
    )
    private val ABI_MAP = mapOf(
        "arm64_v8a" to "arm64-v8a", "armeabi_v7a" to "armeabi-v7a",
        "arm64" to "arm64-v8a", "armeabi" to "armeabi-v7a",
        "x86_64" to "x86_64", "x86" to "x86"
    )

    fun parse(file: File): List<SplitEntry> {
        ZipFile(file).use { zip ->
            val manifest = findManifest(zip)
            if (manifest != null) {
                val json = JSONObject(zip.getInputStream(manifest).bufferedReader().readText())
                val parsed = parseJsonManifest(json)
                if (parsed.isNotEmpty()) return parsed
            }
            return parseByNaming(zip)
        }
    }

    private fun findManifest(zip: ZipFile): ZipEntry? {
        val entries = zip.entries()
        while (entries.hasMoreElements()) {
            val entry = entries.nextElement()
            val name = entry.name.substringAfterLast('/')
            if (name == "manifest.json") return entry
        }
        return null
    }

    fun isSplitPackage(file: File): Boolean {
        val name = file.name.lowercase()
        return name.endsWith(".apks") || name.endsWith(".xapk") || name.endsWith(".apkm")
    }

    private fun parseJsonManifest(json: JSONObject): List<SplitEntry> {
        val result = mutableListOf<SplitEntry>()

        val splits = json.optJSONArray("splits")
        if (splits != null) {
            for (i in 0 until splits.length()) {
                val split = splits.getJSONObject(i)
                val name = split.getString("name")
                val id = split.optString("id", "")
                val type = split.optInt("type", -1)
                result.add(parseSplitType(if (id.isNotEmpty()) id else name, type, split))
            }
            return result
        }

        val apkInfo = json.optJSONArray("apk_info")
        if (apkInfo != null) {
            for (i in 0 until apkInfo.length()) {
                val info = apkInfo.getJSONObject(i)
                val name = info.getString("apk")
                result.add(SplitEntry(name, "split", name, isDefault = true))
            }
            return result
        }

        return result
    }

    private fun parseSplitType(name: String, type: Int, split: JSONObject): SplitEntry {
        return when (type) {
            0 -> SplitEntry(name, "base", "基础 APK", isRequired = true, isDefault = true)
            1 -> {
                val abi = split.optString("abi", "")
                val matched = if (abi.isNotEmpty()) DEVICE_ABIS.any { abi.contains(it, ignoreCase = true) } else true
                SplitEntry(name, "abi", "$abi 代码", isDefault = matched)
            }
            2 -> {
                val density = split.optString("density", "")
                SplitEntry(name, "density", "$density 资源", isDefault = true)
            }
            3 -> {
                val locale = split.optString("locale", "")
                val lang = when {
                    locale.startsWith("zh") -> "中文"
                    locale.startsWith("en") -> "English"
                    locale.startsWith("ja") -> "日本語"
                    locale.startsWith("ko") -> "한국어"
                    else -> locale
                }
                SplitEntry(name, "locale", "$lang 语言", isDefault = true)
            }
            else -> SplitEntry(name, "feature", split.optString("id", name), isDefault = true)
        }
    }

    private fun parseByNaming(zip: ZipFile): List<SplitEntry> {
        val result = mutableListOf<SplitEntry>()
        val entries = zip.entries()
        while (entries.hasMoreElements()) {
            val entry = entries.nextElement()
            if (entry.isDirectory) continue
            val path = entry.name
            val name = path.substringAfterLast('/')
            if (!name.endsWith(".apk")) continue

            val lower = name.lowercase()
            when {
                lower == "base.apk" || lower.startsWith("base") ->
                    result.add(SplitEntry(path, "base", "基础 APK", isRequired = true, isDefault = true))
                lower.startsWith("split_config.") || lower.startsWith("config.") -> {
                    val configPart = name.substringAfter("config.").substringBefore(".apk")
                    parseSplitConfig(configPart, path)?.let { result.add(it) }
                }
                lower.contains("arm64") || lower.contains("armv8") ->
                    result.add(SplitEntry(path, "abi", "arm64-v8a 代码", isDefault = DEVICE_ABIS.any { it.contains("arm64") }))
                lower.contains("armeabi") || lower.contains("armv7") ->
                    result.add(SplitEntry(path, "abi", "armeabi-v7a 代码", isDefault = DEVICE_ABIS.any { it.contains("armeabi") }))
                lower.contains("x86_64") ->
                    result.add(SplitEntry(path, "abi", "x86_64 代码", isDefault = DEVICE_ABIS.any { it.contains("x86_64") }))
                lower.contains("x86") && !lower.contains("x86_64") ->
                    result.add(SplitEntry(path, "abi", "x86 代码", isDefault = DEVICE_ABIS.any { it.contains("x86") }))
                lower.contains("dpi") || lower.contains("ldpi") || lower.contains("mdpi") || lower.contains("hdpi") ->
                    result.add(SplitEntry(path, "density", name, isDefault = true))
                lower.matches(Regex(".*[a-z]{2}(-[a-zA-Z]{2})?\\.apk")) && lower.length < 20 ->
                    result.add(SplitEntry(path, "locale", "${name.substringBeforeLast('.')} 语言", isDefault = true))
                else -> result.add(SplitEntry(path, "unknown", name, isDefault = true))
            }
        }
        if (result.none { it.type == "base" } && result.any { it.type != "base" }) {
            result.add(0, SplitEntry("base.apk", "base", "基础 APK", isRequired = true, isDefault = true))
        }
        return result
    }

    private fun parseSplitConfig(configPart: String, path: String): SplitEntry? {
        val abiMatch = ABI_MAP.entries.find { configPart.contains(it.key, ignoreCase = true) }
        if (abiMatch != null) {
            val matched = DEVICE_ABIS.any { it == abiMatch.value }
            return SplitEntry(path, "abi", "${abiMatch.value} 代码", isDefault = matched)
        }
        val densityKey = DENSITY_MAP.keys.find { configPart.contains(it) }
        if (densityKey != null) {
            return SplitEntry(path, "density", "$densityKey(${DENSITY_MAP[densityKey]} DPI) 资源", isDefault = true)
        }
        val localeMatch = Regex("^(?:[a-z]{2})(?:-r?[A-Z]{2})?$").find(configPart)
        if (localeMatch != null) {
            val locale = localeMatch.value
            val lang = when {
                locale.startsWith("zh") -> "中文"
                locale.startsWith("en") -> "English"
                locale.startsWith("ja") -> "日本語"
                locale.startsWith("ko") -> "한국어"
                else -> locale
            }
            return SplitEntry(path, "locale", "$lang 语言", isDefault = true)
        }
        return SplitEntry(path, "unknown", configPart, isDefault = true)
    }
}