package com.dfc.mobile.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
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
import com.dfc.mobile.LocalThumbs
import com.dfc.mobile.ThumbLoader
import com.dfc.mobile.ui.DfcViewModel.Kind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * A thumbnail with a truthful placeholder.
 *
 * Two root causes made every photo surface in this app a wall of generic
 * document glyphs: the cell only ever tried the server rendition (so anything
 * not yet uploaded had nothing to show), and it fell back to a file-type icon
 * that users read as "broken". Now the cell resolves in order — cached, local
 * MediaStore decode, then server rendition — and while it resolves it shows a
 * neutral surface, never a glyph that means "error".
 *
 * [localId] is the MediaStore row; [fileId] is the server id. Either may be
 * absent, which is the normal case for a photo taken seconds ago.
 */
@Composable
fun Thumb(
    fileId: String,
    kind: Kind,
    name: String,
    modifier: Modifier = Modifier,
    localId: Long? = null,
) {
    val context = LocalContext.current
    val key = "${localId ?: "-"}:${fileId.ifBlank { "-" }}"
    var bitmap by remember(key) {
        mutableStateOf(
            when {
                fileId.isNotBlank() -> ThumbLoader.cached(fileId)
                localId != null -> LocalThumbs.cached(localId)
                else -> null
            }
        )
    }
    var failed by remember(key) { mutableStateOf(false) }

    LaunchedEffect(key) {
        if (bitmap != null) return@LaunchedEffect
        bitmap = withContext(Dispatchers.IO) {
            // Local first: a photo taken a minute ago has no server id yet, and
            // asking for /api/thumb on a blank id can only be rejected.
            if (fileId.isBlank()) {
                localId?.let { runCatching { LocalThumbs.thumb(context, it, kind == Kind.VIDEO) }.getOrNull() }
            } else {
                ThumbLoader.load(context, fileId)
                    ?: localId?.let { runCatching { LocalThumbs.thumb(context, it, kind == Kind.VIDEO) }.getOrNull() }
            }
        }
        failed = bitmap == null
    }

    val current = bitmap
    when {
        current != null -> Image(
            bitmap = current.asImageBitmap(),
            contentDescription = name,
            modifier = modifier,
            contentScale = ContentScale.Crop,
        )
        failed -> Box(
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
        else -> Box(
            modifier = modifier.background(MaterialTheme.colorScheme.surfaceContainer),
        )
    }
}
