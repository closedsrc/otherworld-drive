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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
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
 * decode lands the cell shows a neutral surface — never a glyph that reads as
 * "broken".
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
        )
    }
}

/**
 * One square of the library. The photo is the cell; the only things drawn over
 * it are the two facts the grid cannot show by itself: how long a video runs,
 * and whether this item has reached the drive yet.
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
        targetValue = if (selected) 0.90f else 1f,
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
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.30f))
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
 * A day in the timeline. Just the date — the old header also carried "5 items ·
 * 1 video", which turned every section heading into a stats line and made the
 * grid read like a report. The count belongs to the screen's subtitle.
 */
@Composable
fun DateHeader(section: DaySection, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(start = Spacing.md, end = Spacing.md, top = Spacing.lg, bottom = Spacing.sm),
    ) {
        Text(
            text = section.label,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * The library grid: a scrolling column of day sections, each a sticky date
 * heading followed by rows of squares.
 *
 * The old version put the date headings inside a LazyVerticalGrid as ordinary
 * full-span rows, because that Compose version has no sticky-header support in
 * the grid. The consequence was that scrolling a long library left you with a
 * wall of unlabelled photographs and no idea where you were in time — the exact
 * thing a photo timeline exists to answer. A LazyColumn of section rows gives
 * real pinned headers.
 *
 * [columns] comes from the window class, so a tablet gets six across and a
 * phone three, rather than three 400dp tiles stretched over a landscape
 * display.
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
    columns: Int = 3,
    header: (@Composable () -> Unit)? = null,
) {
    LazyColumn(
        state = rememberLazyListState(),
        contentPadding = PaddingValues(bottom = bottomPadding),
        modifier = modifier.fillMaxSize(),
    ) {
        if (header != null) {
            item(key = "timeline-header", contentType = "header") { header() }
        }
        sections.forEach { section ->
            stickyHeader(key = "day-${section.dayStart}") {
                DateHeader(section)
            }
            val rows = section.items.chunked(columns)
            rows.forEachIndexed { rowIndex, rowItems ->
                item(key = "row-${section.dayStart}-$rowIndex", contentType = "row") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = GUTTER),
                        horizontalArrangement = Arrangement.spacedBy(GUTTER),
                    ) {
                        rowItems.forEach { item ->
                            PhotoCell(
                                item = item,
                                selected = selectedIds.contains(item.id),
                                selecting = selecting,
                                onClick = {
                                    if (selecting && onToggle != null) onToggle(item) else onOpen(item)
                                },
                                onLongClick = onToggle?.let { toggle -> { toggle(item) } },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        // Pad the final short row so its cells keep the same
                        // width as every other row instead of stretching.
                        repeat(columns - rowItems.size) {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

/**
 * Album cover: a four-way mosaic when the album has enough photos to fill it,
 * otherwise the single newest one.
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
