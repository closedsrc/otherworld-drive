package com.dfc.mobile.ui.screens

import com.dfc.mobile.ui.components.ErrorCard
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dfc.mobile.DfcApi
import com.dfc.mobile.RemoteFile
import com.dfc.mobile.data.MediaItem
import com.dfc.mobile.ui.BarIcon
import com.dfc.mobile.ui.DfcViewModel
import com.dfc.mobile.ui.DfcViewModel.Kind
import com.dfc.mobile.ui.theme.Radii
import com.dfc.mobile.ui.theme.Spacing

/**
 * Files is the app's core: it browses the drive itself, not just the phone's
 * backup. Long press enters selection mode, where the top bar morphs into a
 * count with the actions that can actually run against the server.
 */
@Composable
fun FilesScreen(
    ui: DfcViewModel.Ui,
    layout: FilesLayout,
    onToggleLayout: () -> Unit,
    onOpenFolder: (RemoteFile) -> Unit,
    onNavigateDepth: (Int) -> Unit,
    onOpenFile: (RemoteFile) -> Unit,
    onOpenGallery: (MediaItem, List<MediaItem>) -> Unit,
    onDelete: (List<RemoteFile>) -> Unit,
    onShare: (RemoteFile) -> Unit,
    onRefresh: () -> Unit,
    classify: (RemoteFile) -> Kind,
    modifier: Modifier = Modifier,
) {
    var selected by remember { mutableStateOf(setOf<String>()) }
    var showDelete by remember { mutableStateOf(false) }

    // A folder change invalidates the selection: keeping ids from the previous
    // listing would let a delete target files the user can no longer see.
    val pathKey = ui.folderPath.joinToString("/") { it.id }
    androidx.compose.runtime.LaunchedEffect(pathKey) { selected = emptySet() }

    val selecting = selected.isNotEmpty()
    val allEntries = ui.entries
    val selectedFiles = allEntries.filter { selected.contains(it.id) }

    val folders = allEntries.filter { it.isDir }
    val media = allEntries.filter { !it.isDir && classify(it).let { k -> k == Kind.IMAGE || k == Kind.VIDEO } }
    val others = allEntries.filter { !it.isDir && classify(it).let { k -> k != Kind.IMAGE && k != Kind.VIDEO } }

    Column(modifier) {
        AnimatedVisibility(
            visible = selecting,
            enter = fadeIn(tween(150)) + slideInVertically { -it },
            exit = fadeOut(tween(100)) + slideOutVertically { -it },
        ) {
            SelectionBar(
                count = selected.size,
                onClose = { selected = emptySet() },
                onDelete = { showDelete = true },
                onShare = { selectedFiles.firstOrNull()?.let(onShare) },
            )
        }
        AnimatedVisibility(
            visible = !selecting,
            enter = fadeIn(tween(150)),
            exit = fadeOut(tween(100)),
        ) {
            NormalBar(
                path = ui.folderPath,
                layout = layout,
                onNavigateDepth = onNavigateDepth,
                onToggleLayout = onToggleLayout,
                onRefresh = onRefresh,
                loading = ui.loading,
            )
        }

        Box(Modifier.weight(1f)) {
            when {
                ui.error != null && ui.entries.isEmpty() -> ErrorCard(
                    message = ui.error!!,
                    onRetry = onRefresh,
                    modifier = Modifier.align(Alignment.Center).padding(Spacing.md),
                )
                ui.loading && ui.entries.isEmpty() -> Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator(color = MaterialTheme.colorScheme.primary) }
                else -> FolderBody(
                    layout = layout,
                    folders = folders,
                    media = media,
                    otherFiles = others,
                    classify = classify,
                    selectedIds = selected,
                    selecting = selecting,
                    onOpenFolder = onOpenFolder,
                    onOpenFile = onOpenFile,
                    onToggle = { file ->
                        selected = if (selected.contains(file.id)) selected - file.id
                        else selected + file.id
                    },
                    bottomPadding = Spacing.navClearance,
                )
            }
        }
    }

    if (showDelete) {
        DeleteDialog(
            count = selected.size,
            onDismiss = { showDelete = false },
            onConfirm = {
                showDelete = false
                onDelete(selectedFiles)
                selected = emptySet()
            },
        )
    }
}

/** Breadcrumb path with a back arrow; each segment jumps to that level. */
@Composable
private fun NormalBar(
    path: List<RemoteFile>,
    layout: FilesLayout,
    onNavigateDepth: (Int) -> Unit,
    onToggleLayout: () -> Unit,
    onRefresh: () -> Unit,
    loading: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.sm, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (path.isNotEmpty()) {
            BarIcon(
                icon = Icons.Filled.ArrowBack,
                contentDescription = "Back to the parent folder",
                onClick = { onNavigateDepth(path.size - 1) },
            )
        } else {
            Spacer(Modifier.width(Spacing.sm))
        }

        Row(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Crumb(text = "Drive", last = path.isEmpty(), onClick = { onNavigateDepth(0) })
            path.forEachIndexed { index, folder ->
                Crumb(text = folder.name, last = index == path.lastIndex) {
                    if (index < path.lastIndex) onNavigateDepth(index + 1)
                }
            }
        }

        if (loading && path.isEmpty()) {
            Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(16.dp),
                )
            }
        } else {
            BarIcon(
                icon = Icons.Outlined.Refresh,
                contentDescription = "Reload this folder",
                onClick = onRefresh,
            )
        }
        LayoutToggle(layout = layout, onToggle = onToggleLayout)
    }
}

@Composable
private fun Crumb(text: String, last: Boolean, onClick: () -> Unit) {
    Text(
        text = text,
        style = if (last) MaterialTheme.typography.titleMedium
        else MaterialTheme.typography.bodyMedium,
        color = if (last) MaterialTheme.colorScheme.onSurface
        else MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .clip(RoundedCornerShape(Radii.control))
            .clickable(enabled = !last, onClick = onClick)
            .padding(horizontal = Spacing.sm, vertical = Spacing.sm),
    )
    if (!last) {
        Text(
            text = "/",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.outline,
        )
    }
}

/** The morphing bar: a count, then the actions that apply to the selection. */
@Composable
private fun SelectionBar(
    count: Int,
    onClose: () -> Unit,
    onDelete: () -> Unit,
    onShare: () -> Unit,
) {
    val shape = RoundedCornerShape(Radii.card)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.sm, vertical = Spacing.sm)
            .clip(shape)
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(start = Spacing.sm, end = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BarIcon(
            icon = Icons.Filled.Close,
            contentDescription = "Leave selection mode",
            onClick = onClose,
        )
        Text(
            text = if (count == 1) "1 selected" else "$count selected",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.weight(1f),
        )
        // One link at a time: the server issues per-file links, so multi-share
        // would silently drop all but the first.
        SelectionAction(
            icon = Icons.Outlined.Link,
            label = if (count == 1) "Link" else "Link (single only)",
            enabled = count == 1,
            onClick = onShare,
        )
        SelectionAction(
            icon = Icons.Outlined.DeleteOutline,
            label = "Delete",
            enabled = true,
            onClick = onDelete,
        )
    }
}

@Composable
private fun SelectionAction(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(Radii.control))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (enabled) MaterialTheme.colorScheme.onPrimaryContainer
            else MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.4f),
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (enabled) MaterialTheme.colorScheme.onPrimaryContainer
            else MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.4f),
            maxLines = 1,
        )
    }
}
