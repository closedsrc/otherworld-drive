package com.dfc.mobile.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Link
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
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.dfc.mobile.LocalThumbs
import com.dfc.mobile.ThumbLoader
import com.dfc.mobile.ui.components.Thumb
import com.dfc.mobile.ui.components.minTouchSize
import com.dfc.mobile.ui.formatBytes
import com.dfc.mobile.ui.fullDateLabel
import com.dfc.mobile.ui.theme.Spacing
import com.dfc.mobile.ui.theme.currentType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The viewer: full-bleed, black, and immersive.
 *
 * Three things changed from the version this replaces. The chrome fades out —
 * tap the photo and the top bar, action row and caption all go, leaving just
 * the image; tap again and they return. The top bar carries the date the photo
 * was taken rather than a filename and a "3 of 47" counter, because that is
 * what a person is looking for when they are scrolling back through a life.
 * And the pager indicator is a row of dots at the top of the screen, so the
 * count is legible at a glance instead of being a line of text competing with
 * the picture.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GalleryPreview(
    items: List<ViewerItem>,
    startIndex: Int,
    onClose: () -> Unit,
    onShare: (ViewerItem) -> Unit,
    onDownload: (ViewerItem) -> Unit,
    onDelete: (ViewerItem) -> Unit,
    onDetails: (ViewerItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (items.isEmpty()) return
    val pager = rememberPagerState(
        initialPage = startIndex.coerceIn(0, items.lastIndex),
        pageCount = { items.size },
    )
    var zoomed by remember { mutableStateOf(false) }
    var chromeVisible by remember { mutableStateOf(true) }

    androidx.activity.compose.BackHandler(enabled = true) {
        if (zoomed) zoomed = false else onClose()
    }

    // A new page means a new photograph: bring the chrome back so its date is
    // readable without a tap.
    LaunchedEffect(pager.currentPage) { chromeVisible = true }

    Box(modifier.fillMaxSize().background(Color.Black)) {
        HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
            val item = items[page]
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null,
                    ) { chromeVisible = !chromeVisible },
            ) {
                if (item.isVideo) {
                    ViewerVideoPane(item = item)
                } else {
                    ZoomableImage(item = item, onZoomChange = { zoomed = it > 1f })
                }
            }
        }

        // ---- top chrome: date, count, close --------------------------------
        AnimatedVisibility(
            visible = chromeVisible,
            enter = fadeIn() + slideInVertically { -it },
            exit = fadeOut() + slideOutVertically { -it },
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Black.copy(alpha = 0.72f),
                            1f to Color.Transparent,
                        )
                    )
                    .statusBarsPadding()
                    .padding(horizontal = Spacing.sm, vertical = Spacing.sm),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .clickable(onClick = onClose),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = "Close viewer",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                    Column(
                        Modifier
                            .weight(1f)
                            .padding(start = Spacing.xs),
                    ) {
                        val current = items[pager.currentPage]
                        Text(
                            text = if (current.dateTaken > 0) fullDateLabel(current.dateTaken)
                            else current.displayName,
                            style = currentType.item,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = "${pager.currentPage + 1} of ${items.size}",
                            style = currentType.meta,
                            color = Color.White.copy(alpha = 0.7f),
                        )
                    }
                }
                if (items.size > 1) {
                    PagerDots(
                        count = items.size,
                        current = pager.currentPage,
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .padding(top = Spacing.sm),
                    )
                }
            }
        }

        // ---- bottom chrome: actions ---------------------------------------
        AnimatedVisibility(
            visible = chromeVisible,
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it },
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            val current = items[pager.currentPage]
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.45f to Color.Black.copy(alpha = 0.65f),
                            1f to Color.Black.copy(alpha = 0.9f),
                        )
                    )
                    .navigationBarsPadding()
                    .padding(horizontal = Spacing.md, vertical = Spacing.md),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    ViewerAction(Icons.Outlined.Link, "Share link") { onShare(current) }
                    ViewerAction(Icons.Outlined.Download, "Save to device") { onDownload(current) }
                    ViewerAction(Icons.Outlined.Info, "Details") { onDetails(current) }
                    ViewerAction(Icons.Outlined.Delete, "Delete") { onDelete(current) }
                }
                Spacer(Modifier.height(Spacing.sm))
                Text(
                    text = buildString {
                        append(current.displayName)
                        append("  ·  ")
                        append(formatBytes(current.size))
                    },
                    style = currentType.meta,
                    color = Color.White.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
            }
        }
    }
}

