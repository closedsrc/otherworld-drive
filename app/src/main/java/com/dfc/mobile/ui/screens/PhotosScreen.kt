package com.dfc.mobile.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.CloudQueue
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dfc.mobile.backup.BackupGate
import com.dfc.mobile.backup.UploadTracker
import com.dfc.mobile.data.MediaItem
import com.dfc.mobile.ui.BarIcon
import com.dfc.mobile.ui.DfcViewModel
import com.dfc.mobile.ui.DfcViewModel.LibraryFilter
import com.dfc.mobile.ui.components.EmptyState
import com.dfc.mobile.ui.components.ErrorCard
import com.dfc.mobile.ui.components.GhostButton
import com.dfc.mobile.ui.components.PhotoTimeline
import com.dfc.mobile.ui.components.PrimaryButton
import com.dfc.mobile.ui.components.SelectionAction
import com.dfc.mobile.ui.formatBytes
import com.dfc.mobile.ui.groupByDays
import com.dfc.mobile.ui.itemCount
import com.dfc.mobile.ui.pressFeedback
import com.dfc.mobile.ui.theme.Radii
import com.dfc.mobile.ui.theme.Spacing
import com.dfc.mobile.ui.theme.currentType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** Index states the library grid reasons about. */
private const val STATE_PENDING = 0
private const val STATE_UPLOADED = 1
private const val STATE_FAILED = 2

/** How often the strip re-reads the network and battery while work is waiting. */
private const val GATE_POLL_MS = 4_000L

/**
 * The library: everything on this phone, newest first, under the date it was
 * taken. This is the app's start destination, because "what is on my phone and
 * is it safe" is the question the app exists to answer.
 *
 * The chrome is deliberately thin. A photo product should open onto photographs,
 * not onto three stacked bands of status furniture; the backup state is one
 * slim line that expands into the transfer sheet on tap, and the filters are a
 * single quiet row.
 */
