// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2020-2024 Muntashir Al-Islam (AppManager)
// Copyright (C) 2025 Appkitz Contributors

package com.appkitz.installer

import android.content.Context
import java.io.File

/**
 * Manages temporary files used during APK installation.
 * Files are created in the app cache directory and cleaned up when no longer needed.
 */
class FileCache(context: Context) : AutoCloseable {

    private val mCacheDir: File = context.cacheDir
    private val mCachedFiles: MutableList<File> = mutableListOf()

    /**
     * Create a temporary file for extracting a split APK entry.
     * The caller is responsible for writing data to the returned file.
     */
    fun createCacheFile(prefix: String, suffix: String = ".apk"): File {
        val file = File(mCacheDir, "${prefix}_${System.currentTimeMillis()}$suffix")
        mCachedFiles.add(file)
        return file
    }

    /**
     * Register an externally-created file for cleanup on [close].
     */
    fun register(file: File) {
        mCachedFiles.add(file)
    }

    override fun close() {
        for (file in mCachedFiles) {
            if (file.exists()) file.delete()
        }
        mCachedFiles.clear()
    }
}
