package com.dfc.mobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dfc.mobile.ui.pressFeedback
import com.dfc.mobile.ui.theme.Radii
import com.dfc.mobile.ui.theme.Spacing

/** Name prompt for a new folder; validates against what the server strips. */
@Composable
fun NewFolderDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    val invalid = name.contains('/') || name.contains('\\')
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New folder") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    label = { Text("Folder name") },
                    isError = invalid,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (invalid) {
                    Text(
                        text = "A folder name cannot contain slashes.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = Spacing.sm),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name.trim()) },
                enabled = name.isNotBlank() && !invalid,
            ) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Confirms a destructive delete with the real count of what will be removed. */
@Composable
fun DeleteDialog(
    count: Int,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (count == 1) "Delete this item?" else "Delete $count items?") },
        text = {
            Text(
                "They move to the drive's trash, where they stay until you empty it. " +
                    "This cannot be undone from the phone."
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Delete", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Result of a share creation: the link, shown once, with copy and share. */
@Composable
fun ShareResultDialog(
    url: String,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Public link created") },
        text = {
            Column {
                Text(
                    "Anyone with this link can download the file until it expires. " +
                        "It is shown once, so copy it now.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(Spacing.md))
                Text(
                    text = url,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(Radii.control))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .padding(Spacing.sm),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                    as android.content.ClipboardManager
                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("drive link", url))
                onDismiss()
            }) { Text("Copy link") }
        },
        dismissButton = {
            TextButton(onClick = {
                val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(android.content.Intent.EXTRA_TEXT, url)
                }
                context.startActivity(android.content.Intent.createChooser(send, "Share link"))
                onDismiss()
            }) { Text("Share") }
        },
    )
}


/** Rename prompt; the server rejects empty and path-like names, so mirror that. */
@Composable
fun RenameDialog(
    currentName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember { mutableStateOf(currentName) }
    val invalid = name.isBlank() || name.contains('/') || name.contains('\\')
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    label = { Text("Name") },
                    isError = invalid,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (invalid) {
                    Text(
                        text = "A name cannot be empty or contain slashes.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = Spacing.sm),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name.trim()) },
                enabled = !invalid && name.trim() != currentName,
            ) { Text("Rename") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** What this file is, for the "what am I looking at" moment. */
@Composable
fun DetailsDialog(
    file: com.dfc.mobile.RemoteFile,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(file.name) },
        text = {
            Column {
                DetailsRow("Size", com.dfc.mobile.ui.formatBytes(file.size))
                DetailsRow("Type", file.mimeType.ifBlank { "file" })
                DetailsRow(
                    "Modified",
                    java.text.DateFormat.getDateTimeInstance(
                        java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT
                    ).format(java.util.Date(file.modTime * 1000)),
                )
                DetailsRow("Location", "/" + file.parentId.take(8))
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
private fun DetailsRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
