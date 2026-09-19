package com.dfc.mobile.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dfc.mobile.data.MediaItem
import com.dfc.mobile.ui.DfcViewModel
import com.dfc.mobile.ui.components.DfcCard
import com.dfc.mobile.ui.components.DfcPrimaryButton
import com.dfc.mobile.ui.components.EmptyState
import com.dfc.mobile.ui.components.Thumb
import com.dfc.mobile.ui.formatBytes
import com.dfc.mobile.ui.relativeTime
import com.dfc.mobile.ui.theme.Elevation
import com.dfc.mobile.ui.theme.Radii
import com.dfc.mobile.ui.theme.Spacing
import com.dfc.mobile.ui.theme.currentType
import com.dfc.mobile.ui.theme.windowSize

/**
 * Home answers four questions, in this order:
 *
 *  1. Is my backup working?
 *  2. Is my data safe?
 *  3. What needs attention?
 *  4. What changed recently?
 *
 * The old Home inverted this: four equal stat tiles ("4.8 GB / 697 files /
 * 5 phone / Unlimited") occupied the top, and the backup state — the only thing
 * a user of a backup app is actually asking about — was a thin caption. Storage
 * figures are now one tappable row near the bottom, and the hero is the state.
 */
@Composable
fun HomeScreen(
    ui: DfcViewModel.Ui,
    onBackupNow: () -> Unit,
    onOpenUploads: () -> Unit,
    onOpenFiles: () -> Unit,
    onOpenPhotos: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenItem: (MediaItem, List<MediaItem>) -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val w = windowSize
    val backedUp = ui.backedUp
    val pending = ui.pending
    val needsAttention = pending > 0 && ui.lastBackupAt > 0 &&
        (System.currentTimeMillis() / 1000 - ui.lastBackupAt) > 6 * 3600

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = if (w.isCompact) Spacing.md else Spacing.lg,
            end = if (w.isCompact) Spacing.md else Spacing.lg,
            top = Spacing.md,
            bottom = Spacing.navClearance,
        ),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        // 1 — the state, as the hero
        item(key = "status") {
            BackupStatusHero(
                configured = ui.configured,
                backedUp = backedUp,
                pending = pending,
                lastBackupAt = ui.lastBackupAt,
                error = ui.error ?: ui.storageError,
                onBackupNow = onBackupNow,
                onOpenSettings = onOpenSettings,
            )
        }

        // 2 — what needs attention
        if (ui.configured && (pending > 0 || needsAttention)) {
            item(key = "attention") {
                AttentionCard(
                    pending = pending,
                    stale = needsAttention,
                    onOpenUploads = onOpenUploads,
                    onBackupNow = onBackupNow,
                )
            }
        }

        // 3 — what changed recently
        item(key = "recentHeader") { SectionHeader("Recently backed up") }
        if (ui.timeline.isEmpty()) {
            item(key = "recentEmpty") {
                EmptyState(
                    icon = Icons.Filled.CloudQueue,
                    title = if (ui.indexLoading) "Reading your library…"
                    else "Nothing backed up yet",
                    body = if (ui.indexLoading) ""
                    else "Photos and videos you take will appear here once they are on your drive.",
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        } else {
            item(key = "recent") {
                val recent = ui.timeline.take(12)
                val columns = if (w.isCompact) 3 else 6
                // Chunked rows keep the grid inside a vertically scrolling page
                // without a nested scrollable measuring conflict.
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    recent.chunked(columns).forEachIndexed { rowIndex, row ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                        ) {
                            row.forEachIndexed { colIndex, item ->
                                Box(Modifier.weight(1f)) {
                                    RecentTile(
                                        item = item,
                                        list = recent,
                                        onClick = { onOpenItem(item, recent) },
                                    )
                                }
                            }
                            repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
        }

        // 4 — storage, demoted to a summary row
        item(key = "storage") {
            StorageSummary(
                filesCount = ui.storage?.filesCount,
                totalBytes = ui.storage?.totalBytes,
                error = ui.storageError,
                onOpenFiles = onOpenFiles,
                onOpenPhotos = onOpenPhotos,
            )
        }
    }
}

@Composable
private fun BackupStatusHero(
    configured: Boolean,
    backedUp: Int,
    pending: Int,
    lastBackupAt: Long,
    error: String?,
    onBackupNow: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val (icon, tint, title, body) = when {
        !configured -> Quad(
            Icons.Filled.CloudQueue,
            MaterialTheme.colorScheme.onSurfaceVariant,
            "Not connected yet",
            "Connect this phone to start backing up photos and videos.",
        )
        error != null -> Quad(
            Icons.Filled.ErrorOutline,
            MaterialTheme.colorScheme.error,
            "Can't reach your drive",
            error,
        )
        pending > 0 -> Quad(
            Icons.Filled.CloudQueue,
            MaterialTheme.colorScheme.primary,
            "Backing up",
            "$pending item${if (pending == 1) "" else "s"} waiting · $backedUp already on your drive",
        )
        else -> Quad(
            Icons.Filled.CloudDone,
            MaterialTheme.colorScheme.primary,
            "Everything is backed up",
            if (lastBackupAt > 0L) "Last checked ${relativeTime(lastBackupAt)} · $backedUp items safe"
            else "$backedUp items safe on your drive",
        )
    }

    DfcCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(Spacing.lg)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(tint.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = tint,
                        modifier = Modifier.size(22.dp),
                    )
                }
                Spacer(Modifier.width(Spacing.md))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = currentType.item,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = body,
                        style = currentType.meta,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (error != null) {
                Spacer(Modifier.height(Spacing.md))
                DfcPrimaryButton(
                    text = "Try again",
                    onClick = onOpenSettings,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else if (configured && pending > 0) {
                Spacer(Modifier.height(Spacing.md))
                DfcPrimaryButton(
                    text = "Back up now",
                    onClick = onBackupNow,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

private data class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)

@Composable
private fun AttentionCard(
    pending: Int,
    stale: Boolean,
    onOpenUploads: () -> Unit,
    onBackupNow: () -> Unit,
) {
    DfcCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.errorContainer,
    ) {
        Row(
            modifier = Modifier.padding(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.ErrorOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
            )
            Spacer(Modifier.width(Spacing.md))
            Text(
                text = if (stale) "Backup is overdue — $pending items still waiting"
                else "$pending item${if (pending == 1) "" else "s"} waiting to back up",
                style = currentType.body,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f),
            )
            androidx.compose.material3.TextButton(
                onClick = if (stale) onBackupNow else onOpenUploads,
                modifier = Modifier.minTouch(),
            ) { Text(if (stale) "Back up" else "Review") }
        }
    }
}

@Composable
private fun RecentTile(
    item: MediaItem,
    list: List<MediaItem>,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(Radii.tile),
        tonalElevation = Elevation.card,
        modifier = Modifier
            .aspectRatio(1f)
            .semantics { contentDescription = item.displayName },
    ) {
        Thumb(
            fileId = item.remoteId ?: "",
            kind = if (item.isVideo) com.dfc.mobile.ui.DfcViewModel.Kind.VIDEO
            else com.dfc.mobile.ui.DfcViewModel.Kind.IMAGE,
            name = item.displayName,
            localId = item.id,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun StorageSummary(
    filesCount: Int?,
    totalBytes: Long?,
    error: String?,
    onOpenFiles: () -> Unit,
    onOpenPhotos: () -> Unit,
) {
    DfcCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = "On your drive",
                    style = currentType.section,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = when {
                        error != null -> "Storage totals unavailable"
                        filesCount == null -> "Reading…"
                        else -> "${formatBytes(totalBytes ?: 0L)} across $filesCount files"
                    },
                    style = currentType.meta,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            androidx.compose.material3.TextButton(
                onClick = onOpenFiles,
                modifier = Modifier.minTouch(),
            ) { Text("Files") }
            androidx.compose.material3.TextButton(
                onClick = onOpenPhotos,
                modifier = Modifier.minTouch(),
            ) { Text("Photos") }
        }
    }
}

/** Section heading used inside scrolling pages. */
@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = currentType.section,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier.padding(top = Spacing.xs, bottom = Spacing.xs),
    )
}

/** 48dp minimum for a text action inside a row. */
private fun Modifier.minTouch(): Modifier =
    this.size(48.dp)
