package com.dfc.mobile.ui.screens

import com.dfc.mobile.ui.components.PrimaryButton
import com.dfc.mobile.ui.components.GhostButton
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dfc.mobile.DfcApi
import com.dfc.mobile.RemoteFile
import com.dfc.mobile.backup.UploadTracker
import com.dfc.mobile.data.MediaItem
import com.dfc.mobile.ui.RiseIn
import com.dfc.mobile.ui.formatBytes
import com.dfc.mobile.ui.pressFeedback
import com.dfc.mobile.ui.relativeTime
import com.dfc.mobile.ui.DfcViewModel
import com.dfc.mobile.ui.ScreenTitle
import com.dfc.mobile.ui.components.SectionHeader
import com.dfc.mobile.ui.components.StorageRing
import com.dfc.mobile.ui.components.Thumb
import com.dfc.mobile.ui.theme.Radii
import com.dfc.mobile.ui.theme.Spacing
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue

/**
 * Home answers three questions in order: how much is stored, is it safe right
 * now, and what changed recently. The ring is the focal point; everything else
 * defers to it.
 */
@Composable
fun HomeScreen(
    ui: DfcViewModel.Ui,
    classify: (RemoteFile) -> DfcViewModel.Kind,
    onBackupNow: () -> Unit,
    onOpenUploads: () -> Unit,
    onOpenFiles: () -> Unit,
    onShare: () -> Unit,
    onCreateFolder: () -> Unit,
    onOpenPreview: (MediaItem, List<MediaItem>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val running by UploadTracker.runActive.collectAsState()
    val current by UploadTracker.current.collectAsState()
    val storage = ui.storage

    // The top blocks fade up in reading order, once per visit. The flag lives
    // here rather than inside RiseIn so a recycled item does not replay it.
    var revealed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { revealed = true }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(bottom = Spacing.navClearance),
    ) {
        item(key = "title") {
            RiseIn(visible = revealed, index = 0) {
                ScreenTitle(
                    title = greeting(),
                    subtitle = if (ui.configured) {
                        val backend = storage?.primaryBackend ?: "the server"
                        "Backed by $backend"
                    } else {
                        "Not connected yet"
                    },
                )
            }
        }

        item(key = "ring") {
            RiseIn(visible = revealed, index = 1) {
                StorageCard(
                    storage = storage,
                    pending = ui.pending,
                    backedUp = ui.backedUp,
                    lastBackupAt = ui.lastBackupAt,
                    running = running,
                    job = if (running) current else null,
                    onBackupNow = onBackupNow,
                )
            }
        }

        item(key = "actions") {
            RiseIn(visible = revealed, index = 2) {
                Spacer(Modifier.height(Spacing.lg))
                Box(Modifier.padding(horizontal = Spacing.md)) {
                    QuickActions(
                        onUpload = onBackupNow,
                        onCreateFolder = onCreateFolder,
                        onShare = onShare,
                    )
                }
            }
        }

        // Recent backups: only rendered when there is something to show, so the
        // first run gets a productive empty state instead of an empty shelf.
        if (ui.gallery.isNotEmpty()) {
            item(key = "hdr-recent") {
                Spacer(Modifier.height(Spacing.xl))
                Box(Modifier.padding(horizontal = Spacing.md)) {
                    SectionHeader(
                        title = "Recent backups",
                        caption = "${ui.backedUp} items on the drive",
                    )
                }
                Spacer(Modifier.height(Spacing.sm))
            }
            item(key = "recent-row") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = Spacing.md),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    items(ui.gallery.take(12), key = { it.id }) { item ->
                        RecentCard(item = item, onClick = { onOpenPreview(item, ui.gallery) })
                    }
                }
            }
        }

        if (ui.gallery.isEmpty() && ui.configured) {
            item(key = "empty") {
                Spacer(Modifier.height(Spacing.xl))
                Box(Modifier.padding(horizontal = Spacing.md)) {
                    EmptyHomeCard(ui = ui, onBackupNow = onBackupNow, onOpenUploads = onOpenUploads)
                }
            }
        }

        if (!ui.configured) {
            item(key = "unconfigured") {
                Spacer(Modifier.height(Spacing.xl))
                Box(Modifier.padding(horizontal = Spacing.md)) {
                    NotConnectedCard(onOpenSettings = onOpenFiles)
                }
            }
        }
    }
}

/**
 * The storage figure plus the live state of the drive. The centre figure is the
 * sum of what the drive holds; the third stat names the storage model instead of
 * the host disk, because the drive is backed by Telegram and has no quota.
 */
@Composable
private fun StorageCard(
    storage: DfcApi.Stats?,
    pending: Int,
    backedUp: Int,
    lastBackupAt: Long,
    running: Boolean,
    job: UploadTracker.Job?,
    onBackupNow: () -> Unit,
) {
    val shape = RoundedCornerShape(Radii.card)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.md)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(Spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val totalBytes = storage?.totalBytes ?: 0L
        val backend = storage?.primaryBackend ?: "the server"

        StorageRing(
            progress = job?.fraction,
            centerValue = if (storage == null) "..." else formatBytes(totalBytes),
            centerLabel = if (job != null) "sending" else "stored",
        )

        Spacer(Modifier.height(Spacing.lg))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Stat(
                value = (storage?.filesCount ?: 0).toString(),
                label = "files",
                modifier = Modifier.weight(1f),
            )
            StatDivider()
            Stat(
                value = backedUp.toString(),
                label = "phone",
                modifier = Modifier.weight(1f),
            )
            StatDivider()
            Stat(
                value = "Unlimited",
                label = backend,
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(Modifier.height(Spacing.lg))

        SyncLine(
            running = running,
            pending = pending,
            job = job,
            lastBackupAt = lastBackupAt,
            onBackupNow = onBackupNow,
        )
    }
}

