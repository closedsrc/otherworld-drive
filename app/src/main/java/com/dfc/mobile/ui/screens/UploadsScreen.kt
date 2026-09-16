package com.dfc.mobile.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dfc.mobile.backup.UploadTracker
import com.dfc.mobile.ui.formatBytes
import com.dfc.mobile.ui.formatDuration
import com.dfc.mobile.ui.relativeTime
import com.dfc.mobile.ui.DfcViewModel
import com.dfc.mobile.ui.DfcViewModel.Kind
import com.dfc.mobile.ui.ScreenTitle
import com.dfc.mobile.ui.components.GhostButton
import com.dfc.mobile.ui.components.NoUploadsForFile
import com.dfc.mobile.ui.components.NoUploadsYet
import com.dfc.mobile.ui.components.PrimaryButton
import com.dfc.mobile.ui.components.SectionHeader
import com.dfc.mobile.ui.components.Thumb
import com.dfc.mobile.ui.theme.Radii
import com.dfc.mobile.ui.theme.Spacing

/**
 * Uploads exists because a backup you cannot see is a backup you do not trust.
 * Everything here is measured: bytes move from the socket to the tracker, and
 * the waiting list is the local index's own pending rows.
 */
@Composable
fun UploadsScreen(
    ui: DfcViewModel.Ui,
    onBackupNow: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val current by UploadTracker.current.collectAsState()
    val completed by UploadTracker.completed.collectAsState()
    val running by UploadTracker.runActive.collectAsState()

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(bottom = Spacing.navClearance),
    ) {
        item(key = "title") {
            ScreenTitle(
                title = "Uploads",
                subtitle = when {
                    running -> "Backup running now"
                    ui.pending > 0 -> "${ui.pending} waiting in the queue"
                    else -> "Nothing waiting"
                },
            )
        }

        // Live transfer. Only rendered while something is actually moving.
        current?.let { job ->
            item(key = "current") {
                Spacer(Modifier.height(Spacing.xs))
                Box(Modifier.padding(horizontal = Spacing.md)) {
                    LiveCard(job = job)
                }
            }
        }

        if (ui.pending > 0) {
            item(key = "hdr-queued") {
                Spacer(Modifier.height(Spacing.lg))
                Box(Modifier.padding(horizontal = Spacing.md)) {
                    SectionHeader(
                        title = "Queued",
                        caption = "Sent in batches of ${com.dfc.mobile.backup.BackupWorker.MAX_PER_RUN} per run",
                    )
                }
            }
            item(key = "queued-note") {
                Box(Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm)) {
                    QueueCard(
                        pending = ui.pending,
                        running = running,
                        wifiOnly = ui.wifiOnly,
                        onBackupNow = onBackupNow,
                    )
                }
            }
        }

        if (completed.isNotEmpty()) {
            item(key = "hdr-done") {
                Spacer(Modifier.height(Spacing.lg))
                Box(Modifier.padding(horizontal = Spacing.md)) {
                    SectionHeader(
                        title = "Completed this session",
                        caption = "${completed.count { it.ok }} finished, " +
                            "${completed.count { !it.ok }} failed",
                    )
                }
                Spacer(Modifier.height(Spacing.xs))
            }
            items(completed, key = { "${it.at}-${it.displayName}" }) { done ->
                DoneRow(done = done)
            }
        }

        if (current == null && completed.isEmpty() && ui.pending == 0) {
            item(key = "empty") {
                Spacer(Modifier.height(Spacing.xl))
                if (ui.configured) NoUploadsYet() else NotConnectedForUploads(onOpenSettings)
            }
        }
    }
}

/** The file in flight: name, measured speed, estimate, and a live bar. */
@Composable
private fun LiveCard(job: UploadTracker.Job) {
    val shape = RoundedCornerShape(Radii.card)
    val fraction by animateFloatAsState(
        targetValue = job.fraction,
        animationSpec = tween(220),
        label = "progress",
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(Spacing.md),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp)) {
                Thumb(
                    fileId = "",
                    kind = if (job.isVideo) Kind.VIDEO else Kind.IMAGE,
                    name = job.displayName,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(Radii.control)),
                )
            }
            Spacer(Modifier.width(Spacing.md))
            Column(Modifier.weight(1f)) {
                Text(
                    text = job.displayName,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${formatBytes(job.sentBytes)} of ${formatBytes(job.totalBytes)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = "Sending",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        Spacer(Modifier.height(Spacing.md))
        LinearProgressIndicator(
            progress = { fraction },
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.outlineVariant,
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp)),
        )
        Spacer(Modifier.height(Spacing.sm))
        Row(Modifier.fillMaxWidth()) {
            Text(
                text = if (job.bytesPerSecond > 0)
                    "${formatBytes(job.bytesPerSecond)}/s" else "starting",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = if (job.remainingSeconds > 0)
                    "${formatDuration(job.remainingSeconds)} left" else "estimating",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(Spacing.sm))
        Text(
            text = "File ${job.indexInRun + 1} of ${job.runSize} in this run",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline,
        )
    }
}

/**
 * What is waiting. The exact file list lives in the local index and is sent in
 * a batch order the worker controls, so the count is reported rather than a
 * list the UI would have to keep in sync.
 */
@Composable
private fun QueueCard(
    pending: Int,
    running: Boolean,
    wifiOnly: Boolean,
    onBackupNow: () -> Unit,
) {
    val shape = RoundedCornerShape(Radii.card)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Outlined.Schedule,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(Spacing.md))
        Column(Modifier.weight(1f)) {
            Text(
                text = if (pending == 1) "1 item waiting" else "$pending items waiting",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = if (wifiOnly) "Runs on Wi-Fi when charging"
                else "Runs on any connection when charging",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        PrimaryButton(
            text = if (running) "Running" else "Start",
            onClick = onBackupNow,
            enabled = !running,
        )
    }
}

/** A finished transfer, or the item the upload format never supports. */
@Composable
private fun DoneRow(done: UploadTracker.Done) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (done.ok) Icons.Outlined.CheckCircle
            else Icons.Outlined.ErrorOutline,
            contentDescription = null,
            tint = if (done.ok) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.error,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(Spacing.md))
        Column(Modifier.weight(1f)) {
            Text(
                text = done.displayName,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${formatBytes(done.sizeBytes)}  ·  ${relativeTime(done.at / 1000)}" +
                    if (done.ok) "" else "  ·  failed, will retry",
                style = MaterialTheme.typography.labelSmall,
                color = if (done.ok) MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun NotConnectedForUploads(onOpenSettings: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.md),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        NoUploadsForFile()
        Spacer(Modifier.height(Spacing.md))
        GhostButton(text = "Open settings", onClick = onOpenSettings)
    }
}
