// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2020-2024 Muntashir Al-Islam (AppManager)
// Copyright (C) 2025 Appkitz Contributors

package com.appkitz.installer.apk

import android.text.TextUtils
import android.util.Log
import com.reandroid.apk.AndroidFrameworks
import com.reandroid.apk.FrameworkApk
import com.reandroid.arsc.chunk.PackageBlock
import com.reandroid.arsc.chunk.xml.AndroidManifestBlock
import com.reandroid.arsc.chunk.xml.ResXmlElement
import com.reandroid.arsc.io.BlockReader
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream

/**
 * Utilities for extracting and parsing AndroidManifest.xml from APK files.
 * Ported from AppManager's ApkUtils.java, using standard ZipFile/ZipInputStream
 * instead of apksig for manifest extraction.
 */
object ApkUtils {

    private const val TAG = "ApkUtils"
    private const val MANIFEST_FILE = "AndroidManifest.xml"
    private const val DEFAULT_BUFFER_SIZE = 8192

    private var sFrameworkPackageBlock: PackageBlock? = null

    /**
     * Extract AndroidManifest.xml from an APK file as raw ByteBuffer (binary XML).
     * Uses standard java.util.zip.ZipFile instead of apksig.
     */
    @Throws(ApkFile.ApkFileException::class)
    fun getManifestFromApk(apkFile: File): ByteBuffer {
        try {
            ZipFile(apkFile).use { zipFile ->
                val entry = zipFile.getEntry(MANIFEST_FILE)
                    ?: throw ApkFile.ApkFileException("Missing $MANIFEST_FILE")
                zipFile.getInputStream(entry).use { input ->
                    return readStreamToByteBuffer(input)
                }
            }
        } catch (e: IOException) {
            throw ApkFile.ApkFileException(e.message ?: "IOException", e)
        }
    }

    /**
     * Extract AndroidManifest.xml from an APK InputStream as raw ByteBuffer.
     * Uses ZipInputStream to find the manifest entry (same as AppManager).
     */
    @Throws(ApkFile.ApkFileException::class)
    fun getManifestFromApk(apkInputStream: InputStream): ByteBuffer {
        try {
            ZipInputStream(BufferedInputStream(apkInputStream)).use { zipInputStream ->
                var zipEntry: ZipEntry?
                while (zipInputStream.nextEntry.also { zipEntry = it } != null) {
                    if (zipEntry!!.name != MANIFEST_FILE) {
                        continue
                    }
                    return readStreamToByteBuffer(zipInputStream)
                }
            }
        } catch (e: IOException) {
            // Fall through to error
        }
        throw ApkFile.ApkFileException("Failed to read $MANIFEST_FILE")
    }

    private fun readStreamToByteBuffer(input: InputStream): ByteBuffer {
        val buffer = ByteArrayOutputStream()
        val buf = ByteArray(DEFAULT_BUFFER_SIZE)
        var n: Int
        while (input.read(buf).also { n = it } != -1) {
            buffer.write(buf, 0, n)
        }
        return ByteBuffer.wrap(buffer.toByteArray())
    }