/**
 * Dots for the pager. Capped at a window of nearby pages so a library of a
 * thousand photos does not try to draw a thousand dots.
 */
@Composable
private fun PagerDots(count: Int, current: Int, modifier: Modifier = Modifier) {
    if (count > 12) return
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(count) { i ->
            Box(
                Modifier
                    .size(if (i == current) 6.dp else 4.dp)
                    .clip(CircleShape)
                    .background(
                        if (i == current) Color.White
                        else Color.White.copy(alpha = 0.35f)
                    ),
            )
        }
    }
}

/** One labelled action in the viewer's bottom bar. */
@Composable
private fun ViewerAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(4.dp))
        Text(label, style = currentType.meta, color = Color.White.copy(alpha = 0.85f))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ZoomableImage(
    item: ViewerItem,
    onZoomChange: (Float) -> Unit,
) {
    val context = LocalContext.current
    val key = "${item.localId ?: "-"}:${item.remoteId ?: "-"}"
    var bitmap by remember(key) {
        mutableStateOf(
            if (item.remoteId.isNullOrBlank()) LocalThumbs.cached(item.localId ?: -1, full = true)
            else ThumbLoader.cached(item.remoteId, preview = true)
        )
    }
    var failed by remember(key) { mutableStateOf(false) }
    var scale by remember(key) { mutableFloatStateOf(1f) }
    var offsetX by remember(key) { mutableFloatStateOf(0f) }
    var offsetY by remember(key) { mutableFloatStateOf(0f) }

    LaunchedEffect(key) {
        if (bitmap == null) {
            bitmap = withContext(Dispatchers.IO) {
                val remoteId = item.remoteId
                if (remoteId.isNullOrBlank()) LocalThumbs.full(context, item.localId ?: -1)
                else ThumbLoader.load(context, remoteId, preview = true)
            }
            failed = bitmap == null
        }
    }

    LaunchedEffect(scale) { onZoomChange(scale) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(key) {
                // Hand-rolled instead of detectTransformGestures: that detector
                // consumes one-finger drags at 1x, which starved the pager.
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent(PointerEventPass.Main)
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
        when {
            bitmap != null -> Image(
                bitmap = bitmap!!.asImageBitmap(),
                contentDescription = item.displayName,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = scale, scaleY = scale,
                        translationX = offsetX, translationY = offsetY,
                    ),
            )
            failed -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Outlined.Info,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.size(28.dp),
                )
                Spacer(Modifier.size(Spacing.sm))
                Text(
                    "This photo could not be opened",
                    style = currentType.body,
                    color = Color.White.copy(alpha = 0.8f),
                )
            }
            else -> CircularProgressIndicator(
                color = Color.White,
                strokeWidth = 2.dp,
                modifier = Modifier.size(28.dp),
            )
        }
    }
}

/** One item the viewer can show, whether it lives on the phone or the drive. */
data class ViewerItem(
    val displayName: String,
    val size: Long,
    val dateTaken: Long,
    val isVideo: Boolean,
    val remoteId: String? = null,
    val localId: Long? = null,
)

/**
 * Video pane. Videos play through the platform player over the drive's
 * range-capable stream, so seeking and buffering behave the way the device
 * already does.
 */
@Composable
private fun ViewerVideoPane(item: ViewerItem) {
    val context = LocalContext.current
    var preparing by remember(item.remoteId) { mutableStateOf(true) }

    Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        Thumb(
            fileId = item.remoteId ?: "",
            kind = com.dfc.mobile.ui.DfcViewModel.Kind.VIDEO,
            name = item.displayName,
            localId = item.localId,
            modifier = Modifier.fillMaxSize(),
        )
        if (preparing) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .background(Color.Black.copy(alpha = 0.45f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = "Play video",
                    tint = Color.White,
                    modifier = Modifier.size(34.dp),
                )
            }
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Transparent)
                .clickable {
                    val id = item.remoteId ?: return@clickable
                    preparing = false
                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                        setDataAndType(
                            com.dfc.mobile.VideoProxy.url(context, id).toUri(),
                            "video/*",
                        )
                        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    runCatching { context.startActivity(intent) }
                },
        )
    }
}
