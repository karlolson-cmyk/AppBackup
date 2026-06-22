package com.appkitz.installer

import android.os.Build
import org.json.JSONObject
import java.io.File
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
        "x86_64" to "x86_64", "x86" to "x86"
    )

    fun parse(file: File): List<SplitEntry> {
        ZipFile(file).use { zip ->
            val manifest = zip.getEntry("manifest.json")
            if (manifest != null) {
                val json = JSONObject(zip.getInputStream(manifest).bufferedReader().readText())
                return parseJsonManifest(json)
            }
            return parseByNaming(zip)
        }
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
                val type = split.optInt("type", -1)
                result.add(parseSplitType(name, type, split))
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
                val matched = DEVICE_ABIS.any { abi.contains(it, ignoreCase = true) }
                SplitEntry(name, "abi", "$abi 代码", isDefault = matched)
            }
            2 -> {
                val density = split.optString("density", "")
                SplitEntry(name, "density", "$density 资源", isDefault = true)
            }
            3 -> {
                val locale = split.optString("locale", "")
                SplitEntry(name, "locale", "$locale 语言", isDefault = true)
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
            val name = entry.name.substringAfterLast('/')
            if (!name.endsWith(".apk")) continue

            when {
                name == "base.apk" -> result.add(SplitEntry(entry.name, "base", "基础 APK", isRequired = true, isDefault = true))
                name.contains("config.") -> {
                    val configPart = name.substringAfter("config.").substringBefore(".apk")
                    val abiMatch = ABI_MAP.entries.find { configPart.contains(it.key) }
                    if (abiMatch != null) {
                        val matched = DEVICE_ABIS.any { it == abiMatch.value }
                        result.add(SplitEntry(entry.name, "abi", "${abiMatch.value} 代码", isDefault = matched))
                    } else if (DENSITY_MAP.containsKey(configPart)) {
                        result.add(SplitEntry(entry.name, "density", "${configPart}(${DENSITY_MAP[configPart]} DPI)资源", isDefault = true))
                    } else if (configPart.matches(Regex("[a-z]{2}(-[a-zA-Z]{2})?"))) {
                        result.add(SplitEntry(entry.name, "locale", "${configPart}语言", isDefault = true))
                    } else {
                        result.add(SplitEntry(entry.name, "unknown", name, isDefault = true))
                    }
                }
                name.contains("arm64") || name.contains("armv8") ->
                    result.add(SplitEntry(entry.name, "abi", "arm64-v8a 代码", isDefault = DEVICE_ABIS.any { it.contains("arm64") }))
                name.contains("armeabi") || name.contains("armv7") ->
                    result.add(SplitEntry(entry.name, "abi", "armeabi-v7a 代码", isDefault = DEVICE_ABIS.any { it.contains("armeabi") }))
                name.contains("x86_64") ->
                    result.add(SplitEntry(entry.name, "abi", "x86_64 代码", isDefault = DEVICE_ABIS.any { it.contains("x86_64") }))
                name.contains("x86") ->
                    result.add(SplitEntry(entry.name, "abi", "x86 代码", isDefault = DEVICE_ABIS.any { it.contains("x86") }))
                else -> result.add(SplitEntry(entry.name, "unknown", name, isDefault = true))
            }
        }
        if (result.none { it.type == "base" } && result.any { it.type != "base" }) {
            result.add(0, SplitEntry("base.apk", "base", "基础 APK", isRequired = true, isDefault = true))
        }
        return result
    }
}
