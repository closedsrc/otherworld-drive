package com.dfc.mobile.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.BrokenImage
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.ViewList
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dfc.mobile.RemoteFile
import com.dfc.mobile.ui.formatBytes
import com.dfc.mobile.ui.relativeTime
import com.dfc.mobile.ui.DfcViewModel.Kind
import com.dfc.mobile.ui.components.MetaLine
import com.dfc.mobile.ui.components.NoFilesYet
import com.dfc.mobile.ui.components.RowChevron
import com.dfc.mobile.ui.components.Thumb
import com.dfc.mobile.ui.theme.Radii
import com.dfc.mobile.ui.theme.Spacing

/** Grid or list; the choice is remembered for the session. */
enum class FilesLayout { GRID, LIST }

/** One folder row, used by both the folder list on Files and search results. */
@Composable
fun FolderRow(
    file: RemoteFile,
    selected: Boolean,
    selecting: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(Radii.card)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .then(
                if (selected) Modifier.background(MaterialTheme.colorScheme.primaryContainer)
                else Modifier
            )
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SelectionBox(selected = selected, selecting = selecting) {
            Icon(
                imageVector = com.dfc.mobile.ui.components.kindIcon(Kind.FOLDER, file.name),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp),
            )
        }
        Spacer(Modifier.width(Spacing.md))
        Column(Modifier.weight(1f)) {
            Text(
                text = file.name,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            MetaLine(sizeText = "Folder", timeText = relativeTime(file.modTime))
        }
        if (!selecting) RowChevron()
    }
}

/**
 * A file row: name, then real size and real modified time. Both come from the
 * server record, so nothing here is estimated.
 */
@Composable
fun FileRow(
    file: RemoteFile,
    kind: Kind,
    selected: Boolean,
    selecting: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null,
) {
    val shape = RoundedCornerShape(Radii.card)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .then(
                if (selected) Modifier.background(MaterialTheme.colorScheme.primaryContainer)
                else Modifier
            )
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SelectionBox(selected = selected, selecting = selecting) {
            Thumb(
                fileId = file.id,
                kind = kind,
                name = file.name,
                modifier = Modifier.size(38.dp).clip(RoundedCornerShape(Radii.control)),
            )
        }
        Spacer(Modifier.width(Spacing.md))
        Column(Modifier.weight(1f)) {
            Text(
                text = file.name,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            MetaLine(sizeText = formatBytes(file.size), timeText = relativeTime(file.modTime))
        }
        trailing?.invoke()
    }
}

/** Grid cell for an image or video: the preview is the cell, the name is secondary. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MediaCell(
    file: RemoteFile,
    kind: Kind,
    selected: Boolean,
    selecting: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scale by animateFloatAsState(
        targetValue = if (selected) 0.92f else 1f,
        animationSpec = tween(160),
        label = "cellScale",
    )
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .scale(scale)
            .clip(RoundedCornerShape(Radii.control))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Thumb(
            fileId = file.id,
            kind = kind,
            name = file.name,
            modifier = Modifier.fillMaxSize(),
        )
        if (selected) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.28f))
            )
        }
        if (selecting) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(Spacing.xs),
            ) {
                SelectionDot(selected = selected)
            }
        }
        Text(
            text = file.name,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                // Legibility over an arbitrary photo needs its own scrim.
                .background(Color.Black.copy(alpha = 0.42f))
                .padding(horizontal = Spacing.sm, vertical = 6.dp),
        )
    }
}

/** Leading slot of a row: becomes a checkmark once selection mode is on. */
@Composable
private fun SelectionBox(
    selected: Boolean,
    selecting: Boolean,
    content: @Composable () -> Unit,
) {
    if (!selecting) {
        content()
        return
    }
    Box(contentAlignment = Alignment.Center) {
        content()
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(Radii.control))
                .background(
                    if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
                    else Color.Black.copy(alpha = 0.18f)
                )
                .padding(Spacing.sm),
        ) {
            SelectionDot(selected = selected)
        }
    }
}

