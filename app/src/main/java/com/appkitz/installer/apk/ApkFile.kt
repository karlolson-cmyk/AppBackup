// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2020-2024 Muntashir Al-Islam (AppManager)
// Copyright (C) 2025 Appkitz Contributors

package com.appkitz.installer.apk

import android.content.Context
import android.net.Uri
import android.os.Build
import android.text.format.Formatter
import android.util.Log
import com.appkitz.installer.apk.ApkUtils.getManifestAttributes
import com.appkitz.installer.apk.ApkUtils.getManifestFromApk
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.util.Locale
import java.util.Objects
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

/**
 * Parses APK / APKS / APKM / XAPK files and classifies split entries.
 * Ported from AppManager's ApkFile.java.
 */
class ApkFile private constructor(
    private val mCacheFilePath: File
) : AutoCloseable {

    companion object {
        private const val TAG = "ApkFile"

        const val APK_BASE = 0
        const val APK_SPLIT_FEATURE = 1
        const val APK_SPLIT_ABI = 2
        const val APK_SPLIT_DENSITY = 3
        const val APK_SPLIT_LOCALE = 4
        const val APK_SPLIT_UNKNOWN = 5
        const val APK_SPLIT = 6

        private const val ATTR_IS_FEATURE_SPLIT = "isFeatureSplit"
        private const val ATTR_IS_SPLIT_REQUIRED = "isSplitRequired"
        private const val ATTR_ISOLATED_SPLIT = "isolatedSplits"
        private const val ATTR_CONFIG_FOR_SPLIT = "configForSplit"
        private const val ATTR_SPLIT = "split"
        private const val ATTR_PACKAGE = "package"
        private const val CONFIG_PREFIX = "config."

        val SUPPORTED_EXTENSIONS = listOf("apk", "apkm", "apks", "xapk")

        @Throws(ApkFileException::class)
        fun createInstance(context: Context, uri: Uri, mimeType: String?): ApkFile {
            val cachedFile = try {
                ApkSource.cacheUri(context, uri)
            } catch (e: IOException) {
                throw ApkFileException(e)
            }
            val apkFile = ApkFile(cachedFile)
            // If we cached a content:// URI, mark it for cleanup on close
            if (uri.scheme != "file" || !File(uri.path ?: "").canRead()) {
                apkFile.markAsExternalCache()
            }
            return apkFile
        }
    }

    private val mEntries: MutableList<Entry> = mutableListOf()
    private var mBaseEntry: Entry? = null
    private var mPackageName: String? = null
    private val mObbZipEntries: MutableList<ZipEntry> = mutableListOf()
    private var mZipFile: ZipFile? = null
    private var mClosed = false
    private val mCachedFiles: MutableList<File> = mutableListOf()
    private var mIsExternalCacheFile = false

    init {
        // An APK file is itself a ZIP, so we can't just check if it's a valid ZIP.
        // Instead, check if the ZIP contains .apk entries (APKS/APKM/XAPK container)
        // or AndroidManifest.xml at root (standalone APK).
        val zipFile = try {
            ZipFile(mCacheFilePath)
        } catch (e: Exception) {
            null
        }
        if (zipFile != null) {
            // Check if this is a split container (has .apk files inside)
            val hasApkEntries = zipFile.entries().asSequence()
                .any { !it.isDirectory && it.name.endsWith(".apk") }
            if (hasApkEntries) {
                mZipFile = zipFile
                parseZipContainer()
            } else {
                // It's a standalone APK (which is also a ZIP, but has no .apk entries inside)
                zipFile.close()
                parseStandaloneApk()
            }
        } else {
            parseStandaloneApk()
        }
        mEntries.sortWith(compareBy({ it.type }, { it.rank }))
        mPackageName ?: throw ApkFileException("Package name not found.")
    }

    private fun parseStandaloneApk() {
        val manifest = getManifestFromApk(mCacheFilePath)
        val manifestAttrs = getManifestAttributes(manifest)
        if (!manifestAttrs.containsKey(ATTR_PACKAGE)) {
            throw ApkFileException("Manifest doesn't contain any package name.")
        }
        mPackageName = manifestAttrs[ATTR_PACKAGE]
        mBaseEntry = Entry(
            fileName = mCacheFilePath.name,
            type = APK_BASE,
            manifest = manifest,
            manifestAttrs = manifestAttrs,
            source = mCacheFilePath
        )
        mEntries.add(mBaseEntry!!)
    }

    private fun parseZipContainer() {
        // mZipFile is already opened and assigned by the init block
        val zipFile = mZipFile ?: throw ApkFileException("ZipFile not initialized")
        val entries = zipFile.entries()
        while (entries.hasMoreElements()) {
            val zipEntry = entries.nextElement()
            if (zipEntry.isDirectory) continue
            val fileName = File(zipEntry.name).name
            when {
                fileName.endsWith(".apk") -> {
                    try {
                        zipFile.getInputStream(zipEntry).use { input ->
                            val manifest = getManifestFromApk(input)
                            val manifestAttrs = getManifestAttributes(manifest)

                            // Heuristic fallback: if "split" attribute was not resolved
                            // by ARSCLib (framework not loaded), check file name for
                            // common split APK naming patterns
                            if (!manifestAttrs.containsKey(ATTR_SPLIT)) {
                                val lowerName = fileName.lowercase()
                                if (lowerName.startsWith("config.") ||
                                    lowerName.startsWith("split_")
                                ) {
                                    val splitName = fileName.removeSuffix(".apk")
                                    manifestAttrs[ATTR_SPLIT] = splitName
                                    Log.w(TAG, "Heuristic: treated $fileName as split '$splitName' based on file name")
                                }
                            }

                            if (manifestAttrs.containsKey(ATTR_SPLIT)) {
                                mEntries.add(Entry(
                                    fileName = fileName,
                                    type = APK_SPLIT,
                                    manifest = manifest,
                                    manifestAttrs = manifestAttrs,
                                    zipEntry = zipEntry
                                ))
                            } else {
                                if (mBaseEntry != null) {
                                    throw RuntimeException("Duplicate base apk found.")
                                }
                                if (manifestAttrs.containsKey(ATTR_PACKAGE)) {
                                    mPackageName = manifestAttrs[ATTR_PACKAGE]
                                } else throw RuntimeException("Package name not found.")
                                mBaseEntry = Entry(
                                    fileName = fileName,
                                    type = APK_BASE,
                                    manifest = manifest,
                                    manifestAttrs = manifestAttrs,
                                    zipEntry = zipEntry
                                )
                                mEntries.add(mBaseEntry!!)
                            }
                        }
                    } catch (e: IOException) {
                        throw ApkFileException(e)
                    }
                }
                fileName.endsWith(".obb") -> mObbZipEntries.add(zipEntry)
                // info.json, .idsig etc. are ignored
            }
        }
        if (mBaseEntry == null) throw ApkFileException("No base apk found.")
    }

    fun getBaseEntry(): Entry = mBaseEntry ?: throw IllegalStateException("No base entry")
    fun getEntries(): List<Entry> = mEntries.toList()
    fun getPackageName(): String = mPackageName ?: throw IllegalStateException("No package name")
    fun isSplit(): Boolean = mEntries.size > 1
    fun hasObb(): Boolean = mObbZipEntries.isNotEmpty()

    @Throws(IOException::class)
    fun extractObb(obbDir: File) {
        if (!hasObb() || mZipFile == null) return
        obbDir.mkdirs()
        for (obbEntry in mObbZipEntries) {
            val outFile = File(obbDir, File(obbEntry.name).name)
            mZipFile!!.getInputStream(obbEntry).use { input ->
                FileOutputStream(outFile).use { output -> input.copyTo(output) }
            }
        }
    }

    @Throws(IOException::class)
    fun getCachedEntryFile(entry: Entry): File {
        entry.source?.let { if (it.canRead()) return it }
        entry.cachedFile?.let { if (it.canRead()) return it }
        val zipEntry = entry.zipEntry ?: throw IOException("No source for entry ${entry.name}")
        val cacheFile = File(mCacheFilePath.parentFile, "split_${System.currentTimeMillis()}_${entry.fileName}")
        mZipFile!!.getInputStream(zipEntry).use { input ->
            FileOutputStream(cacheFile).use { output -> input.copyTo(output) }
        }
        entry.cachedFile = cacheFile
        mCachedFiles.add(cacheFile)
        return cacheFile
    }

    fun markAsExternalCache() { mIsExternalCacheFile = true }

    override fun close() {
        if (mClosed) return
        mClosed = true
        mZipFile?.let { runCatching { it.close() } }
        for (file in mCachedFiles) runCatching { file.delete() }
        mCachedFiles.clear()
        if (mIsExternalCacheFile) runCatching { mCacheFilePath.delete() }
        mEntries.clear()
        mBaseEntry = null
        mObbZipEntries.clear()
    }

    /**
     * Represents a single APK entry (base or split) within the package.
     * For splits, [name] is the split name from the manifest (e.g. "config.arm64_v8a").
     * For base, [name] is "base".
     */
    inner class Entry(
        val fileName: String,
        entryType: Int,
        val manifest: ByteBuffer,
        private val manifestAttrs: HashMap<String, String>
    ) {
        val name: String
        var type: Int = entryType
            private set
        var rank: Int = Int.MAX_VALUE
            private set

        private var mSplitSuffix: String? = null
        private var mForFeature: String? = null
        var source: File? = null
            internal set
        var zipEntry: ZipEntry? = null
            internal set
        var cachedFile: File? = null
            internal set

        private val mRequired: Boolean
        private val mIsolated: Boolean

        constructor(
            fileName: String,
            type: Int,
            manifest: ByteBuffer,
            manifestAttrs: HashMap<String, String>,
            source: File
        ) : this(fileName, type, manifest, manifestAttrs) {
            this.source = source
        }

        constructor(
            fileName: String,
            type: Int,
            manifest: ByteBuffer,
            manifestAttrs: HashMap<String, String>,
            zipEntry: ZipEntry
        ) : this(fileName, type, manifest, manifestAttrs) {
            this.zipEntry = zipEntry
        }

        init {
            if (entryType == APK_BASE) {
                name = "base"
                mRequired = true
                mIsolated = false
                type = APK_BASE
            } else if (entryType == APK_SPLIT) {
                val splitName = manifestAttrs[ATTR_SPLIT] ?: throw RuntimeException("Split name is empty.")
                name = splitName
                mRequired = manifestAttrs[ATTR_IS_SPLIT_REQUIRED]?.toBoolean() ?: false
                mIsolated = manifestAttrs[ATTR_ISOLATED_SPLIT]?.toBoolean() ?: false
                if (manifestAttrs.containsKey(ATTR_IS_FEATURE_SPLIT)) {
                    type = APK_SPLIT_FEATURE
                } else {
                    if (manifestAttrs.containsKey(ATTR_CONFIG_FOR_SPLIT)) {
                        mForFeature = manifestAttrs[ATTR_CONFIG_FOR_SPLIT]
                        if (mForFeature.isNullOrEmpty()) mForFeature = null
                    }
                    val configPartIndex = splitName.lastIndexOf(CONFIG_PREFIX)
                    if (configPartIndex == -1 ||
                        (configPartIndex != 0 && splitName[configPartIndex - 1] != '.')
                    ) {
                        type = APK_SPLIT_UNKNOWN
                    } else {
                        mSplitSuffix = splitName.substring(configPartIndex + CONFIG_PREFIX.length)
                        when {
                            StaticDataset.ALL_ABIS.containsKey(mSplitSuffix) -> {
                                type = APK_SPLIT_ABI
                                val abi = StaticDataset.ALL_ABIS[mSplitSuffix]
                                val idx = Build.SUPPORTED_ABIS.indexOf(abi)
                                if (idx != -1) {
                                    rank = idx
                                    if (mForFeature == null) rank -= 1000
                                }
                            }
                            StaticDataset.DENSITY_NAME_TO_DENSITY.containsKey(mSplitSuffix) -> {
                                type = APK_SPLIT_DENSITY
                                rank = Math.abs(StaticDataset.DEVICE_DENSITY - StaticDataset.getDensityFromName(mSplitSuffix!!))
                                if (mForFeature == null) rank -= 1000
                            }
                            StaticDataset.isValidLocale(mSplitSuffix!!) -> {
                                type = APK_SPLIT_LOCALE
                                StaticDataset.getLocaleRank(mSplitSuffix!!)?.let {
                                    rank = it
                                    if (mForFeature == null) rank -= 1000
                                }
                            }
                            else -> type = APK_SPLIT_UNKNOWN
                        }
                    }
                }
            } else {
                name = fileName
                mRequired = false
                mIsolated = false
                type = APK_SPLIT_UNKNOWN
            }
        }

        fun getFileSize(): Long {
            cachedFile?.let { if (it.exists()) return it.length() }
            zipEntry?.let { return it.size }
            source?.let { if (it.exists()) return it.length() }
            return 0L
        }

        fun isRequired(): Boolean = mRequired
        fun isIsolated(): Boolean = mIsolated

        fun supported(): Boolean {
            if (type == APK_SPLIT_ABI) return rank != Int.MAX_VALUE
            return true
        }

        fun getAbi(): String {
            if (type == APK_SPLIT_ABI) return StaticDataset.ALL_ABIS[mSplitSuffix]!!
            throw RuntimeException("Attempt to fetch ABI for invalid apk")
        }

        fun getDensity(): Int {
            if (type == APK_SPLIT_DENSITY) return StaticDataset.getDensityFromName(mSplitSuffix!!)
            throw RuntimeException("Attempt to fetch Density for invalid apk")
        }

        fun getLocale(): Locale {
            if (type == APK_SPLIT_LOCALE)
                return StaticDataset.toLocale(mSplitSuffix!!)
            throw RuntimeException("Attempt to fetch Locale for invalid apk")
        }

        fun getFeature(): String? = if (type == APK_SPLIT_FEATURE) name else mForFeature
        fun isForFeature(): Boolean = mForFeature != null

        fun toShortLocalizedString(context: Context): String = when (type) {
            APK_BASE -> context.getString(com.appkitz.R.string.base_apk)
            APK_SPLIT_DENSITY -> if (mForFeature != null) {
                context.getString(com.appkitz.R.string.density_split_for_feature, mSplitSuffix, getDensity(), mForFeature)
            } else {
                context.getString(com.appkitz.R.string.density_split_for_base_apk, mSplitSuffix, getDensity())
            }
            APK_SPLIT_ABI -> if (mForFeature != null) {
                context.getString(com.appkitz.R.string.abi_split_for_feature, getAbi(), mForFeature)
            } else {
                context.getString(com.appkitz.R.string.abi_split_for_base_apk, getAbi())
            }
            APK_SPLIT_LOCALE -> if (mForFeature != null) {
                context.getString(com.appkitz.R.string.locale_split_for_feature, getLocale().getDisplayLanguage(Locale.getDefault()), mForFeature)
            } else {
                context.getString(com.appkitz.R.string.locale_split_for_base_apk, getLocale().getDisplayLanguage(Locale.getDefault()))
            }
            APK_SPLIT_FEATURE -> context.getString(com.appkitz.R.string.split_feature_name, name)
            APK_SPLIT_UNKNOWN, APK_SPLIT -> if (mForFeature != null) {
                context.getString(com.appkitz.R.string.unknown_split_for_feature, name, mForFeature)
            } else {
                context.getString(com.appkitz.R.string.unknown_split_for_base_apk, name)
            }
            else -> throw RuntimeException("Invalid split type.")
        }

        fun toLocalizedString(context: Context): String {
            val shortText = toShortLocalizedString(context)
            val sizeStr = Formatter.formatFileSize(context, getFileSize())
            val builder = StringBuilder("$shortText\n${context.getString(com.appkitz.R.string.size)}: $sizeStr")
            if (isRequired()) builder.append(", ${context.getString(com.appkitz.R.string.required)}")
            if (!supported()) builder.append(", ${context.getString(com.appkitz.R.string.unsupported_split)}")
            return builder.toString()
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other is Entry) return name == other.name
            if (other is String) return name == other
            return false
        }

        override fun hashCode(): Int = Objects.hash(name)
    }

    class ApkFileException : Throwable {
        constructor(message: String) : super(message)
        constructor(message: String, cause: Throwable) : super(message, cause)
        constructor(cause: Throwable) : super(cause)
    }
}
