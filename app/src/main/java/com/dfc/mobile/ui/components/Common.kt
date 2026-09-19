package com.dfc.mobile.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material.icons.outlined.FolderZip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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



/**
 * Right-pointing chevron for rows that lead somewhere. It uses the muted text
 * colour rather than `outline`: outline is a border token (about 1.1:1 against
 * the surface in light mode), which made the only affordance on the row
 * effectively invisible.
 */
@Composable
fun RowChevron(modifier: Modifier = Modifier) {
    Icon(
        imageVector = Icons.Filled.ChevronRight,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.size(18.dp),
    )
}

/**
 * The checkmark that marks a selected item. Shared by the drive list and the
 * library grid: two copies of it had already drifted apart once, and a selection
 * control is the last thing that should look different between screens.
 */
@Composable
fun SelectionDot(selected: Boolean, modifier: Modifier = Modifier) {
    val bg = if (selected) MaterialTheme.colorScheme.primary
    else Color.Black.copy(alpha = 0.45f)
    Box(
        modifier = modifier
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
 * One labelled action in a selection bar, shared by the drive list and the
 * library grid. The 44dp floor is not decoration: at text height these were
 * around 33dp and missed the tap more often than they hit it.
 */
@Composable
fun SelectionAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = MaterialTheme.colorScheme.onPrimaryContainer,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(Radii.control))
            .clickable(enabled = enabled, onClick = onClick)
            .heightIn(min = 48.dp)
            .padding(horizontal = Spacing.xs, vertical = Spacing.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (enabled) tint else tint.copy(alpha = 0.4f),
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (enabled) tint else tint.copy(alpha = 0.4f),
            maxLines = 1,
        )
    }
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