/** Equal columns, so the three figures share one baseline and one rhythm. */
@Composable
private fun RowScope.Stat(value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 2,
        )
    }
}

@Composable
private fun StatDivider() {
    Box(
        Modifier
            .padding(horizontal = Spacing.sm)
            .width(1.dp)
            .height(28.dp)
            .background(MaterialTheme.colorScheme.outlineVariant)
    )
}

/**
 * Sync status. While a transfer is live it names the file and the measured rate;
 * otherwise it reports what is waiting and when the last run finished.
 */
@Composable
private fun SyncLine(
    running: Boolean,
    pending: Int,
    job: UploadTracker.Job?,
    lastBackupAt: Long,
    onBackupNow: () -> Unit,
) {
    val shape = RoundedCornerShape(Radii.control)
    val interaction = remember { MutableInteractionSource() }
    val (icon, tint, text) = when {
        job != null -> Triple(
            Icons.Outlined.Sync,
            MaterialTheme.colorScheme.primary,
            buildString {
                append("Sending ${job.displayName}")
                if (job.bytesPerSecond > 0L) append(" · ${formatBytes(job.bytesPerSecond)}/s")
            },
        )
        running -> Triple(
            Icons.Outlined.Sync,
            MaterialTheme.colorScheme.primary,
            "Checking for new photos",
        )
        pending > 0 -> Triple(
            Icons.Outlined.CloudOff,
            MaterialTheme.colorScheme.onSurfaceVariant,
            "$pending waiting to upload",
        )
        lastBackupAt > 0L -> Triple(
            Icons.Outlined.CloudDone,
            MaterialTheme.colorScheme.primary,
            "Everything is backed up, last run ${relativeTime(lastBackupAt / 1000)}",
        )
        else -> Triple(
            Icons.Outlined.CloudOff,
            MaterialTheme.colorScheme.onSurfaceVariant,
            "No backup has run yet",
        )
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pressFeedback(interaction)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(interactionSource = interaction, indication = null, onClick = onBackupNow)
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(Spacing.sm))
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = when {
                job != null -> "${(job.fraction * 100).toInt()}%"
                running -> "Running"
                else -> "Back up"
            },
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

/** Horizontal card for the recent rail: thumbnail over its capture date. */
@Composable
private fun RecentCard(item: MediaItem, onClick: () -> Unit) {
    val shape = RoundedCornerShape(Radii.card)
    val interaction = remember { MutableInteractionSource() }
    Column(
        modifier = Modifier
            .width(132.dp)
            .pressFeedback(interaction)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(Spacing.xs),
    ) {
        Thumb(
            fileId = item.remoteId ?: "",
            kind = if (item.isVideo) DfcViewModel.Kind.VIDEO else DfcViewModel.Kind.IMAGE,
            name = item.displayName,
            modifier = Modifier
                .fillMaxWidth()
                .height(96.dp)
                .clip(RoundedCornerShape(Radii.control)),
        )
        Spacer(Modifier.height(Spacing.sm))
        Text(
            text = item.displayName,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = Spacing.xs),
        )
        Text(
            text = formatBytes(item.size),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = Spacing.xs, vertical = Spacing.xs),
        )
    }
}

/** First-run guidance: says what will happen and how to start it. */
@Composable
private fun EmptyHomeCard(
    ui: DfcViewModel.Ui,
    onBackupNow: () -> Unit,
    onOpenUploads: () -> Unit,
) {
    val shape = RoundedCornerShape(Radii.card)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(Spacing.lg),
    ) {
        Text(
            text = if (ui.pending > 0) "Ready to send ${ui.pending} items" else "Ready for the first backup",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(Spacing.xs))
        Text(
            text = if (ui.pending > 0) {
                "Backups run every 15 minutes on Wi-Fi. Start one now to send them immediately."
            } else {
                "Allow photo access and the next run will index this phone and upload what it finds."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Spacing.md))
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            PrimaryButton(text = "Back up now", onClick = onBackupNow)
            GhostButton(text = "View uploads", onClick = onOpenUploads)
        }
    }
}

@Composable
private fun NotConnectedCard(onOpenSettings: () -> Unit) {
    val shape = RoundedCornerShape(Radii.card)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(Spacing.lg),
    ) {
        Text(
            text = "No server connected",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(Spacing.xs))
        Text(
            text = "Add the server address and a write token in Settings, then backups can start.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Spacing.md))
        PrimaryButton(text = "Open settings", onClick = onOpenSettings)
    }
}

/** Time-of-day greeting. Real clock, no invented personalisation. */
private fun greeting(): String {
    val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
    return when {
        hour < 5 -> "Working late"
        hour < 12 -> "Good morning"
        hour < 18 -> "Good afternoon"
        else -> "Good evening"
    }
}
