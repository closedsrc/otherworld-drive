package com.dfc.mobile.ui.screens

import com.dfc.mobile.ui.components.PrimaryButton
import com.dfc.mobile.ui.components.GhostButton
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dfc.mobile.DfcApi
import com.dfc.mobile.RemoteFile
import com.dfc.mobile.Prefs
import com.dfc.mobile.backup.UploadTracker
import com.dfc.mobile.data.MediaItem
import com.dfc.mobile.ui.formatBytes
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

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(bottom = Spacing.navClearance),
    ) {
        item(key = "title") {
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

        item(key = "ring") {
            StorageCard(
                storage = storage,
                pending = ui.pending,
                backedUp = ui.backedUp,
                lastBackupAt = ui.lastBackupAt,
                running = running,
                currentName = current?.displayName,
                onBackupNow = onBackupNow,
            )
        }

        item(key = "actions") {
            Spacer(Modifier.height(Spacing.lg))
            Box(Modifier.padding(horizontal = Spacing.md)) {
                QuickActions(
                    onUpload = onBackupNow,
                    onCreateFolder = onCreateFolder,
                    onShare = onShare,
                )
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
 * The storage figure plus the live state of the drive. The ring shows the share
 * of the server's disk the drive occupies, which is the only ratio the backend
 * reports; there is no quota, so no "of X GB" claim is made.
 */
@Composable
private fun StorageCard(
    storage: DfcApi.Stats?,
    pending: Int,
    backedUp: Int,
    lastBackupAt: Long,
    running: Boolean,
    currentName: String?,
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
        val diskFree = storage?.diskFreeBytes ?: 0L
        val usedFraction = if (totalBytes + diskFree > 0) {
            totalBytes.toFloat() / (totalBytes + diskFree).toFloat()
        } else 0f

        StorageRing(
            usedFraction = usedFraction,
            centerValue = if (storage == null) "..." else formatBytes(totalBytes),
            centerLabel = "stored on the drive",
        )

        Spacer(Modifier.height(Spacing.lg))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            Stat(
                value = (storage?.filesCount ?: 0).toString(),
                label = "files",
            )
            Stat(
                value = backedUp.toString(),
                label = "from this phone",
            )
            Stat(
                value = if (diskFree > 0) formatBytes(diskFree) else "unknown",
                label = "server disk free",
            )
        }

        Spacer(Modifier.height(Spacing.lg))

        SyncLine(
            running = running,
            pending = pending,
            currentName = currentName,
            lastBackupAt = lastBackupAt,
            onBackupNow = onBackupNow,
        )
    }
}

@Composable
private fun Stat(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
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
            maxLines = 1,
        )
    }
}

/**
 * Sync status. While a transfer is live it names the file; otherwise it reports
 * what is waiting and when the last run finished.
 */
@Composable
private fun SyncLine(
    running: Boolean,
    pending: Int,
    currentName: String?,
    lastBackupAt: Long,
    onBackupNow: () -> Unit,
) {
    val shape = RoundedCornerShape(Radii.control)
    val (icon, tint, text) = when {
        running && currentName != null -> Triple(
            Icons.Outlined.Sync,
            MaterialTheme.colorScheme.primary,
            "Sending $currentName",
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
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onBackupNow)
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
            text = if (running) "Running" else "Back up",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

/** Horizontal card for the recent rail: thumbnail over its capture date. */
@Composable
private fun RecentCard(item: MediaItem, onClick: () -> Unit) {
    val shape = RoundedCornerShape(Radii.card)
    Column(
        modifier = Modifier
            .width(132.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(onClick = onClick)
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
