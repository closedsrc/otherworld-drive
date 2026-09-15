package com.dfc.mobile.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ArrowForward
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dfc.mobile.DfcApi
import com.dfc.mobile.ThumbLoader
import com.dfc.mobile.data.MediaItem
import com.dfc.mobile.ui.formatBytes
import com.dfc.mobile.ui.BarIcon
import com.dfc.mobile.ui.theme.Radii
import com.dfc.mobile.ui.theme.Spacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Immersive preview for the local backup index. Photos get pinch and pan on a
 * solid black field; videos stream from the drive with the platform player, so
 * seeking and buffering behave the way the device already does.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun GalleryPreview(
    items: List<MediaItem>,
    startIndex: Int,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (items.isEmpty()) return
    val pager = rememberPagerState(
        initialPage = startIndex.coerceIn(0, items.lastIndex),
        pageCount = { items.size },
    )
    val context = LocalContext.current

    Box(modifier.fillMaxSize().background(Color.Black)) {
        HorizontalPager(
            state = pager,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            val item = items[page]
            if (item.isVideo) {
                VideoPane(fileId = item.remoteId ?: "", displayName = item.displayName)
            } else {
                ZoomableImage(fileId = item.remoteId ?: "", displayName = item.displayName)
            }
        }

        // Top bar floats over the media: title and the one action that applies.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = Spacing.sm, vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BarIcon(
                icon = Icons.Filled.Close,
                contentDescription = "Close the viewer",
                onClick = onClose,
                modifier = Modifier.background(
                    Color.Black.copy(alpha = 0.35f),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(Radii.control),
                ),
            )
            Spacer(Modifier.width(Spacing.sm))
            Column(Modifier.weight(1f)) {
                Text(
                    text = items[pager.currentPage].displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${formatBytes(items[pager.currentPage].size)}  ·  " +
                        "${pager.currentPage + 1} of ${items.size}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.7f),
                )
            }
        }
    }
}

/** Pinch to zoom, drag to pan, double the offset clamped so the image cannot be lost. */
@Composable
private fun ZoomableImage(fileId: String, displayName: String) {
    val context = LocalContext.current
    var bitmap by remember(fileId) { mutableStateOf<Bitmap?>(ThumbLoader.cached(fileId, preview = true)) }
    var scale by remember(fileId) { mutableFloatStateOf(1f) }
    var offsetX by remember(fileId) { mutableFloatStateOf(0f) }
    var offsetY by remember(fileId) { mutableFloatStateOf(0f) }

    LaunchedEffect(fileId) {
        if (bitmap == null) {
            bitmap = withContext(Dispatchers.IO) { ThumbLoader.load(context, fileId, preview = true) }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(fileId) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(1f, 6f)
                    // Panning is only meaningful while zoomed in; at 1x the image
                    // stays centred so it cannot slide off screen.
                    val limitX = (size.width * (scale - 1)) / 2f
                    val limitY = (size.height * (scale - 1)) / 2f
                    offsetX = (offsetX + pan.x).coerceIn(-limitX, limitX)
                    offsetY = (offsetY + pan.y).coerceIn(-limitY, limitY)
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        val current = bitmap
        if (current == null) {
            CircularProgressIndicator(color = Color.White)
        } else {
            androidx.compose.foundation.Image(
                bitmap = current.asImageBitmap(),
                contentDescription = displayName,
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
        }
    }
}

/**
 * Video plays through the platform player via an intent, which keeps seeking,
 * audio focus, and picture-in-picture behaviour consistent with the device
 * rather than reimplementing a player inside the app.
 */
@Composable
private fun VideoPane(fileId: String, displayName: String) {
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
                text = displayName,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White,
            )
            Spacer(Modifier.height(Spacing.xs))
            Text(
                text = "Playing in the system player",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.7f),
            )
            Spacer(Modifier.height(Spacing.lg))
            var launched by remember(fileId) { mutableStateOf(false) }
            LaunchedEffect(fileId) {
                if (!launched && fileId.isNotBlank()) {
                    launched = true
                    withContext(Dispatchers.IO) {
                        runCatching { openStream(context, fileId, displayName) }
                    }
                }
            }
        }
    }
}

/** Hands the authenticated stream URL to the system player. */
private fun openStream(context: android.content.Context, fileId: String, displayName: String) {
    val url = DfcApi.get(context).streamUrl(fileId)
    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
        setDataAndType(android.net.Uri.parse(url), "video/*")
        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    runCatching { context.startActivity(intent) }
}
