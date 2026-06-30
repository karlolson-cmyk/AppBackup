// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2020-2024 Muntashir Al-Islam (AppManager)
// Copyright (C) 2025 Appkitz Contributors

package com.appkitz.installer.apk

import android.content.Context
import android.net.Uri
import android.os.StatFs
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * Caches a content:// or file:// Uri to a local file.
 * Ported from AppManager's ApkSource concept.
 */
object ApkSource {

    private const val CACHE_PREFIX = "apk_"
    private const val CACHE_SUFFIX = ".cache"

    /**
     * Resolve the given [uri] to a local [File].
     * - file:// URIs are returned directly if readable.
     * - content:// URIs are copied to the app cache directory.
     * @throws IOException if the stream cannot be opened or the file is too large
     *                     for available storage.
     */
    @Throws(IOException::class)
    fun cacheUri(context: Context, uri: Uri): File {
        if (uri.scheme == "file") {
            val file = File(uri.path ?: throw IOException("Invalid file URI: $uri"))
            if (file.canRead()) return file
        }

        val cacheDir = context.cacheDir
        // Check available space before copying
        val stat = StatFs(cacheDir.absolutePath)
        val availBytes = stat.availableBytes

        val cacheFile = File(cacheDir, "${CACHE_PREFIX}${System.currentTimeMillis()}$CACHE_SUFFIX")
        context.contentResolver.openInputStream(uri).use { input ->
            if (input == null) throw IOException("Cannot open input stream for $uri")
            FileOutputStream(cacheFile).use { output ->
                val buf = ByteArray(DEFAULT_BUFFER_SIZE)
                var totalRead = 0L
                while (true) {
                    val n = input.read(buf)
                    if (n == -1) break
                    totalRead += n
                    if (totalRead > availBytes) {
                        cacheFile.delete()
                        throw IOException("Insufficient cache space")
                    }
                    output.write(buf, 0, n)
                }
            }
        }
        return cacheFile
    }

    private const val DEFAULT_BUFFER_SIZE = 8192
}
