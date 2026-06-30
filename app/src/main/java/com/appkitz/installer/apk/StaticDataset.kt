// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2020-2024 Muntashir Al-Islam (AppManager)
// Copyright (C) 2025 Appkitz Contributors

package com.appkitz.installer.apk

import android.content.res.Resources
import android.util.DisplayMetrics
import androidx.core.os.ConfigurationCompat
import androidx.core.os.LocaleListCompat
import java.util.IllformedLocaleException
import java.util.Locale

/**
 * Static dataset for ABI, density and locale mapping.
 * Ported from AppManager's StaticDataset.java.
 */
object StaticDataset {

    // ABI split suffix -> ABI name
    val ALL_ABIS: Map<String, String> = mapOf(
        "armeabi_v7a" to "armeabi-v7a",
        "arm64_v8a" to "arm64-v8a",
        "x86" to "x86",
        "x86_64" to "x86_64"
    )

    // Density split suffix -> density dpi value
    val DENSITY_NAME_TO_DENSITY: Map<String, Int> = mapOf(
        "ldpi" to DisplayMetrics.DENSITY_LOW,      // 120
        "mdpi" to DisplayMetrics.DENSITY_MEDIUM,    // 160
        "tvdpi" to DisplayMetrics.DENSITY_TV,       // 213
        "hdpi" to DisplayMetrics.DENSITY_HIGH,      // 240
        "xhdpi" to DisplayMetrics.DENSITY_XHIGH,    // 320
        "xxhdpi" to DisplayMetrics.DENSITY_XXHIGH,  // 480
        "xxxhdpi" to DisplayMetrics.DENSITY_XXXHIGH // 640
    )

    val DEVICE_DENSITY: Int = Resources.getSystem().displayMetrics.densityDpi

    // Precomputed set of valid language codes (for fast locale validation)
    private val VALID_LANGUAGES: Set<String> by lazy {
        Locale.getAvailableLocales()
            .map { it.language }
            .filter { it.isNotEmpty() }
            .toSet()
    }

    /**
     * Locale ranking map: BCP 47 tag (with hyphens) -> priority index.
     * Also includes language-only keys (e.g., "zh" alongside "zh-CN").
     * Lower index = higher priority.
     */
    val LOCALE_RANKING: Map<String, Int> by lazy {
        val ranking = mutableMapOf<String, Int>()
        val localeList: LocaleListCompat =
            ConfigurationCompat.getLocales(Resources.getSystem().configuration)
        for (i in 0 until localeList.size()) {
            localeList[i]?.let { locale ->
                ranking[locale.toLanguageTag()] = i
                // Also map language-only for partial matching
                if (locale.language.isNotEmpty()) {
                    ranking.putIfAbsent(locale.language, i)
                }
            }
        }
        ranking
    }

    /**
     * Look up the locale rank for a split suffix.
     * Android split names use underscores (e.g. "zh_CN"), BCP 47 uses hyphens ("zh-CN").
     * Tries full tag first, then language-only.
     * @return rank index, or null if not found
     */
    fun getLocaleRank(splitSuffix: String): Int? {
        val normalized = splitSuffix.replace('_', '-')
        // Try full tag (e.g. "zh-CN")
        LOCALE_RANKING[normalized]?.let { return it }
        // Try language-only (e.g. "zh" from "zh-CN")
        val langOnly = normalized.substringBefore('-')
        if (langOnly != normalized) {
            LOCALE_RANKING[langOnly]?.let { return it }
        }
        return null
    }

    /**
     * Check if the given split suffix represents a valid locale.
     * Handles underscore-to-hyphen conversion (Android uses "zh_CN", BCP 47 uses "zh-CN").
     */
    fun isValidLocale(languageTag: String): Boolean {
        return try {
            val normalized = languageTag.replace('_', '-')
            val locale = Locale.forLanguageTag(normalized)
            val lang = locale.language
            lang.isNotEmpty() && lang in VALID_LANGUAGES
        } catch (e: IllformedLocaleException) {
            false
        }
    }

    /**
     * Normalize a locale split suffix (underscores → hyphens) and build a Locale.
     */
    fun toLocale(splitSuffix: String): Locale {
        return Locale.forLanguageTag(splitSuffix.replace('_', '-'))
    }

    fun getDensityFromName(densityName: String): Int {
        return DENSITY_NAME_TO_DENSITY[densityName]
            ?: throw IllegalArgumentException("Unknown density $densityName")
    }
}