@Composable
fun PhotosScreen(
    ui: DfcViewModel.Ui,
    filter: LibraryFilter,
    onFilterChange: (LibraryFilter) -> Unit,
    onOpenItem: (MediaItem, List<MediaItem>) -> Unit,
    onBackupNow: () -> Unit,
    onOpenUploads: () -> Unit,
    onOpenAlbums: () -> Unit,
    onOpenAlbum: (String) -> Unit,
    onOpenSearch: () -> Unit,
    onOpenSetup: () -> Unit,
    onRequestAccess: () -> Unit,
    onShareId: (String) -> Unit,
    onRefresh: () -> Unit,
    hasPhotoAccess: Boolean,
    modifier: Modifier = Modifier,
) {
    var selected by remember { mutableStateOf(setOf<Long>()) }
    val selecting = selected.isNotEmpty()

    val visible = remember(ui.timeline, filter) {
        when (filter) {
            LibraryFilter.ALL -> ui.timeline
            LibraryFilter.PHOTOS -> ui.timeline.filter { !it.isVideo }
            LibraryFilter.VIDEOS -> ui.timeline.filter { it.isVideo }
            LibraryFilter.PENDING -> ui.timeline.filter {
                it.state == STATE_PENDING || it.state == STATE_FAILED
            }
        }
    }
    val sections = remember(visible) { groupByDays(visible) }

    val photoCount = ui.timeline.count { !it.isVideo }
    val videoCount = ui.timeline.count { it.isVideo }
    val waitingCount = ui.timeline.count {
        it.state == STATE_PENDING || it.state == STATE_FAILED
    }

    Column(modifier) {
        AnimatedContent(
            targetState = selecting,
            transitionSpec = {
                (fadeIn(tween(150)) + slideInVertically { -it / 3 }) togetherWith
                    (fadeOut(tween(100)) + slideOutVertically { -it / 3 })
            },
            label = "photosBar",
        ) { inSelection ->
            if (inSelection) {
                val chosen = ui.timeline.filter { selected.contains(it.id) }
                val shareable = chosen.singleOrNull()?.takeIf { it.state == STATE_UPLOADED }
                PhotosSelectionBar(
                    count = selected.size,
                    linkEnabled = shareable != null,
                    onClose = { selected = emptySet() },
                    onBackupNow = onBackupNow,
                    onShareLink = { shareable?.remoteId?.let(onShareId) },
                )
            } else {
                PhotosTopBar(ui = ui, onOpenSearch = onOpenSearch)
            }
        }

        if (!ui.configured) {
            SetupStrip(onOpenSetup = onOpenSetup)
        } else if (ui.pending > 0 || ui.lastBackupAt > 0L) {
            BackupLine(
                pending = ui.pending,
                lastBackupAt = ui.lastBackupAt,
                onBackupNow = onBackupNow,
                onOpenUploads = onOpenUploads,
            )
        }

        if (ui.timeline.isNotEmpty()) {
            FilterChips(
                filter = filter,
                all = ui.timeline.size,
                photos = photoCount,
                videos = videoCount,
                waiting = waitingCount,
                onFilterChange = onFilterChange,
            )
        }

        Box(Modifier.weight(1f)) {
            when {
                ui.indexError != null && ui.timeline.isEmpty() -> ErrorCard(
                    message = ui.indexError!!,
                    onRetry = onRefresh,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(Spacing.md),
                )

                ui.indexLoading && ui.timeline.isEmpty() -> Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.size(Spacing.md))
                    Text(
                        text = "Reading this phone's photos",
                        style = currentType.bodyMuted,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                ui.timeline.isEmpty() -> EmptyLibrary(
                    hasPhotoAccess = hasPhotoAccess,
                    onRequestAccess = onRequestAccess,
                    modifier = Modifier.align(Alignment.Center),
                )

                visible.isEmpty() -> EmptyFilter(
                    filter = filter,
                    onShowAll = { onFilterChange(LibraryFilter.ALL) },
                    modifier = Modifier.align(Alignment.Center),
                )

                else -> PhotoTimeline(
                    sections = sections,
                    selectedIds = selected,
                    selecting = selecting,
                    onOpen = { item -> onOpenItem(item, visible) },
                    onToggle = { item ->
                        selected = if (selected.contains(item.id)) selected - item.id
                        else selected + item.id
                    },
                    bottomPadding = Spacing.navClearance,
                    columns = com.dfc.mobile.ui.theme.windowSize.gridColumns,
                    header = if (filter == LibraryFilter.ALL && ui.albums.size >= 2) {
                        { AlbumStripSection(ui.albums, onOpenAlbum, onOpenAlbums) }
                    } else null,
                )
            }
        }
    }
}

/**
 * Title, what the library holds, and search. The title is the largest thing on
 * the screen and the count sits under it as one muted line — the old header
 * spent three lines ("Photos" / "5 items · 2 albums" / a backup card) before the
 * first photograph appeared.
 */
@Composable
private fun PhotosTopBar(ui: DfcViewModel.Ui, onOpenSearch: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = Spacing.md, end = Spacing.sm, top = Spacing.lg, bottom = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = "Photos",
                style = currentType.display,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = when {
                    ui.indexLoading && ui.timeline.isEmpty() -> "Reading this phone"
                    ui.timeline.isEmpty() -> "Nothing indexed yet"
                    ui.albums.size >= 2 -> "${itemCount(ui.timeline.size)}  ·  ${ui.albums.size} albums"
                    else -> itemCount(ui.timeline.size)
                },
                style = currentType.meta,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        BarIcon(
            icon = Icons.Filled.Search,
            contentDescription = "Search this phone and the drive",
            onClick = onOpenSearch,
        )
    }
}

/**
 * The selection header. Only actions the server actually backs are offered: a
 * run can be started, and a link can be issued for a single file.
 */
