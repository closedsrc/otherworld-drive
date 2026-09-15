package com.dfc.mobile.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material.icons.outlined.FolderZip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.dfc.mobile.ui.DfcViewModel.Kind
import com.dfc.mobile.ui.theme.Radii
import com.dfc.mobile.ui.theme.Spacing

/**
 * File-type glyphs. Icons are chosen for what they say about the file, not for
 * a library's look: a film strip for video, a page for documents, and so on.
 */
fun kindIcon(kind: Kind, name: String): ImageVector = when (kind) {
    Kind.FOLDER -> Icons.Outlined.Folder
    Kind.IMAGE -> Icons.Outlined.Image
    Kind.VIDEO -> Icons.Outlined.Movie
    Kind.ARCHIVE -> Icons.Outlined.FolderZip
    Kind.DOCUMENT -> if (name.substringAfterLast('.', "").lowercase() == "pdf")
        Icons.Outlined.Description else Icons.Outlined.Description
    Kind.OTHER -> Icons.Outlined.InsertDriveFile
}

/**
 * A section title with an optional trailing slot. The small caption above is
 * used only where a group needs a reason to exist ("Why this is empty").
 */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    caption: String? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (caption != null) {
                Text(
                    text = caption,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        trailing?.invoke()
    }
}

/**
 * Empty state: says what is missing, why, and the next action. A bare "no data"
 * line would leave the user with no idea whether the app is working.
 */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    action: @Composable (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.xl, vertical = Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .background(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = RoundedCornerShape(Radii.card),
                )
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant,
                    shape = RoundedCornerShape(Radii.card),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )
        }
        Spacer(Modifier.height(Spacing.md))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(Spacing.xs))
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (action != null) {
            Spacer(Modifier.height(Spacing.md))
            action()
        }
    }
}

/** The three empty states the app can actually reach, named for reuse. */
@Composable
fun NoFilesYet(modifier: Modifier = Modifier, action: @Composable (() -> Unit)? = null) =
    EmptyState(
        icon = Icons.Outlined.FolderOpen,
        title = "This folder is empty",
        body = "Nothing has been stored here yet. Uploads land in Mobile Backup, grouped by the day they were taken.",
        modifier = modifier,
        action = action,
    )

@Composable
fun NoUploadsYet(modifier: Modifier = Modifier) =
    EmptyState(
        icon = Icons.Outlined.CloudOff,
        title = "No uploads yet",
        body = "Backups run every 15 minutes on Wi-Fi. Start one now and progress shows up here as it moves.",
        modifier = modifier,
    )

@Composable
fun NoResults(query: String, modifier: Modifier = Modifier) =
    EmptyState(
        icon = Icons.Outlined.SearchOff,
        title = "Nothing matches \"$query\"",
        body = "Try a shorter word, or clear the filters to search every file type.",
        modifier = modifier,
    )

@Composable
fun NoVideosFound(modifier: Modifier = Modifier) =
    EmptyState(
        icon = Icons.Outlined.Videocam,
        title = "No videos here",
        body = "This folder holds no video files.",
        modifier = modifier,
    )

@Composable
fun NoUploadsForFile(modifier: Modifier = Modifier) =
    EmptyState(
        icon = Icons.Outlined.UploadFile,
        title = "Nothing waiting",
        body = "Every file on this device is already on the drive.",
        modifier = modifier,
    )

/** Two-line metadata row used by list rows: size, then separator, then time. */
@Composable
fun MetaLine(sizeText: String, timeText: String, modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = sizeText,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = "  ·  ",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline,
        )
        Text(
            text = timeText,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Right-pointing chevron for rows that lead somewhere. */
@Composable
fun RowChevron(modifier: Modifier = Modifier) {
    Icon(
        imageVector = Icons.Filled.ChevronRight,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.outline,
        modifier = modifier.size(18.dp),
    )
}

/** Horizontal hairline used to separate rows without boxing each one. */
@Composable
fun Divider(modifier: Modifier = Modifier, inset: Int = 0) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = inset.dp)
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outlineVariant),
    )
}

/** Even vertical rhythm between stacked sections. */
@Composable
fun SectionGap(height: androidx.compose.ui.unit.Dp = Spacing.lg) {
    Spacer(Modifier.height(height))
}

/**
 * Failure state for a folder or listing that could not be loaded. It names what
 * failed and offers the one action that can fix it, instead of a bare error
 * string the user cannot act on.
 */
@Composable
fun ErrorCard(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radii.card))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(Spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Could not load this folder",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(Spacing.xs))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(Spacing.md))
        PrimaryButton(text = "Try again", onClick = onRetry)
    }
}
