package com.dfc.mobile.ui.screens

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.ArrowForward
import androidx.compose.material.icons.outlined.BrokenImage
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dfc.mobile.LocalThumbs
import com.dfc.mobile.ThumbLoader
import com.dfc.mobile.VideoProxy
import com.dfc.mobile.data.MediaItem
import com.dfc.mobile.ui.BarIcon
import com.dfc.mobile.ui.formatBytes
import com.dfc.mobile.ui.fullDateLabel
import com.dfc.mobile.ui.theme.Radii
import com.dfc.mobile.ui.theme.Spacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.dfc.mobile.ui.theme.currentType

private const val STATE_UPLOADED = 1
private const val STATE_FAILED = 2
private const val STATE_REMOVED = 3

/**
 * Immersive preview for the local backup index. Photos get pinch and pan on a
 * solid black field; videos stream from the drive with the platform player, so
 * seeking and buffering behave the way the device already does.
 *
 * Every pane can render an item that has never been uploaded: a photo is decoded
 * from this phone's own MediaStore row, and a video opens from its content URI.
 * Before that, the viewer could only show what the server already held, so a
 * photo taken a minute ago opened onto an empty screen.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun GalleryPreview(
    items: List<MediaItem>,
    startIndex: Int,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    onSendAgain: ((MediaItem) -> Unit)? = null,
) {
    if (items.isEmpty()) return
    val pager = rememberPagerState(
        initialPage = startIndex.coerceIn(0, items.lastIndex),
        pageCount = { items.size },
    )

    Box(modifier.fillMaxSize().background(Color.Black)) {
        HorizontalPager(
            state = pager,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            val item = items[page]
            if (item.isVideo) {
                VideoPane(item = item)
            } else {
                ZoomableImage(item = item)
            }
        }

        // Top bar floats over the media, with its own scrim: white text straight
        // on a bright photo is unreadable, and only the close button used to
        // carry a backing.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.42f))
                .statusBarsPadding()
                .padding(horizontal = Spacing.sm, vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BarIcon(
                icon = Icons.Filled.Close,
                contentDescription = "Close the viewer",
                onClick = onClose,
            )
            Spacer(Modifier.width(Spacing.sm))
            Column(Modifier.weight(1f)) {
                Text(
                    text = items[pager.currentPage].displayName,
                    style = currentType.bodyMuted,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${formatBytes(items[pager.currentPage].size)}  ·  " +
                        "${pager.currentPage + 1} of ${items.size}",
                    style = currentType.meta,
                    color = Color.White.copy(alpha = 0.7f),
                )
            }
        }

        // Where and when this was taken, and whether the drive has it. The top bar
        // carries the file; this carries the library facts a photo app is asked
        // for.
        val current = items[pager.currentPage]
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.35f to Color.Black.copy(alpha = 0.55f),
                        1f to Color.Black.copy(alpha = 0.75f),
                    )
                )
                .navigationBarsPadding()
                .padding(horizontal = Spacing.md, vertical = Spacing.md),
        ) {
            Text(
                text = fullDateLabel(current.dateTaken),
                style = currentType.bodyMuted,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = buildString {
                    if (current.album.isNotBlank()) {
                        append(current.album)
                        append("  ·  ")
                    }
                    append(formatBytes(current.size))
                    if (current.isVideo && current.durationMs > 0L) {
                        append("  ·  ")
                        append(com.dfc.mobile.ui.clockDuration(current.durationMs))
                    }
                },
                style = currentType.meta,
                color = Color.White.copy(alpha = 0.7f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val status = when (current.state) {
                STATE_UPLOADED -> "On the drive"
                STATE_FAILED -> "The last upload failed. The next run will try again."
                STATE_REMOVED -> "Removed from the drive"
                else -> "Waiting to be backed up"
            }
            Text(
                text = status,
                style = currentType.meta,
                color = if (current.state == STATE_FAILED) MaterialTheme.colorScheme.error
                else Color.White.copy(alpha = 0.7f),
                maxLines = 2,
                modifier = Modifier.padding(top = Spacing.xs),
            )
            // A removed file is deliberately out of the queue, so putting it back
            // is an explicit action rather than something the next run decides.
            if (current.state == STATE_REMOVED && onSendAgain != null) {
                Box(
                    modifier = Modifier
                        .padding(top = Spacing.xs)
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(Radii.control))
                        .clickable { onSendAgain(current) }
                        .heightIn(min = 48.dp)
                        .padding(horizontal = Spacing.sm),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Send to the drive again",
                        style = currentType.action,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/**
 * Pinch to zoom, drag to pan, both clamped so the image cannot be lost. The
 * bitmap comes from the drive when the file is up there and from this phone's own
 * copy when it is not.
 */
