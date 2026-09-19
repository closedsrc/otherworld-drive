package com.dfc.mobile.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dfc.mobile.DfcApi
import com.dfc.mobile.RemoteFile
import com.dfc.mobile.ui.BarIcon
import com.dfc.mobile.ui.components.EmptyState
import com.dfc.mobile.ui.ScreenTitle
import com.dfc.mobile.ui.formatBytes
import com.dfc.mobile.ui.theme.Radii
import com.dfc.mobile.ui.theme.Spacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.dfc.mobile.ui.theme.currentType

/**
 * The drive's trash. Restore puts items back where they were; purge removes
 * them for good — the server queues the stored bytes for destruction and
 * reconciles on its own, so "deleted" here means the catalog row is gone and
 * the remote cleanup is scheduled, not merely hoped for.
 */
@Composable
fun TrashScreen(
    onBack: () -> Unit,
    onMessage: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var items by remember { mutableStateOf<List<RemoteFile>?>(null) }
    var busy by remember { mutableStateOf(false) }
    var showEmptyConfirm by remember { mutableStateOf(false) }
    var purgeTarget by remember { mutableStateOf<RemoteFile?>(null) }

    fun reload() {
        scope.launch {
            items = withContext(Dispatchers.IO) {
                runCatching { DfcApi.get(context).listTrash() }.getOrElse { emptyList() }
            }
        }
    }
    LaunchedEffect(Unit) { reload() }

    Column(modifier.fillMaxSize()) {
        ScreenTitle(title = "Trash")
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = Spacing.sm, end = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BarIcon(icon = Icons.Filled.ArrowBack, contentDescription = "Back", onClick = onBack)
            Spacer(Modifier.width(Spacing.sm))
            Text(
                text = when {
                    items == null -> "Reading…"
                    items!!.isEmpty() -> "Nothing trashed"
                    items!!.size == 1 -> "1 item"
                    else -> "${items!!.size} items"
                },
                style = currentType.item,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            if (!items.isNullOrEmpty() && !busy) {
                TextButton(onClick = { showEmptyConfirm = true }) { Text("Empty trash") }
            }
        }

        when {
            items == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
            items!!.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                EmptyState(
                    icon = Icons.Outlined.DeleteOutline,
                    title = "The trash is empty",
                    body = "Deleted files land here first, so a mistake is never final.",
                )
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = Spacing.md, end = Spacing.md,
                    top = Spacing.sm, bottom = Spacing.navClearance,
                ),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                items(items!!, key = { it.id }) { file ->
                    TrashedRow(
                        file = file,
                        busy = busy,
                        onRestore = {
                            busy = true
                            scope.launch {
                                val ok = withContext(Dispatchers.IO) {
                                    runCatching { DfcApi.get(context).restoreFiles(listOf(file.id)) }
                                        .getOrDefault(false)
                                }
                                busy = false
                                if (ok) {
                                    onMessage("Restored \"${file.name}\"")
                                    reload()
                                } else onMessage("Could not restore \"${file.name}\"")
                            }
                        },
                        onPurge = { purgeTarget = file },
                    )
                }
            }
        }
    }

    if (showEmptyConfirm) {
        AlertDialog(
            onDismissRequest = { showEmptyConfirm = false },
            title = { Text("Empty the trash?") },
            text = { Text("Everything in it is removed for good. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    showEmptyConfirm = false
                    busy = true
                    scope.launch {
                        val ok = withContext(Dispatchers.IO) {
                            runCatching { DfcApi.get(context).purgeTrash(emptyList()) }
                                .getOrDefault(false)
                        }
                        busy = false
                        if (ok) onMessage("Trash emptied") else onMessage("Could not empty the trash")
                        reload()
                    }
                }) { Text("Empty", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showEmptyConfirm = false }) { Text("Cancel") } },
        )
    }

    purgeTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { purgeTarget = null },
            title = { Text("Delete \"${target.name}\" for good?") },
            text = { Text("This cannot be undone. The stored copies are removed after this.") },
            confirmButton = {
                TextButton(onClick = {
                    purgeTarget = null
                    busy = true
                    scope.launch {
                        val ok = withContext(Dispatchers.IO) {
                            runCatching { DfcApi.get(context).purgeTrash(listOf(target.id)) }
                                .getOrDefault(false)
                        }
                        busy = false
                        if (ok) onMessage("Deleted for good") else onMessage("Could not delete it")
                        reload()
                    }
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { purgeTarget = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun TrashedRow(
    file: RemoteFile,
    busy: Boolean,
    onRestore: () -> Unit,
    onPurge: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radii.card))
            .padding(horizontal = Spacing.sm, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Outlined.Description,
            contentDescription = "File",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(Spacing.md))
        Column(Modifier.weight(1f)) {
            Text(
                text = file.name,
                style = currentType.body,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = formatBytes(file.size),
                style = currentType.meta,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = onRestore, enabled = !busy) {
            Icon(Icons.Outlined.Restore, contentDescription = "Restore")
            Spacer(Modifier.width(Spacing.xs))
            Text("Restore")
        }
        TextButton(onClick = onPurge, enabled = !busy) {
            Text("Delete", color = MaterialTheme.colorScheme.error)
        }
    }
}
