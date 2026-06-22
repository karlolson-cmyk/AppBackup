package com.appbackup.ui.component

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun BackupProgressDialog(
    message: String,
    progress: Float,
    onCancel: () -> Unit
) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text("正在备份...") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Text(text = message)
            }
        },
        confirmButton = {
            TextButton(onClick = onCancel) {
                Text("取消")
            }
        }
    )
}
