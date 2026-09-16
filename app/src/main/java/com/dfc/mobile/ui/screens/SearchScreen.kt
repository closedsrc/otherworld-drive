package com.dfc.mobile.ui.screens

import com.dfc.mobile.ui.formatBytes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dfc.mobile.RemoteFile
import com.dfc.mobile.data.MediaItem
import com.dfc.mobile.ui.DfcViewModel
import com.dfc.mobile.ui.DfcViewModel.Kind
import com.dfc.mobile.ui.ScreenTitle
import com.dfc.mobile.ui.components.NoResults
import com.dfc.mobile.ui.theme.Radii
import com.dfc.mobile.ui.theme.Spacing
import kotlinx.coroutines.delay

/** File-type filters. "All" is the default and every other chip narrows it. */
private enum class Filter(val label: String) {
    ALL("All"),
    IMAGES("Images"),
    VIDEOS("Videos"),
    DOCUMENTS("Documents"),
    ARCHIVES("Archives"),
    FOLDERS("Folders"),
}

/**
 * Search runs over what the app has already fetched: the local backup index and
 * the folder listings opened this session. It is honest about its scope, and it
 * never shows a spinner between keystrokes: results either match or they do not.
 */
@Composable
fun SearchScreen(
    ui: DfcViewModel.Ui,
    onOpenFile: (RemoteFile) -> Unit,
    onOpenFolder: (RemoteFile) -> Unit,
    onOpenGallery: (MediaItem, List<MediaItem>) -> Unit,
    onSearchRemote: suspend (String) -> List<RemoteFile>,
    classify: (RemoteFile) -> Kind,
    modifier: Modifier = Modifier,
) {
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(Filter.ALL) }
    var remote by remember { mutableStateOf<List<RemoteFile>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }

    // Debounced server search: only fires for a real query, and only once the
    // user has stopped typing, so a fast typist does not queue round-trips.
    LaunchedEffect(query) {
        val q = query.trim()
        if (q.length < 2) {
            remote = emptyList()
            searching = false
            return@LaunchedEffect
        }
        searching = true
        delay(280)
        remote = onSearchRemote(q)
        searching = false
    }

    val needle = query.trim().lowercase()

    val localMedia = remember(needle, filter, ui.gallery) {
        if (needle.isEmpty()) emptyList()
        else ui.gallery.filter { it.displayName.lowercase().contains(needle) }
    }
    val localFiles = remember(needle, filter, ui.entries) {
        if (needle.isEmpty()) emptyList()
        else ui.entries.filter { it.name.lowercase().contains(needle) }
    }
    val serverHits = remember(needle, filter, remote) {
        if (needle.isEmpty()) emptyList()
        else remote.filter { it.name.lowercase().contains(needle) }
    }

    fun passFilter(kind: Kind): Boolean = when (filter) {
        Filter.ALL -> true
        Filter.IMAGES -> kind == Kind.IMAGE
        Filter.VIDEOS -> kind == Kind.VIDEO
        Filter.DOCUMENTS -> kind == Kind.DOCUMENT
        Filter.ARCHIVES -> kind == Kind.ARCHIVE
        Filter.FOLDERS -> kind == Kind.FOLDER
    }

    val visibleRemote = serverHits.filter { passFilter(classify(it)) }
    val visibleLocalFiles = localFiles.filter { passFilter(classify(it)) }
    val visibleMedia = localMedia.filter {
        val k = if (it.isVideo) Kind.VIDEO else Kind.IMAGE
        passFilter(k)
    }

    // Server results and locally-known files can describe the same file; the
    // server record wins because it carries the id a preview needs.
    val remoteIds = visibleRemote.map { it.id }.toSet()
    val dedupedLocalFiles = visibleLocalFiles.filter { it.id !in remoteIds }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(bottom = Spacing.navClearance),
    ) {
        item(key = "title") {
            ScreenTitle(
                title = "Search",
                subtitle = "Across this device's index and the server",
            )
        }
        item(key = "field") {
            Box(Modifier.padding(horizontal = Spacing.md)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    placeholder = { Text("File name, or part of one") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                    },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "Clear the search",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .size(20.dp)
                                    .clickable { query = "" },
                            )
                        }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    shape = RoundedCornerShape(Radii.control),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        item(key = "filters") {
            Spacer(Modifier.height(Spacing.md))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.md),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Filter.entries.forEach { option ->
                    FilterChip(
                        label = option.label,
                        selected = option == filter,
                        onClick = { filter = option },
                    )
                }
            }
        }

        when {
            query.trim().isEmpty() -> item(key = "idle") {
                Spacer(Modifier.height(Spacing.xl))
                SearchHint(
                    hasIndex = ui.gallery.isNotEmpty(),
                    backedUp = ui.backedUp,
                )
            }
            visibleRemote.isEmpty() && dedupedLocalFiles.isEmpty() && visibleMedia.isEmpty() ->
                item(key = "none") {
                    Spacer(Modifier.height(Spacing.lg))
                    if (searching) {
                        Box(
                            Modifier.fillMaxWidth().padding(Spacing.xl),
                            contentAlignment = Alignment.Center,
                        ) { CircularProgressIndicator(color = MaterialTheme.colorScheme.primary) }
                    } else {
                        NoResults(query = query.trim())
                    }
                }
            else -> {
                if (visibleMedia.isNotEmpty()) {
                    item(key = "hdr-media") {
                        ResultHeader("From this device", visibleMedia.size)
                    }
                    items(visibleMedia, key = { "m-${it.id}" }) { item ->
                        FileRowWrapper {
                            GalleryResultRow(item = item) {
                                onOpenGallery(item, visibleMedia)
                            }
                        }
                    }
                }
                if (visibleRemote.isNotEmpty() || dedupedLocalFiles.isNotEmpty()) {
                    item(key = "hdr-server") {
                        ResultHeader("On the drive", visibleRemote.size + dedupedLocalFiles.size)
                    }
                    items(visibleRemote, key = { "r-${it.id}" }) { file ->
                        if (file.isDir) {
                            FolderRow(
                                file = file,
                                selected = false,
                                selecting = false,
                                onClick = { onOpenFolder(file) },
                                onLongClick = {},
                            )
                        } else {
                            FileRow(
                                file = file,
                                kind = classify(file),
                                selected = false,
                                selecting = false,
                                onClick = { onOpenFile(file) },
                                onLongClick = {},
                            )
                        }
                    }
                    items(dedupedLocalFiles, key = { "l-${it.id}" }) { file ->
                        FileRow(
                            file = file,
                            kind = classify(file),
                            selected = false,
                            selecting = false,
                            onClick = { onOpenFile(file) },
                            onLongClick = {},
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FileRowWrapper(content: @Composable () -> Unit) = content()

/** Result row for an indexed backup: name plus its real size. */
@Composable
private fun GalleryResultRow(item: MediaItem, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        com.dfc.mobile.ui.components.Thumb(
            fileId = item.remoteId ?: "",
            kind = if (item.isVideo) Kind.VIDEO else Kind.IMAGE,
            name = item.displayName,
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(Radii.control)),
        )
        Spacer(Modifier.width(Spacing.md))
        Column(Modifier.weight(1f)) {
            Text(
                text = item.displayName,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${formatBytes(item.size)}  ·  backed up",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ResultHeader(title: String, count: Int) {
    Text(
        text = "$title  ·  $count",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(
            start = Spacing.md, top = Spacing.lg, bottom = Spacing.xs
        ),
    )
}

@Composable
private fun FilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(Radii.control)
    Box(
        modifier = Modifier
            .clip(shape)
            .background(
                if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceContainer
            )
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** What search can and cannot see, stated before the first keystroke. */
@Composable
private fun SearchHint(hasIndex: Boolean, backedUp: Int) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = if (hasIndex) "Search $backedUp indexed items" else "Nothing indexed yet",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(Spacing.xs))
        Text(
            text = "Matching runs against files this device has recorded and folders " +
                "you have opened. Names only; file contents are not indexed.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
