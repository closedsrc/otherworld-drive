package com.dfc.mobile.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Sort
import androidx.compose.material.icons.outlined.ViewList
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import com.dfc.mobile.ui.components.kindIcon
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dfc.mobile.RemoteFile
import com.dfc.mobile.ui.DfcViewModel
import com.dfc.mobile.ui.components.DfcCard
import com.dfc.mobile.ui.components.EmptyState
import com.dfc.mobile.ui.components.Thumb
import com.dfc.mobile.ui.components.minTouchSize
import com.dfc.mobile.ui.formatBytes
import com.dfc.mobile.ui.relativeTime
import com.dfc.mobile.ui.theme.Radii
import com.dfc.mobile.ui.theme.Spacing
import com.dfc.mobile.ui.theme.currentType
import com.dfc.mobile.ui.theme.windowSize

/**
 * How the folder is ordered. The old screen had a fixed server order and no
 * control — on a wall of eight identical columns nothing was findable.
 */
enum class SortKey(val label: String) {
    NAME("Name"),
    DATE("Date modified"),
    SIZE("Size"),
    KIND("Kind"),
}

/**
 * The file browser, rebuilt.
 *
 * What was wrong: files were appended into the *folder* row (one PDF sitting in
 * a horizontal scroller of folders), cards had mismatched heights, names wrapped
 * mid-word ("applicationsix. pdf"), there was no sort, no date or type metadata,
 * selection was long-press-only with two verbs, and the whole thing was a phone
 * layout stretched to 1600px with no density or column adaptation.
 *
 * Now: folders and files are separate sections, every row carries name + date +
 * size, sort is explicit, selection is tap-to-toggle once you are in selection
 * mode (and long-press to enter it), and the grid/readable list adapts to the
 * window class with a capped content column on tablets.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FilesScreen(
    ui: DfcViewModel.Ui,
    sort: SortKey,
    onSortChange: (SortKey) -> Unit,
    layout: FilesLayout,
    onToggleLayout: () -> Unit,
    onOpenFolder: (RemoteFile) -> Unit,
    onNavigateDepth: (Int) -> Unit,
    onOpenFile: (RemoteFile) -> Unit,
    onDelete: (List<RemoteFile>) -> Unit,
    onShare: (RemoteFile) -> Unit,
    onRename: (RemoteFile) -> Unit,
    onDownload: (RemoteFile) -> Unit,
    onDetails: (RemoteFile) -> Unit,
    onRefresh: () -> Unit,
    onCreateFolder: () -> Unit,
    onOpenTrash: () -> Unit,
    modifier: Modifier = Modifier,
    classify: (RemoteFile) -> DfcViewModel.Kind,
) {
    var selected by remember { mutableStateOf(setOf<String>()) }
    var sortMenu by remember { mutableStateOf(false) }

    // A folder change invalidates the selection: ids from the previous listing
    // would let a delete target files the user can no longer see.
    val pathKey = ui.folderPath.joinToString("/") { it.id }
    LaunchedEffect(pathKey) { selected = emptySet() }

    val selecting = selected.isNotEmpty()
    val allEntries = ui.entries
    val selectedFiles = allEntries.filter { selected.contains(it.id) }
    val w = windowSize

    val folders = remember(allEntries, sort) { sortEntries(allEntries.filter { it.isDir }, sort, classify) }
    val files = remember(allEntries, sort) { sortEntries(allEntries.filter { !it.isDir }, sort, classify) }

    Column(modifier.fillMaxSize()) {
        if (selecting) {
            SelectionBar(
                count = selected.size,
                onClose = { selected = emptySet() },
                onShare = { selectedFiles.firstOrNull()?.let(onShare) },
                onRename = { selectedFiles.firstOrNull()?.let(onRename) },
                onDownload = { selectedFiles.firstOrNull()?.let(onDownload) },
                onDetails = { selectedFiles.firstOrNull()?.let(onDetails) },
                onDelete = { onDelete(selectedFiles); selected = emptySet() },
            )
        } else {
            FilesTopBar(
                path = ui.folderPath,
                sort = sort,
                sortMenu = sortMenu,
                onSortMenu = { sortMenu = it },
                onSortChange = { onSortChange(it); sortMenu = false },
                layout = layout,
                onToggleLayout = onToggleLayout,
                onRefresh = onRefresh,
                onCreateFolder = onCreateFolder,
                onOpenTrash = onOpenTrash,
                onNavigateDepth = onNavigateDepth,
                loading = ui.loading,
            )
        }

        Box(Modifier.weight(1f)) {
            when {
                ui.error != null && allEntries.isEmpty() -> EmptyState(
                    icon = Icons.Outlined.Description,
                    title = "Could not load this folder",
                    body = ui.error ?: "",
                    modifier = Modifier.align(Alignment.Center).fillMaxWidth(),
                    action = { com.dfc.mobile.ui.components.DfcPrimaryButton("Try again", onRefresh) },
                )
                ui.loading && allEntries.isEmpty() -> Box(
                    Modifier.fillMaxSize(), contentAlignment = Alignment.Center,
                ) { androidx.compose.material3.CircularProgressIndicator() }
                allEntries.isEmpty() -> EmptyState(
                    icon = Icons.Outlined.Folder,
                    title = "This folder is empty",
                    body = "Upload from the web dashboard, or back up your photos.",
                    modifier = Modifier.align(Alignment.Center).fillMaxWidth(),
                )
                layout == FilesLayout.GRID -> FolderGrid(
                    folders = folders,
                    files = files,
                    selected = selected,
                    selecting = selecting,
                    classify = classify,
                    onOpenFolder = { if (selecting) selected = toggle(selected, it.id) else onOpenFolder(it) },
                    onOpenFile = { if (selecting) selected = toggle(selected, it.id) else onOpenFile(it) },
                    onToggle = { selected = toggle(selected, it.id) },
                )
                else -> FolderList(
                    folders = folders,
                    files = files,
                    selected = selected,
                    selecting = selecting,
                    classify = classify,
                    onOpenFolder = { if (selecting) selected = toggle(selected, it.id) else onOpenFolder(it) },
                    onOpenFile = { if (selecting) selected = toggle(selected, it.id) else onOpenFile(it) },
                    onToggle = { selected = toggle(selected, it.id) },
                )
            }
        }
    }
}

enum class FilesLayout { GRID, LIST }

private fun toggle(current: Set<String>, id: String): Set<String> =
    if (current.contains(id)) current - id else current + id

private fun sortEntries(
    list: List<RemoteFile>,
    sort: SortKey,
    classify: (RemoteFile) -> DfcViewModel.Kind,
): List<RemoteFile> = when (sort) {
    SortKey.NAME -> list.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
    SortKey.DATE -> list.sortedByDescending { it.modTime }
    SortKey.SIZE -> list.sortedByDescending { it.size }
    SortKey.KIND -> list.sortedBy { classify(it).name }
}

// ---------------------------------------------------------------- top bar

@Composable
private fun FilesTopBar(
    path: List<RemoteFile>,
    sort: SortKey,
    sortMenu: Boolean,
    onSortMenu: (Boolean) -> Unit,
    onSortChange: (SortKey) -> Unit,
    layout: FilesLayout,
    onToggleLayout: () -> Unit,
    onRefresh: () -> Unit,
    onCreateFolder: () -> Unit,
    onOpenTrash: () -> Unit,
    onNavigateDepth: (Int) -> Unit,
    loading: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = Spacing.sm, end = Spacing.sm, top = Spacing.md, bottom = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (path.isNotEmpty()) {
            IconButton(
                onClick = { onNavigateDepth(path.size - 1) },
                modifier = Modifier.minTouchSize(),
            ) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back to parent folder")
            }
        } else {
            Spacer(Modifier.width(Spacing.sm))
        }
        Text(
            text = path.lastOrNull()?.name ?: "Drive",
            style = currentType.title,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (loading) {
            androidx.compose.material3.CircularProgressIndicator(
                modifier = Modifier.size(18.dp), strokeWidth = 2.dp,
            )
        }
        Box {
            IconButton(onClick = { onSortMenu(true) }, modifier = Modifier.minTouchSize()) {
                Icon(Icons.Outlined.Sort, contentDescription = "Sort")
            }
            DropdownMenu(expanded = sortMenu, onDismissRequest = { onSortMenu(false) }) {
                SortKey.entries.forEach { key ->
                    DropdownMenuItem(
                        text = { Text(key.label) },
                        onClick = { onSortChange(key) },
                        leadingIcon = if (key == sort) {
                            { Icon(Icons.Outlined.CheckCircle, contentDescription = "Current sort") }
                        } else null,
                    )
                }
            }
        }
        IconButton(onClick = onToggleLayout, modifier = Modifier.minTouchSize()) {
            Icon(
                if (layout == FilesLayout.GRID) Icons.Outlined.ViewList else Icons.Outlined.GridView,
                contentDescription = "Switch to ${if (layout == FilesLayout.GRID) "list" else "grid"} view",
            )
        }
        IconButton(onClick = onOpenTrash, modifier = Modifier.minTouchSize()) {
            Icon(Icons.Outlined.Restore, contentDescription = "Open trash")
        }
        IconButton(onClick = onCreateFolder, modifier = Modifier.minTouchSize()) {
            Icon(Icons.Outlined.CreateNewFolder, contentDescription = "New folder")
        }
        IconButton(onClick = onRefresh, modifier = Modifier.minTouchSize()) {
            Icon(Icons.Outlined.Restore, contentDescription = "Reload folder")
        }
    }
}

// ------------------------------------------------------------ selection bar

@Composable
private fun SelectionBar(
    count: Int,
    onClose: () -> Unit,
    onShare: () -> Unit,
    onRename: () -> Unit,
    onDownload: () -> Unit,
    onDetails: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = Spacing.sm, end = Spacing.sm, top = Spacing.md, bottom = Spacing.sm),
        shape = RoundedCornerShape(Radii.card),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        tonalElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier.padding(start = Spacing.sm, end = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onClose, modifier = Modifier.minTouchSize()) {
                Icon(Icons.Outlined.Close, contentDescription = "Leave selection")
            }
            Text(
                text = if (count == 1) "1 selected" else "$count selected",
                style = currentType.item,
                modifier = Modifier.weight(1f),
            )
            // Per-file actions are single-only: the server renames one row and
            // issues one link at a time.
            SelectionAction(Icons.Outlined.Link, "Share link", count == 1, onShare)
            SelectionAction(Icons.Outlined.Edit, "Rename", count == 1, onRename)
            SelectionAction(Icons.Outlined.Download, "Save to device", count == 1, onDownload)
            SelectionAction(Icons.Outlined.Info, "Details", count == 1, onDetails)
            SelectionAction(Icons.Outlined.Delete, "Delete", true, onDelete)
        }
    }
}

@Composable
private fun SelectionAction(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.minTouchSize(),
    ) {
        Icon(icon, contentDescription = label)
    }
}

// ------------------------------------------------------------------- bodies

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FolderGrid(
    folders: List<RemoteFile>,
    files: List<RemoteFile>,
    selected: Set<String>,
    selecting: Boolean,
    classify: (RemoteFile) -> DfcViewModel.Kind,
    onOpenFolder: (RemoteFile) -> Unit,
    onOpenFile: (RemoteFile) -> Unit,
    onToggle: (RemoteFile) -> Unit,
) {
    val w = windowSize
    BoxWithConstraints(Modifier.fillMaxSize()) {
        // Real density: column width drives the count instead of a fixed 3.
        val minCol = if (w.isCompact) 104.dp else 132.dp
        val count = maxOf(2, (maxWidth / minCol).toInt())
        LazyVerticalGrid(
            columns = GridCells.Fixed(count),
            contentPadding = PaddingValues(
                start = Spacing.md, end = Spacing.md,
                top = Spacing.sm, bottom = Spacing.navClearance,
            ),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            items(folders, key = { it.id }) { folder ->
                GridTile(
                    file = folder, kind = DfcViewModel.Kind.FOLDER,
                    selected = selected.contains(folder.id), selecting = selecting,
                    onClick = { onOpenFolder(folder) }, onLongClick = { onToggle(folder) },
                )
            }
            items(files, key = { it.id }) { file ->
                GridTile(
                    file = file, kind = classify(file),
                    selected = selected.contains(file.id), selecting = selecting,
                    onClick = { onOpenFile(file) }, onLongClick = { onToggle(file) },
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GridTile(
    file: RemoteFile,
    kind: DfcViewModel.Kind,
    selected: Boolean,
    selecting: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val isMedia = kind == DfcViewModel.Kind.IMAGE || kind == DfcViewModel.Kind.VIDEO
    Surface(
        modifier = Modifier
            .semantics { contentDescription = file.name }
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        shape = RoundedCornerShape(Radii.tile),
        tonalElevation = if (selected) 4.dp else 0.dp,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Box(Modifier.fillMaxWidth()) {
            Column {
                // Fixed-height media area: the old grid mixed a taller PDF card
                // into a row of folder cards because each tile sized itself.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(96.dp)
                        .clip(RoundedCornerShape(Radii.tile)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (isMedia) {
                        Thumb(
                            fileId = file.id, kind = kind, name = file.name,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        Icon(
                            imageVector = kindIcon(kind, file.name),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(28.dp),
                        )
                    }
                    if (selected) {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)),
                        )
                    }
                }
                Column(Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.sm)) {
                    Text(
                        text = file.name,
                        style = currentType.meta,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = if (file.isDir) "Folder" else formatBytes(file.size),
                        style = currentType.meta,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
            if (selecting) {
                Box(Modifier.align(Alignment.TopEnd).padding(Spacing.xs)) {
                    Icon(
                        if (selected) Icons.Outlined.CheckCircle else Icons.Outlined.Circle,
                        contentDescription = if (selected) "Selected" else "Not selected",
                        tint = if (selected) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color(0x99FFFFFF),
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FolderList(
    folders: List<RemoteFile>,
    files: List<RemoteFile>,
    selected: Set<String>,
    selecting: Boolean,
    classify: (RemoteFile) -> DfcViewModel.Kind,
    onOpenFolder: (RemoteFile) -> Unit,
    onOpenFile: (RemoteFile) -> Unit,
    onToggle: (RemoteFile) -> Unit,
) {
    LazyColumn(
        contentPadding = PaddingValues(
            start = Spacing.md, end = Spacing.md,
            top = Spacing.sm, bottom = Spacing.navClearance,
        ),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        if (folders.isNotEmpty()) {
            item(key = "h-folders") { SectionHeader("Folders") }
            items(folders, key = { it.id }) { folder ->
                FileRow(
                    file = folder, kind = DfcViewModel.Kind.FOLDER,
                    selected = selected.contains(folder.id), selecting = selecting,
                    onClick = { onOpenFolder(folder) }, onLongClick = { onToggle(folder) },
                )
            }
        }
        if (files.isNotEmpty()) {
            item(key = "h-files") { SectionHeader("Files") }
            items(files, key = { it.id }) { file ->
                FileRow(
                    file = file, kind = classify(file),
                    selected = selected.contains(file.id), selecting = selecting,
                    onClick = { onOpenFile(file) }, onLongClick = { onToggle(file) },
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileRow(
    file: RemoteFile,
    kind: DfcViewModel.Kind,
    selected: Boolean,
    selecting: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val isMedia = kind == DfcViewModel.Kind.IMAGE || kind == DfcViewModel.Kind.VIDEO
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = file.name }
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        shape = RoundedCornerShape(Radii.control),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surface,
    ) {
        Row(
            modifier = Modifier.padding(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (selecting) {
                Icon(
                    if (selected) Icons.Outlined.CheckCircle else Icons.Outlined.Circle,
                    contentDescription = if (selected) "Selected" else "Not selected",
                    tint = if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.width(Spacing.sm))
            }
            // A 40dp preview instead of a bare icon: the list should still show
            // what the file is.
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(Radii.tile))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center,
            ) {
                if (isMedia) {
                    Thumb(
                        fileId = file.id, kind = kind, name = file.name,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Icon(
                        imageVector = kindIcon(kind, file.name),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            Spacer(Modifier.width(Spacing.md))
            Column(Modifier.weight(1f)) {
                Text(
                    text = file.name,
                    style = currentType.item,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                // Date + size: the old rows showed size only, which is why a
                // folder of 200 backups was indistinguishable from a wall.
                Text(
                    text = buildString {
                        append(relativeTime(file.modTime))
                        if (!file.isDir) {
                            append(" · ")
                            append(formatBytes(file.size))
                        }
                    },
                    style = currentType.meta,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