@Composable
private fun SelectionDot(selected: Boolean) {
    val bg = if (selected) MaterialTheme.colorScheme.primary
    else Color.Black.copy(alpha = 0.45f)
    Box(
        modifier = Modifier
            .size(20.dp)
            .clip(CircleShape)
            .background(bg)
            .border(
                width = if (selected) 0.dp else 1.5.dp,
                color = if (selected) Color.Transparent else Color.White.copy(alpha = 0.85f),
                shape = CircleShape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = "Selected",
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

/**
 * The folder body: files and folders with the layout toggle applied. The
 * transition between grid and list animates so the change of view reads as a
 * change of lens on the same content, not a page reload.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FolderBody(
    layout: FilesLayout,
    folders: List<RemoteFile>,
    media: List<RemoteFile>,
    otherFiles: List<RemoteFile>,
    classify: (RemoteFile) -> Kind,
    selectedIds: Set<String>,
    selecting: Boolean,
    onOpenFolder: (RemoteFile) -> Unit,
    onOpenFile: (RemoteFile) -> Unit,
    onToggle: (RemoteFile) -> Unit,
    bottomPadding: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
    onOpenWith: ((RemoteFile) -> Unit)? = null,
) {
    if (folders.isEmpty() && media.isEmpty() && otherFiles.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(bottom = bottomPadding),
            contentAlignment = Alignment.Center,
        ) { NoFilesYet() }
        return
    }

    AnimatedContent(
        targetState = layout,
        transitionSpec = {
            (fadeIn(tween(200)) + scaleIn(tween(200), initialScale = 0.97f)) togetherWith
                (fadeOut(tween(140)))
        },
        label = "layoutSwitch",
        modifier = modifier,
    ) { mode ->
        if (mode == FilesLayout.GRID) {
            GridBody(
                folders = folders,
                media = media,
                otherFiles = otherFiles,
                classify = classify,
                selectedIds = selectedIds,
                selecting = selecting,
                onOpenFolder = onOpenFolder,
                onOpenFile = onOpenFile,
                onToggle = onToggle,
                bottomPadding = bottomPadding,
            )
        } else {
            ListBody(
                folders = folders,
                media = media,
                otherFiles = otherFiles,
                classify = classify,
                selectedIds = selectedIds,
                selecting = selecting,
                onOpenFolder = onOpenFolder,
                onOpenFile = onOpenFile,
                onToggle = onToggle,
                bottomPadding = bottomPadding,
            )
        }
    }
}

@Composable
private fun GridBody(
    folders: List<RemoteFile>,
    media: List<RemoteFile>,
    otherFiles: List<RemoteFile>,
    classify: (RemoteFile) -> Kind,
    selectedIds: Set<String>,
    selecting: Boolean,
    onOpenFolder: (RemoteFile) -> Unit,
    onOpenFile: (RemoteFile) -> Unit,
    onToggle: (RemoteFile) -> Unit,
    bottomPadding: androidx.compose.ui.unit.Dp,
) {
    val gridFiles = media + otherFiles
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 112.dp),
        contentPadding = PaddingValues(
            start = Spacing.md,
            end = Spacing.md,
            top = Spacing.xs,
            bottom = bottomPadding,
        ),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(folders, key = { it.id }) { folder ->
            FolderTile(
                folder = folder,
                selected = selectedIds.contains(folder.id),
                selecting = selecting,
                onClick = { if (selecting) onToggle(folder) else onOpenFolder(folder) },
                onLongClick = { onToggle(folder) },
            )
        }
        items(gridFiles, key = { it.id }) { file ->
            val kind = classify(file)
            if (kind == Kind.IMAGE || kind == Kind.VIDEO) {
                MediaCell(
                    file = file,
                    kind = kind,
                    selected = selectedIds.contains(file.id),
                    selecting = selecting,
                    onClick = { if (selecting) onToggle(file) else onOpenFile(file) },
                    onLongClick = { onToggle(file) },
                )
            } else {
                DocumentTile(
                    file = file,
                    kind = kind,
                    selected = selectedIds.contains(file.id),
                    selecting = selecting,
                    onClick = { if (selecting) onToggle(file) else onOpenFile(file) },
                    onLongClick = { onToggle(file) },
                )
            }
        }
    }
}

/** A folder in grid view: glyph, name, and its item count when known. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FolderTile(
    folder: RemoteFile,
    selected: Boolean,
    selecting: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val shape = RoundedCornerShape(Radii.card)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(
                width = 1.dp,
                color = if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outlineVariant,
                shape = shape,
            )
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(Spacing.md),
    ) {
        Column {
            Icon(
                imageVector = com.dfc.mobile.ui.components.kindIcon(Kind.FOLDER, folder.name),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(26.dp),
            )
            Spacer(Modifier.height(Spacing.md))
            Text(
                text = folder.name,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = relativeTime(folder.modTime),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        if (selecting) {
            Box(Modifier.align(Alignment.TopEnd)) { SelectionDot(selected = selected) }
        }
    }
}

/** Non-media file in grid view: glyph plus name, no fake preview. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DocumentTile(
    file: RemoteFile,
    kind: Kind,
    selected: Boolean,
    selecting: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val shape = RoundedCornerShape(Radii.card)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(
                width = 1.dp,
                color = if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outlineVariant,
                shape = shape,
            )
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(Spacing.md),
    ) {
        Column {
            Icon(
                imageVector = com.dfc.mobile.ui.components.kindIcon(kind, file.name),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.height(Spacing.md))
            Text(
                text = file.name,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = formatBytes(file.size),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        if (selecting) {
            Box(Modifier.align(Alignment.TopEnd)) { SelectionDot(selected = selected) }
        }
    }
}

@Composable
private fun ListBody(
    folders: List<RemoteFile>,
    media: List<RemoteFile>,
    otherFiles: List<RemoteFile>,
    classify: (RemoteFile) -> Kind,
    selectedIds: Set<String>,
    selecting: Boolean,
    onOpenFolder: (RemoteFile) -> Unit,
    onOpenFile: (RemoteFile) -> Unit,
    onToggle: (RemoteFile) -> Unit,
    bottomPadding: androidx.compose.ui.unit.Dp,
) {
    LazyColumn(
        contentPadding = PaddingValues(
            start = Spacing.sm,
            end = Spacing.sm,
            top = Spacing.xs,
            bottom = bottomPadding,
        ),
        modifier = Modifier.fillMaxSize(),
    ) {
        if (folders.isNotEmpty()) {
            item(key = "hdr-folders") {
                Text(
                    text = "Folders",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(
                        start = Spacing.md, top = Spacing.sm, bottom = Spacing.xs
                    ),
                )
            }
            items(folders, key = { it.id }) { folder ->
                FolderRow(
                    file = folder,
                    selected = selectedIds.contains(folder.id),
                    selecting = selecting,
                    onClick = { if (selecting) onToggle(folder) else onOpenFolder(folder) },
                    onLongClick = { onToggle(folder) },
                )
            }
        }
        if (media.isNotEmpty() || otherFiles.isNotEmpty()) {
            item(key = "hdr-files") {
                Text(
                    text = "Files",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(
                        start = Spacing.md, top = Spacing.lg, bottom = Spacing.xs
                    ),
                )
            }
            items(media + otherFiles, key = { it.id }) { file ->
                FileRow(
                    file = file,
                    kind = classify(file),
                    selected = selectedIds.contains(file.id),
                    selecting = selecting,
                    onClick = { if (selecting) onToggle(file) else onOpenFile(file) },
                    onLongClick = { onToggle(file) },
                )
            }
        }
    }
}

/** The grid/list toggle that sits in the Files top bar. */
@Composable
fun LayoutToggle(
    layout: FilesLayout,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(Radii.control))
            .clickable(onClick = onToggle)
            .padding(Spacing.sm),
    ) {
        Icon(
            imageVector = if (layout == FilesLayout.GRID) Icons.Outlined.ViewList
            else Icons.Outlined.GridView,
            contentDescription = if (layout == FilesLayout.GRID)
                "Switch to list view" else "Switch to grid view",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
    }
}

/** Shown when a gallery thumbnail cannot be decoded by any renderer. */
@Composable
fun BrokenThumb() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Outlined.BrokenImage,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp),
        )
    }
}