@Composable
private fun ZoomableImage(item: MediaItem) {
    val context = LocalContext.current
    val key = "${item.id}:${item.remoteId ?: ""}"
    var bitmap by remember(key) {
        mutableStateOf(
            if (item.remoteId.isNullOrBlank()) LocalThumbs.cached(item.id, full = true)
            else ThumbLoader.cached(item.remoteId, preview = true)
        )
    }
    var failed by remember(key) { mutableStateOf(false) }
    var scale by remember(key) { mutableFloatStateOf(1f) }
    var offsetX by remember(key) { mutableFloatStateOf(0f) }
    var offsetY by remember(key) { mutableFloatStateOf(0f) }

    LaunchedEffect(key) {
        if (bitmap == null) {
            val loaded = withContext(Dispatchers.IO) {
                val remoteId = item.remoteId
                if (remoteId.isNullOrBlank()) LocalThumbs.full(context, item.id)
                else ThumbLoader.load(context, remoteId, preview = true)
            }
            bitmap = loaded
            // Storing null does not recompose, so a failed decode needs its own
            // flag: without it the spinner ran forever with no explanation.
            failed = loaded == null
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(key) {
                // Hand-rolled transform handling instead of
                // detectTransformGestures: that detector consumes single-finger
                // drags at 1x too, which starved the HorizontalPager — swiping
                // to the next photo did nothing unless you zoomed first. Here a
                // one-finger drag at 1x is left unconsumed so the pager pages;
                // two fingers (pinch) and any drag while zoomed are consumed
                // for zoom/pan.
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        val zoomChange = event.calculateZoom()
                        val panChange = event.calculatePan()
                        val zooming = event.changes.size > 1 || zoomChange != 1f
                        if (zooming || scale > 1f) {
                            event.changes.forEach { it.consume() }
                            scale = (scale * zoomChange).coerceIn(1f, 6f)
                            if (scale > 1f) {
                                val limitX = (size.width * (scale - 1)) / 2f
                                val limitY = (size.height * (scale - 1)) / 2f
                                offsetX = (offsetX + panChange.x).coerceIn(-limitX, limitX)
                                offsetY = (offsetY + panChange.y).coerceIn(-limitY, limitY)
                            } else {
                                offsetX = 0f
                                offsetY = 0f
                            }
                        }
                    } while (event.changes.any { it.pressed })
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        val current = bitmap
        when {
            current != null -> androidx.compose.foundation.Image(
                bitmap = current.asImageBitmap(),
                contentDescription = item.displayName,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offsetX,
                        translationY = offsetY,
                    ),
            )
            failed -> UnavailablePane(name = item.displayName)
            else -> CircularProgressIndicator(color = Color.White)
        }
    }
}

/** The image never arrived. Says so instead of spinning forever. */
@Composable
private fun UnavailablePane(name: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(horizontal = Spacing.xl),
    ) {
        Icon(
            imageVector = Icons.Outlined.BrokenImage,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.8f),
            modifier = Modifier.size(28.dp),
        )
        Spacer(Modifier.height(Spacing.sm))
        Text(
            text = "Nothing to show for this one",
            style = currentType.bodyMuted,
            color = Color.White,
        )
        Spacer(Modifier.height(Spacing.xs))
        Text(
            text = "Neither this phone nor the drive returned an image for $name. " +
                "If the file was deleted from the phone, the index still lists it.",
            style = currentType.meta,
            color = Color.White.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * Video plays through the platform player via an intent, which keeps seeking,
 * audio focus, and picture-in-picture behaviour consistent with the device
 * rather than reimplementing a player inside the app. The copy on this phone is
 * preferred, so a video plays the moment it is recorded.
 */
@Composable
private fun VideoPane(item: MediaItem) {
    val context = LocalContext.current
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Outlined.ArrowForward,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.8f),
                modifier = Modifier.size(28.dp),
            )
            Spacer(Modifier.height(Spacing.md))
            Text(
                text = item.displayName,
                style = currentType.bodyMuted,
                color = Color.White,
            )
            Spacer(Modifier.height(Spacing.xs))
            // Hand off to the system player and report what actually happened.
            // The pane used to claim "Playing in the system player" while a
            // failed launch was swallowed by runCatching.
            var opened by remember(item.id) { mutableStateOf<Pair<Boolean, Boolean>?>(null) }
            LaunchedEffect(item.id) {
                if (opened == null) opened = openVideo(context, item)
            }
            Text(
                text = when (val result = opened) {
                    null -> "Opening the system player"
                    else -> if (result.first) {
                        if (result.second) "Playing this phone's copy in the system player"
                        else "Streaming from the drive in the system player"
                    } else "No app on this device can play this video"
                },
                style = currentType.meta,
                color = Color.White.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(Spacing.lg))
        }
    }
}

/**
 * Hands a video to the system player. Returns whether one took it, and whether
 * the source was this phone rather than the drive.
 *
 * A remote video goes through the loopback proxy (see VideoProxy): an external
 * player cannot send the API token, so the raw https URL only ever came back
 * 401 and nothing played. The proxy attaches the token server-side and relays
 * Range requests, so seeking works like a direct stream.
 */
private fun openVideo(context: Context, item: MediaItem): Pair<Boolean, Boolean> {
    val remoteId = item.remoteId
    val local = remoteId.isNullOrBlank()
    val uri = if (local) LocalThumbs.uri(item.id, isVideo = true)
    else Uri.parse(VideoProxy.url(context, remoteId!!))
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "video/*")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    return runCatching { context.startActivity(intent) }.isSuccess to local
}