    /**
     * Parse binary AndroidManifest.xml and extract attributes from <manifest>
     * and <application> elements using ARSCLib's AndroidManifestBlock.
     *
     * Uses autoSetAttributeNames() to resolve framework attribute names,
     * and falls back to high-level API (isSplit/getSplit/getPackageName)
     * if name resolution fails.
     */
    @Throws(ApkFile.ApkFileException::class)
    fun getManifestAttributes(manifestBytes: ByteBuffer): HashMap<String, String> {
        val manifestAttrs = HashMap<String, String>()
        try {
            val manifestBlock = AndroidManifestBlock()
            BlockReader(manifestBytes.array()).use { reader ->
                manifestBlock.readBytes(reader)
            }

            // Set framework package block for resource name resolution
            try {
                manifestBlock.setPackageBlock(getFrameworkPackageBlock())
            } catch (e: Exception) {
                Log.w(TAG, "Failed to set framework package block, will use fallback", e)
            }

            // Auto-resolve attribute names using framework resources
            try {
                manifestBlock.autoSetAttributeNames()
            } catch (e: Exception) {
                Log.w(TAG, "autoSetAttributeNames failed", e)
            }

            val resManifestElement = manifestBlock.documentElement
                ?: throw ApkFile.ApkFileException("No manifest element found.")
            if ("manifest" != resManifestElement.name) {
                throw ApkFile.ApkFileException("No manifest found, got: ${resManifestElement.name}")
            }

            // Read <manifest> attributes
            var attrIt = resManifestElement.attributes
            while (attrIt.hasNext()) {
                val attr = attrIt.next()
                val rawName = attr.name
                if (!TextUtils.isEmpty(rawName)) {
                    // Normalize: strip namespace prefix (e.g., "android:split" → "split")
                    val normalizedName = rawName!!.substringAfter(':')
                    val value = attr.valueAsString
                    Log.d(TAG, "manifest attr: $rawName → $normalizedName = $value")
                    manifestAttrs[normalizedName] = value
                }
            }

            // Read <application> attributes
            val appElementIt = resManifestElement.getElements("application")
            var appElement: ResXmlElement? = null
            if (appElementIt.hasNext()) {
                appElement = appElementIt.next()
            }
            if (appElementIt.hasNext()) {
                throw ApkFile.ApkFileException("\"manifest\" has duplicate \"application\" tags.")
            }
            if (appElement != null) {
                attrIt = appElement.attributes
                while (attrIt.hasNext()) {
                    val attr = attrIt.next()
                    val rawName = attr.name
                    if (!TextUtils.isEmpty(rawName)) {
                        val normalizedName = rawName!!.substringAfter(':')
                        // Don't overwrite manifest-level attributes
                        if (!manifestAttrs.containsKey(normalizedName)) {
                            manifestAttrs[normalizedName] = attr.valueAsString
                        }
                    }
                }
            }

            // Fallback: use AndroidManifestBlock high-level API for critical attributes
            // This works even when attribute name resolution fails, because it uses resource IDs
            if (!manifestAttrs.containsKey("split") && manifestBlock.isSplit()) {
                val splitName = manifestBlock.getSplit()
                if (splitName != null) {
                    manifestAttrs["split"] = splitName
                    Log.d(TAG, "Fallback: added split='$splitName' via AndroidManifestBlock.isSplit()")
                }
            }
            if (!manifestAttrs.containsKey("package")) {
                val pkgName = manifestBlock.getPackageName()
                if (pkgName != null) {
                    manifestAttrs["package"] = pkgName
                    Log.d(TAG, "Fallback: added package='$pkgName' via AndroidManifestBlock.getPackageName()")
                }
            }

            Log.d(TAG, "Final attributes: $manifestAttrs")
        } catch (e: IOException) {
            throw ApkFile.ApkFileException(e)
        }
        return manifestAttrs
    }

    /**
     * Get the framework PackageBlock for resolving attribute resource IDs to names.
     * Tries built-in ARSCLib frameworks first, then device's framework-res.apk.
     */
    private fun getFrameworkPackageBlock(): PackageBlock {
        sFrameworkPackageBlock?.let { return it }

        // Try ARSCLib built-in frameworks
        try {
            val framework = AndroidFrameworks.getLatest()
            if (framework != null) {
                val tableBlock = framework.tableBlock
                if (tableBlock != null) {
                    val packages = tableBlock.allPackages
                    if (packages.hasNext()) {
                        val block = packages.next()
                        sFrameworkPackageBlock = block
                        Log.d(TAG, "Loaded framework from AndroidFrameworks.getLatest()")
                        return block
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "AndroidFrameworks.getLatest() failed", e)
        }

        // Try loading from device's framework-res.apk
        try {
            val frameworkFile = File("/system/framework/framework-res.apk")
            if (frameworkFile.exists() && frameworkFile.canRead()) {
                val frameworkApk = FrameworkApk.loadApkFile(frameworkFile)
                val tableBlock = frameworkApk.tableBlock
                if (tableBlock != null) {
                    val packages = tableBlock.allPackages
                    if (packages.hasNext()) {
                        val block = packages.next()
                        sFrameworkPackageBlock = block
                        Log.d(TAG, "Loaded framework from /system/framework/framework-res.apk")
                        return block
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load framework from device", e)
        }

        throw RuntimeException("Failed to load framework package block for attribute name resolution")
    }
}
