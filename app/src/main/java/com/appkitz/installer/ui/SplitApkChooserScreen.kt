// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2020-2024 Muntashir Al-Islam (AppManager)
// Copyright (C) 2025 Appkitz Contributors

package com.appkitz.installer.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.appkitz.R
import com.appkitz.installer.apk.ApkFile
import com.appkitz.installer.viewmodel.InstallerState
import android.text.format.Formatter

/**
 * The main split APK chooser dialog.
 * Ported from AppManager's SplitApkChooser activity layout.
 *
 * Shows a searchable list of APK entries (base + splits) with checkboxes,
 * grouped by type. Base APK is always checked and disabled.
 */
@Composable
fun SplitApkChooserScreen(
    state: InstallerState,
    onToggleSplit: (String) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onInstall: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.large,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Title
                Text(
                    text = stringResource(R.string.install),
                    style = MaterialTheme.typography.titleLarge
                )
                if (state.packageName != null) {
                    Text(
                        text = state.packageName!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(12.dp))

                // Search field
                OutlinedTextField(
                    value = state.searchQuery,
                    onValueChange = onSearchQueryChange,
                    label = { Text(stringResource(R.string.search_splits)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))

                // Entry list
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.Start
                ) {
                    // Compute filtered entries
                    val filtered = state.entries.filter { entry ->
                        val query = state.searchQuery.trim()
                        query.isEmpty() ||
                            entry.name.contains(query, ignoreCase = true) ||
                            entry.fileName.contains(query, ignoreCase = true) ||
                            entry.toShortLocalizedString(context)
                                .contains(query, ignoreCase = true)
                    }

                    var lastType = -1
                    for (entry in filtered) {
                        // Group header when type changes
                        if (entry.type != lastType) {
                            lastType = entry.type
                            item {
                                GroupHeader(entry.type)
                            }
                        }
                        item {
                            EntryItem(
                                entry = entry,
                                isSelected = entry.name in state.selectedNames,
                                isDisabled = entry.name in state.disabledNames,
                                onToggle = { onToggleSplit(entry.name) }
                            )
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))

                // Install button
                Button(
                    onClick = onInstall,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.install))
                }
            }
        }
    }
}

@Composable
private fun GroupHeader(type: Int) {
    val categoryText = when (type) {
        ApkFile.APK_BASE -> stringResource(R.string.group_base)
        ApkFile.APK_SPLIT_ABI -> stringResource(R.string.group_abi)
        ApkFile.APK_SPLIT_DENSITY -> stringResource(R.string.group_density)
        ApkFile.APK_SPLIT_LOCALE -> stringResource(R.string.group_locale)
        ApkFile.APK_SPLIT_FEATURE -> stringResource(R.string.group_feature)
        else -> stringResource(R.string.group_unknown)
    }
    Text(
        text = categoryText,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(vertical = 4.dp)
    )
}

@Composable
private fun EntryItem(
    entry: ApkFile.Entry,
    isSelected: Boolean,
    isDisabled: Boolean,
    onToggle: () -> Unit
) {
    val context = LocalContext.current
    val displayText = remember(entry) { entry.toShortLocalizedString(context) }
    val sizeText = remember(entry) {
        Formatter.formatFileSize(context, entry.getFileSize())
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = isSelected,
            onCheckedChange = { onToggle() },
            enabled = !isDisabled
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = displayText,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = sizeText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (entry.isRequired()) {
            Text(
                text = stringResource(R.string.required),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error
            )
        } else if (!entry.supported()) {
            Text(
                text = stringResource(R.string.unsupported_split),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}
