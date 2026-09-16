package com.dfc.mobile.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.dfc.mobile.ThumbLoader
import com.dfc.mobile.ui.DfcViewModel.Kind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Server-rendered thumbnail with the file-type glyph as its placeholder. The
 * grid never shows a generic grey box: until the real rendition arrives the
 * cell states what the file is, which is more useful than a shimmer.
 */
@Composable
fun Thumb(
    fileId: String,
    kind: Kind,
    name: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var bitmap by remember(fileId) {
        mutableStateOf(if (fileId.isBlank()) null else ThumbLoader.cached(fileId))
    }

    LaunchedEffect(fileId) {
        // A blank id means there is no server record yet (e.g. the file
        // currently uploading): stay on the glyph instead of requesting
        // /api/thumb?file_id=, which the server can only reject.
        if (fileId.isBlank()) {
            bitmap = null
            return@LaunchedEffect
        }
        if (bitmap == null) {
            bitmap = withContext(Dispatchers.IO) { ThumbLoader.load(context, fileId) }
        }
    }

    val current = bitmap
    if (current != null) {
        Image(
            bitmap = current.asImageBitmap(),
            contentDescription = name,
            modifier = modifier,
            contentScale = ContentScale.Crop,
        )
    } else {
        Box(
            modifier = modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = kindIcon(kind, name),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}
