package com.dfc.mobile.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dfc.mobile.LocalThumbs
import com.dfc.mobile.ThumbLoader
import com.dfc.mobile.ui.components.minTouchSize
import com.dfc.mobile.ui.formatBytes
import com.dfc.mobile.ui.theme.Spacing
import com.dfc.mobile.ui.theme.currentType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.foundation.clickable

import com.dfc.mobile.ui.components.Thumb
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.net.toUri
import androidx.compose.foundation.Image

/**
 * The viewer, rebuilt.
 *
 * The old one was a trap: the zoom handler swallowed single-finger drags even at
 * 1x, so the pager never received them and "1 of 5" stayed "1 of 5" no matter
 * how hard you swiped. There were no actions at all — no share, no download, no
 * delete, no details — and a failed decode showed an eternal spinner.
 *
 * Now: gestures are handed off correctly (one finger at 1x pages, pinch or a
 * drag while zoomed transforms), the chrome carries the full action set, and
 * loading, error, and zoom states are all explicit and announced.
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
    val context = LocalContext.current
    var zoomed by remember { mutableStateOf(false) }

    // Back closes the viewer; it used to exit the whole app from here.
    androidx.activity.compose.BackHandler(enabled = true) {
        if (zoomed) zoomed = false else onClose()
    }

    Box(modifier.fillMaxSize().background(Color.Black)) {
        HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
            val item = items[page]
            if (item.isVideo) {
                ViewerVideoPane(item = item)
            } else {
                ZoomableImage(
                    item = item,
                    onZoomChange = { zoomed = it > 1f },
                )
            }
        }

        // Top chrome: identity and the count, over a scrim so white text is
        // readable on a bright photo.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.45f))
                .statusBarsPadding()
                .padding(horizontal = Spacing.sm, vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onClose, modifier = Modifier.minTouchSize()) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Close viewer", tint = Color.White)
            }
            Column(Modifier.weight(1f)) {
                Text(
                    text = items[pager.currentPage].displayName,
                    style = currentType.item,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${pager.currentPage + 1} of ${items.size}",
                    style = currentType.meta,
                    color = Color.White.copy(alpha = 0.75f),
                )
            }
        }

        // Bottom chrome: the actions that were missing entirely.
        val current = items[pager.currentPage]
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.4f to Color.Black.copy(alpha = 0.6f),
                        1f to Color.Black.copy(alpha = 0.85f),
                    )
                )
                .navigationBarsPadding()
                .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) {
                ViewerAction(Icons.Outlined.Link, "Share link") { onShare(current) }
                ViewerAction(Icons.Outlined.Download, "Save to device") { onDownload(current) }
                ViewerAction(Icons.Outlined.Info, "Details") { onDetails(current) }
                ViewerAction(Icons.Outlined.Delete, "Delete") { onDelete(current) }
            }
            Text(
                text = buildString {
                    append(formatBytes(current.size))
                    if (current.dateTaken > 0) {
                        append("  ·  ")
                        append(com.dfc.mobile.ui.fullDateLabel(current.dateTaken))
                    }
                },
                style = currentType.meta,
                color = Color.White.copy(alpha = 0.75f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
    }
}

@Composable
private fun ViewerAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.minTouchSize()) {
        Icon(icon, contentDescription = label, tint = Color.White)
    }
    Spacer(Modifier.width(Spacing.xs))
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
                    )
                    .padding(vertical = Spacing.lg),
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

/** Back handling lives with the viewer so it never exits the app. */
@Composable
private fun BackHandler(enabled: Boolean, onBack: () -> Unit) {
    androidx.activity.compose.BackHandler(enabled = enabled, onBack = onBack)
}

/**
 * Video pane. Videos play through the platform player over the drive's
 * range-capable stream, so seeking and buffering behave the way the device
 * already does — a Compose-rendered frame loop would fight the platform for no
 * gain. Until the user taps, a poster frame keeps the pane from being a black
 * rectangle.
 */
@Composable
private fun ViewerVideoPane(item: ViewerItem) {
    val context = LocalContext.current
    var preparing by remember(item.remoteId) { mutableStateOf(true) }

    DisposableEffect(item.remoteId) {
        onDispose { }
    }

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
                    .size(64.dp)
                    .background(Color.Black.copy(alpha = 0.45f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = "Play video",
                    tint = Color.White,
                    modifier = Modifier.size(30.dp),
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
