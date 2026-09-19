package com.dfc.mobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dfc.mobile.data.MediaItem
import com.dfc.mobile.ui.BarIcon
import com.dfc.mobile.ui.DfcViewModel
import com.dfc.mobile.ui.ScreenTitle
import com.dfc.mobile.ui.components.AlbumCover
import com.dfc.mobile.ui.components.EmptyState
import com.dfc.mobile.ui.components.PhotoTimeline
import com.dfc.mobile.ui.dayLabel
import com.dfc.mobile.ui.groupByDays
import com.dfc.mobile.ui.itemCount
import com.dfc.mobile.ui.pressFeedback
import com.dfc.mobile.ui.theme.Radii
import com.dfc.mobile.ui.theme.Spacing
import com.dfc.mobile.ui.theme.currentType

/**
 * Albums are the folders the phone's own camera app filed photos into (Camera,
 * Screenshots, an app's download folder). They are read from MediaStore, so an
 * album here is the phone's grouping, not one the app invented.
 */
@Composable
fun AlbumsScreen(
    ui: DfcViewModel.Ui,
    onOpenAlbum: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val albums = ui.albums
    Column(modifier) {
        ScreenTitle(
            title = "Albums",
            subtitle = if (albums.isEmpty()) "Nothing indexed yet"
            else buildString {
                append(if (albums.size == 1) "1 album" else "${albums.size} albums")
                append("  ·  ")
                append(itemCount(ui.timeline.size))
            },
        )

        if (albums.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(bottom = Spacing.navClearance)) {
                EmptyState(
                    icon = Icons.Outlined.GridView,
                    title = "No albums yet",
                    body = if (ui.indexLoading) "Reading this phone's photo library."
                    else "This phone's library is empty, so there are no albums to show. " +
                        "Photos appear here after the first scan.",
                    modifier = Modifier.align(Alignment.Center),
                )
            }
            return
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(
                start = Spacing.md,
                end = Spacing.md,
                bottom = Spacing.navClearance,
            ),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(albums, key = { it.name }) { album ->
                AlbumCard(album = album, onClick = { onOpenAlbum(album.name) })
            }
        }
    }
}

/** One album tile: its cover, its name, and how much is in it. */
@Composable
private fun AlbumCard(album: DfcViewModel.Album, onClick: () -> Unit) {
    val shape = RoundedCornerShape(Radii.card)
    val interaction = remember { MutableInteractionSource() }
    Column(
        modifier = Modifier
            .pressFeedback(interaction)
            .clip(shape)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
    ) {
        AlbumCover(
            cover = album.cover,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
        )
        Spacer(Modifier.height(Spacing.sm))
        Text(
            text = album.name,
            style = currentType.body,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = buildString {
                append(itemCount(album.count))
                if (album.newest > 0L) {
                    append("  ·  ")
                    append(dayLabel(album.newest))
                }
            },
            style = currentType.meta,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * One album opened: the same date-grouped grid as the library, narrowed to this
 * album. Read-only, so there is no selection semantics and no long press.
 */
@Composable
fun AlbumDetailScreen(
    album: DfcViewModel.Album,
    items: List<MediaItem>,
    onBack: () -> Unit,
    onOpen: (MediaItem, List<MediaItem>) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = Spacing.sm, end = Spacing.sm, top = Spacing.md, bottom = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BarIcon(
                icon = Icons.Filled.ArrowBack,
                contentDescription = "Back to all albums",
                onClick = onBack,
            )
            Column(
                Modifier
                    .weight(1f)
                    .padding(start = Spacing.xs),
            ) {
                Text(
                    text = album.name,
                    style = currentType.title,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = buildString {
                        append(itemCount(items.size))
                        if (album.videos > 0) {
                            append("  ·  ")
                            append(if (album.videos == 1) "1 video" else "${album.videos} videos")
                        }
                    },
                    style = currentType.meta,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        PhotoTimeline(
            sections = groupByDays(items),
            selectedIds = emptySet(),
            selecting = false,
            onOpen = { item -> onOpen(item, items) },
            onToggle = null,
            bottomPadding = Spacing.navClearance,
        )
    }
}

/**
 * The album shelf that sits at the top of the library grid. It carries the two or
 * three albums the user actually opens and hands the rest to the Albums tab,
 * rather than repeating that whole screen inside this one.
 */
@Composable
fun AlbumStrip(
    albums: List<DfcViewModel.Album>,
    onOpenAlbum: (String) -> Unit,
    onSeeAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = Spacing.md),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        items(albums.take(8), key = { it.name }) { album ->
            Column(
                modifier = Modifier
                    .width(116.dp)
                    .clip(RoundedCornerShape(Radii.card))
                    .clickable { onOpenAlbum(album.name) },
            ) {
                AlbumCover(
                    cover = album.cover,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f),
                )
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    text = album.name,
                    style = currentType.meta,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = itemCount(album.count),
                    style = currentType.meta,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
        item(key = "see-all") {
            Column(
                modifier = Modifier
                    .width(116.dp)
                    .clip(RoundedCornerShape(Radii.card))
                    .clickable(onClick = onSeeAll),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(Radii.card))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.GridView,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp),
                    )
                }
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    text = "All albums",
                    style = currentType.meta,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                )
            }
        }
    }
}