@Composable
private fun PhotosSelectionBar(
    count: Int,
    linkEnabled: Boolean,
    onClose: () -> Unit,
    onBackupNow: () -> Unit,
    onShareLink: () -> Unit,
) {
    val shape = RoundedCornerShape(Radii.card)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = Spacing.sm, end = Spacing.sm, top = Spacing.md, bottom = Spacing.sm)
            .clip(shape)
            .background(MaterialTheme.colorScheme.primaryContainer),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BarIcon(
            icon = Icons.Filled.Close,
            contentDescription = "Leave selection mode",
            onClick = onClose,
        )
        Text(
            text = if (count == 1) "1 selected" else "$count selected",
            style = currentType.item,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.weight(1f),
        )
        SelectionAction(
            icon = Icons.Outlined.Upload,
            label = "Back up",
            onClick = onBackupNow,
        )
        SelectionAction(
            icon = Icons.Outlined.Link,
            label = if (linkEnabled) "Link" else "Link (1)",
            enabled = linkEnabled,
            onClick = onShareLink,
        )
    }
}

/**
 * One slim line of backup state. It is a status, not a card: it collapses to a
 * single row with a dot, a sentence and one action, and tapping it opens the
 * transfer sheet where the detail lives.
 *
 * A pending count on its own says nothing about whether anything is wrong, so
 * the label names the condition the run is held on — without it, an app holding
 * off on mobile data and an app that is stuck look identical.
 */
@Composable
private fun BackupLine(
    pending: Int,
    lastBackupAt: Long,
    onBackupNow: () -> Unit,
    onOpenUploads: () -> Unit,
) {
    val running by UploadTracker.runActive.collectAsState()
    val job by UploadTracker.current.collectAsState()
    val context = LocalContext.current
    val gate by produceState(BackupGate.READY, pending) {
        if (pending <= 0) return@produceState
        while (true) {
            value = withContext(Dispatchers.IO) { BackupGate.check(context) }
            delay(GATE_POLL_MS)
        }
    }
    val interaction = remember { MutableInteractionSource() }

    val busy = job != null || running
    val dotColor = when {
        busy -> MaterialTheme.colorScheme.primary
        pending > 0 && gate != BackupGate.READY -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.primary
    }
    val label = when {
        job != null -> buildString {
            append("Sending ${job!!.displayName}")
            if (job!!.bytesPerSecond > 0L) append("  ·  ${formatBytes(job!!.bytesPerSecond)}/s")
        }
        running -> "Checking for new photos"
        pending > 0 -> "${itemCount(pending)} ${gate.waitingLabel}"
        lastBackupAt > 0L -> "Everything is backed up"
        else -> "No backup has run yet"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.md)
            .pressFeedback(interaction)
            .clip(RoundedCornerShape(Radii.control))
            .clickable(interactionSource = interaction, indication = null, onClick = onOpenUploads)
            .heightIn(min = 40.dp)
            .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(dotColor),
        )
        Spacer(Modifier.width(Spacing.sm))
        Text(
            text = label,
            style = currentType.meta,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (!busy) {
            Spacer(Modifier.width(Spacing.sm))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(Radii.control))
                    .clickable(onClick = onBackupNow)
                    .heightIn(min = 32.dp)
                    .padding(horizontal = Spacing.sm),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Back up",
                    style = currentType.meta,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                )
            }
        }
    }
}

/** No drive configured. One action, and it goes to the screen that fixes it. */
@Composable
private fun SetupStrip(onOpenSetup: () -> Unit) {
    val shape = RoundedCornerShape(Radii.control)
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.md)
            .pressFeedback(interaction)
            .clip(shape)
            .background(MaterialTheme.colorScheme.primaryContainer)
            .clickable(interactionSource = interaction, indication = null, onClick = onOpenSetup)
            .heightIn(min = 48.dp)
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Outlined.CloudOff,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(Spacing.sm))
        Text(
            text = "No drive connected, so nothing is being backed up",
            style = currentType.meta,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(Spacing.sm))
        Text(
            text = "Set up",
            style = currentType.meta,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            maxLines = 1,
        )
    }
}

