package com.dfc.mobile.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dfc.mobile.LocalThumbs
import com.dfc.mobile.data.MediaItem
import com.dfc.mobile.ui.DaySection
import com.dfc.mobile.ui.DfcViewModel.Kind
import com.dfc.mobile.ui.clockDuration
import com.dfc.mobile.ui.itemCount
import com.dfc.mobile.ui.theme.Radii
import com.dfc.mobile.ui.theme.Spacing

/** Two pixels of gutter: a photo grid reads as a continuous field, not a table. */
private val GUTTER = 2.dp

/** Cells where the item is neither on the drive nor in a state the app can fix. */
private const val STATE_UPLOADED = 1
private const val STATE_FAILED = 2
private const val STATE_REMOVED = 3

/**
 * Thumbnail for a photo or video that lives on this phone. Until the MediaStore
 * decode lands the cell shows the file-type glyph rather than an empty grey box,
 * which is the same rule the server-rendered [Thumb] follows.
 */
@Composable
fun MediaThumb(item: MediaItem, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var bitmap by remember(item.id) { mutableStateOf(LocalThumbs.cached(item.id)) }

    LaunchedEffect(item.id) {
        if (bitmap == null) bitmap = LocalThumbs.thumb(context, item.id, item.isVideo)
    }

    val current = bitmap
    if (current != null) {
        Image(
            bitmap = current.asImageBitmap(),
            contentDescription = item.displayName,
            modifier = modifier,
            contentScale = ContentScale.Crop,
        )
    } else {
        Box(
            modifier = modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = kindIcon(if (item.isVideo) Kind.VIDEO else Kind.IMAGE, item.displayName),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

/**
 * One square of the library. The photo is the cell; the only things drawn over it
 * are the two facts the grid cannot show by itself: how long a video runs, and
 * whether this item has reached the drive yet.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PhotoCell(
    item: MediaItem,
    selected: Boolean,
    selecting: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val scale by animateFloatAsState(
        targetValue = if (selected) 0.92f else 1f,
        animationSpec = tween(160),
        label = "photoCellScale",
    )
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .scale(scale)
            .clip(RoundedCornerShape(Radii.tile))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        MediaThumb(item = item, modifier = Modifier.fillMaxSize())

        if (item.isVideo && item.durationMs > 0L) {
            CellBadge(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(Spacing.xs),
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(11.dp),
                )
                Spacer(Modifier.width(2.dp))
                Text(
                    text = clockDuration(item.durationMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    maxLines = 1,
                )
            }
        }

        // Only items that still need the drive carry a mark. A library with
        // nothing left to send shows no marks at all, which is the point.
        if (item.state != STATE_UPLOADED) {
            val failed = item.state == STATE_FAILED
            val removed = item.state == STATE_REMOVED
            CellBadge(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(Spacing.xs),
            ) {
                Icon(
                    imageVector = when {
                        failed -> Icons.Outlined.ErrorOutline
                        removed -> Icons.Outlined.DeleteOutline
                        else -> Icons.Outlined.CloudOff
                    },
                    contentDescription = when {
                        failed -> "The last upload failed"
                        removed -> "Removed from the drive"
                        else -> "Not backed up yet"
                    },
                    tint = if (failed) MaterialTheme.colorScheme.error else Color.White,
                    modifier = Modifier.size(12.dp),
                )
            }
        }

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
    }
}

/**
 * The dark pill that carries an icon over a photo. Legibility over an arbitrary
 * image needs its own backing: white on a bright sky is unreadable without it.
 */
@Composable
private fun CellBadge(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(Radii.control))
            .background(Color.Black.copy(alpha = 0.45f))
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        content()
    }
}

/**
 * A day in the timeline: the date on the left, what it holds on the right. The
 * count is real, and the video tally only appears when there is one.
 */
@Composable
fun DateHeader(section: DaySection, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = Spacing.md,
                end = Spacing.md,
                top = Spacing.lg,
                bottom = Spacing.sm,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = section.label,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(Spacing.sm))
        Text(
            text = buildString {
                append(itemCount(section.items.size))
                val videos = section.videos
                if (videos > 0) {
                    append("  ·  ")
                    append(if (videos == 1) "1 video" else "$videos videos")
                }
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

/**
 * The library grid: one pass over the items, grouped under their capture date.
 *
 * Date headings are full-width rows inside the grid rather than sticky headers,
 * because the LazyVerticalGrid in this Compose version has no sticky support. A
 * heading that scrolls away with its photos is honest; a hand-rolled pinned copy
 * of one would be a second source of truth for the same thing.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PhotoTimeline(
    sections: List<DaySection>,
    selectedIds: Set<Long>,
    selecting: Boolean,
    onOpen: (MediaItem) -> Unit,
    onToggle: ((MediaItem) -> Unit)?,
    bottomPadding: Dp,
    modifier: Modifier = Modifier,
    header: (@Composable () -> Unit)? = null,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        contentPadding = PaddingValues(bottom = bottomPadding),
        horizontalArrangement = Arrangement.spacedBy(GUTTER),
        verticalArrangement = Arrangement.spacedBy(GUTTER),
        modifier = modifier.fillMaxSize(),
    ) {
        // A screen-level banner above the first date heading, inside the grid so
        // it scrolls away with the photos instead of holding a fixed band of the
        // screen forever.
        if (header != null) {
            item(key = "timeline-header", span = { GridItemSpan(maxLineSpan) }, contentType = "header") {
                header()
            }
        }
        sections.forEach { section ->
            item(
                key = "day-${section.dayStart}",
                span = { GridItemSpan(maxLineSpan) },
                contentType = "header",
            ) {
                DateHeader(section)
            }
            items(
                items = section.items,
                key = { it.id },
                contentType = { "cell" },
            ) { item ->
                PhotoCell(
                    item = item,
                    selected = selectedIds.contains(item.id),
                    selecting = selecting,
                    onClick = { if (selecting && onToggle != null) onToggle(item) else onOpen(item) },
                    // Null when the screen has no selection semantics at all, so a
                    // read-only timeline does not carry a long press that does
                    // nothing.
                    onLongClick = onToggle?.let { toggle -> { toggle(item) } },
                )
            }
        }
    }
}

/**
 * Album cover: a four-way mosaic when the album has enough photos to fill it,
 * otherwise the single newest one. Repeating one photo four times to complete the
 * grid would be a lie about how much is in the album.
 */
@Composable
fun AlbumCover(cover: List<MediaItem>, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(Radii.card)
    if (cover.size < 4) {
        Box(modifier.clip(shape)) {
            cover.firstOrNull()?.let {
                MediaThumb(item = it, modifier = Modifier.fillMaxSize())
            } ?: Box(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            )
        }
        return
    }
    Column(
        modifier = modifier.clip(shape),
        verticalArrangement = Arrangement.spacedBy(GUTTER),
    ) {
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(GUTTER)) {
            MediaThumb(cover[0], Modifier.weight(1f).fillMaxSize())
            MediaThumb(cover[1], Modifier.weight(1f).fillMaxSize())
        }
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(GUTTER)) {
            MediaThumb(cover[2], Modifier.weight(1f).fillMaxSize())
            MediaThumb(cover[3], Modifier.weight(1f).fillMaxSize())
        }
    }
}
