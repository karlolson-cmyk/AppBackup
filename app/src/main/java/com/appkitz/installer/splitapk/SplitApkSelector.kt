// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2020-2024 Muntashir Al-Islam (AppManager)
// Copyright (C) 2025 Appkitz Contributors

package com.appkitz.installer.splitapk

import com.appkitz.installer.apk.ApkFile

/**
 * Pure logic for selecting/deselecting split APK entries.
 * Ported from AppManager's SplitApkChooser selection algorithm.
 */
class SplitApkSelector(private val entries: List<ApkFile.Entry>) {

    private val entryMap: Map<String, ApkFile.Entry> = entries.associateBy { it.name }
    private var selectedNames: MutableSet<String> = mutableSetOf()
    private val seenTypesByFeature: MutableMap<String?, MutableSet<Int>> = mutableMapOf()

    /** Entry names that cannot be toggled by the user. */
    val disabledEntryNames: Set<String> = entries.filter { entry ->
        !entry.supported() || entry.isRequired()
    }.map { it.name }.toSet()

    /**
     * Compute the initial set of selected entry names.
     *
     * If the app is already installed, AppManager first selects splits from the
     * installed package and then falls through to add any newly required split
     * dependencies from the package being installed.
     */
    fun getInitialSelections(installedSplitNames: Set<String>? = null): Set<String> {
        selectedNames.clear()
        seenTypesByFeature.clear()

        if (!installedSplitNames.isNullOrEmpty()) {
            for (entry in entries) {
                if (entry.name in installedSplitNames) {
                    selectedNames.add(entry.name)
                    markSeen(entry)
                }
            }
        }

        for (entry in entries) {
            if (entry.name in selectedNames) continue
            if (entry.isRequired()) {
                selectedNames.add(entry.name)
                markSeen(entry)
            }
        }

        selectMissingConfigSplitsForSeenFeatures()
        return selectedNames.toSet()
    }

    /**
     * Select an entry by name. AppManager allows selecting additional splits of
     * the same type; it only auto-selects missing dependency types for the same
     * feature group.
     */
    fun select(name: String): Set<String> {
        val entry = entryMap[name] ?: return selectedNames.toSet()
        if (name in disabledEntryNames) return selectedNames.toSet()

        selectedNames.add(name)
        selectMissingConfigSplitsForFeature(entry.getFeature(), entry.type)
        return selectedNames.toSet()
    }

    /**
     * Deselect an entry by name.
     *
     * @return the updated selection set, or null if deselection is not allowed.
     */
    fun deselect(name: String): Set<String>? {
        val entry = entryMap[name] ?: return null
        if (entry.isRequired()) return null
        if (name in disabledEntryNames) return null

        if (entry.type == ApkFile.APK_SPLIT_FEATURE) {
            val feature = entry.getFeature()
            seenTypesByFeature.remove(feature)
            val toDeselect = selectedNames.filter { otherName ->
                entryMap[otherName]?.getFeature() == feature
            }
            selectedNames.removeAll(toDeselect.toSet())
            return selectedNames.toSet()
        }

        val selectedAnySameType = selectedNames.any { otherName ->
            otherName != name &&
                entryMap[otherName]?.type == entry.type &&
                entryMap[otherName]?.getFeature() == entry.getFeature()
        }
        if (!selectedAnySameType) {
            return null
        }

        selectedNames.remove(name)
        return selectedNames.toSet()
    }

    /** Toggle an entry. Returns the new selection set, or null if toggle is not allowed. */
    fun toggle(name: String): Set<String>? {
        return if (name in selectedNames) deselect(name) else select(name)
    }

    fun isSelected(name: String): Boolean = name in selectedNames

    fun getSelectedEntries(): List<ApkFile.Entry> =
        entries.filter { it.name in selectedNames }

    fun getSelectedNames(): Set<String> = selectedNames.toSet()

    fun isDisabled(name: String): Boolean = name in disabledEntryNames

    private fun markSeen(entry: ApkFile.Entry) {
        seenTypesByFeature.getOrPut(entry.getFeature()) { mutableSetOf() }.add(entry.type)
    }

    private fun selectMissingConfigSplitsForSeenFeatures() {
        val features = seenTypesByFeature.keys.toList()
        for (feature in features) {
            selectMissingConfigSplitsForFeature(feature, selectedTypeToSkip = null)
        }
    }

    private fun selectMissingConfigSplitsForFeature(
        feature: String?,
        selectedTypeToSkip: Int?
    ) {
        val seenTypes = seenTypesByFeature.getOrPut(feature) { mutableSetOf() }
        for (entry in entries) {
            if (entry.getFeature() != feature || entry.type == selectedTypeToSkip) {
                continue
            }
            when (entry.type) {
                ApkFile.APK_BASE,
                ApkFile.APK_SPLIT_FEATURE -> {
                    selectedNames.add(entry.name)
                }
                ApkFile.APK_SPLIT_UNKNOWN,
                ApkFile.APK_SPLIT -> {
                    // AppManager does not auto-select unknown split types.
                }
                ApkFile.APK_SPLIT_DENSITY,
                ApkFile.APK_SPLIT_ABI,
                ApkFile.APK_SPLIT_LOCALE -> {
                    if (entry.type !in seenTypes) {
                        seenTypes.add(entry.type)
                        selectedNames.add(entry.name)
                    }
                }
                else -> throw RuntimeException("Invalid split type.")
            }
        }
    }
}