/** Counts on the chips are the size of what tapping them shows. */
@Composable
private fun FilterChips(
    filter: LibraryFilter,
    all: Int,
    photos: Int,
    videos: Int,
    waiting: Int,
    onFilterChange: (LibraryFilter) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        CountChip("All", all, filter == LibraryFilter.ALL) { onFilterChange(LibraryFilter.ALL) }
        CountChip("Photos", photos, filter == LibraryFilter.PHOTOS) {
            onFilterChange(LibraryFilter.PHOTOS)
        }
        CountChip("Videos", videos, filter == LibraryFilter.VIDEOS) {
            onFilterChange(LibraryFilter.VIDEOS)
        }
        CountChip("Waiting", waiting, filter == LibraryFilter.PENDING) {
            onFilterChange(LibraryFilter.PENDING)
        }
    }
}

@Composable
private fun CountChip(label: String, count: Int, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Row(
        modifier = Modifier
            .clip(shape)
            .background(
                if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceContainerHigh
            )
            .clickable(onClick = onClick)
            .heightIn(min = 36.dp)
            .padding(horizontal = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = currentType.meta,
            color = if (selected) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
        Spacer(Modifier.width(Spacing.xs))
        Text(
            text = count.toString(),
            style = currentType.meta,
            color = if (selected) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.75f)
            else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

/** The album shelf, inside the grid so it scrolls away with the photos. */
@Composable
private fun AlbumStripSection(
    albums: List<DfcViewModel.Album>,
    onOpenAlbum: (String) -> Unit,
    onSeeAll: () -> Unit,
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = Spacing.md, end = Spacing.md, top = Spacing.md)
                .clickable(onClick = onSeeAll),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Albums",
                style = currentType.item,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "See all",
                style = currentType.meta,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(Modifier.size(Spacing.sm))
        AlbumStrip(
            albums = albums,
            onOpenAlbum = onOpenAlbum,
            onSeeAll = onSeeAll,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.size(Spacing.xs))
    }
}

/**
 * Nothing indexed. The two reasons are different screens: no photo access means
 * the app cannot see the library and there is an action that fixes it, while an
 * empty library is a fact with nothing to do.
 */
@Composable
private fun EmptyLibrary(
    hasPhotoAccess: Boolean,
    onRequestAccess: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        if (hasPhotoAccess) {
            EmptyState(
                icon = Icons.Outlined.PhotoLibrary,
                title = "Nothing on this phone yet",
                body = "The library is empty. Photos and videos appear here as soon as this " +
                    "phone has any, and the next backup run sends them to the drive.",
            )
        } else {
            EmptyState(
                icon = Icons.Outlined.CloudOff,
                title = "Photo access is off",
                body = "This app can only back up photos it is allowed to read. Grant photo " +
                    "access and the next run will index this phone.",
            )
            Spacer(Modifier.size(Spacing.md))
            PrimaryButton(text = "Allow photo access", onClick = onRequestAccess)
        }
    }
}

/** The library has items, just none in this filter. */
@Composable
private fun EmptyFilter(
    filter: LibraryFilter,
    onShowAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        when (filter) {
            LibraryFilter.PENDING -> EmptyState(
                icon = Icons.Outlined.CloudDone,
                title = "Everything is backed up",
                body = "Every photo and video on this phone is already on the drive.",
            )
            LibraryFilter.VIDEOS -> EmptyState(
                icon = Icons.Outlined.Videocam,
                title = "No videos on this phone",
                body = "This phone's library holds photos only.",
            )
            LibraryFilter.PHOTOS -> EmptyState(
                icon = Icons.Outlined.Image,
                title = "No photos on this phone",
                body = "This phone's library holds videos only.",
            )
            LibraryFilter.ALL -> EmptyState(
                icon = Icons.Outlined.PhotoLibrary,
                title = "Nothing to show",
                body = "The library is empty.",
            )
        }
        Spacer(Modifier.size(Spacing.md))
        GhostButton(text = "Show everything", onClick = onShowAll)
    }
}
