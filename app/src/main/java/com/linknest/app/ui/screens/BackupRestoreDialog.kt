package com.linknest.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.linknest.app.presentation.BackupOperation
import com.linknest.app.presentation.BackupUiState
import com.linknest.app.ui.theme.Purple
import com.linknest.app.ui.theme.SurfaceElevated
import com.linknest.app.ui.theme.TextSecondary

@Composable
fun BackupRestoreDialog(
    state: BackupUiState,
    onDismiss: () -> Unit,
    onExport: () -> Unit,
    onRestore: () -> Unit
) {
    val busy = state.operation != BackupOperation.IDLE
    Dialog(
        onDismissRequest = { if (!busy) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth().padding(18.dp).heightIn(max = 680.dp),
            shape = MaterialTheme.shapes.large,
            color = SurfaceElevated
        ) {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()).padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text("Backup & restore", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "Export keeps your bookmarks, notes, tags, metadata, and explicit graph connections in one local JSON file.",
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodyMedium
                )

                Button(onClick = onExport, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                    if (state.operation == BackupOperation.EXPORTING) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Purple)
                        Spacer(Modifier.size(10.dp))
                    }
                    Text(if (state.operation == BackupOperation.EXPORTING) "Exporting…" else "Export backup")
                }
                OutlinedButton(onClick = onRestore, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                    if (state.operation == BackupOperation.RESTORING) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Purple)
                        Spacer(Modifier.size(10.dp))
                    }
                    Text(if (state.operation == BackupOperation.RESTORING) "Restoring…" else "Restore backup")
                }

                Text(
                    "Restore replaces the current local library. The selected backup is validated before any database changes are made.",
                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.9f),
                    style = MaterialTheme.typography.bodySmall
                )
                state.feedback?.let { feedback ->
                    Text(
                        feedback,
                        color = if (state.feedbackIsError) MaterialTheme.colorScheme.error else Color(0xFF9BE7B1),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss, enabled = !busy) { Text("Done") }
                }
            }
        }
    }
}

@Composable
fun RestoreConfirmationDialog(
    enabled: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (enabled) onDismiss() },
        title = { Text("Replace your library?") },
        text = {
            Text(
                "Restoring this backup will replace all current bookmarks, notes, tags, and explicit graph connections. This cannot be undone from inside LinkNest."
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = enabled) { Text("Restore") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = enabled) { Text("Cancel") }
        }
    )
}
