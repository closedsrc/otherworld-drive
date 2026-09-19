package com.dfc.mobile

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.LruCache
import android.util.Size
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/**
 * Thumbnails for media that is still on the phone. [ThumbLoader] only knows how
 * to ask the server for a rendition, so before this existed nothing in the app
 * could draw a photo that had not finished uploading, and a library grid of the
 * device's own pictures was impossible.
 *
 * Decoding is local, so the concurrency cap is about memory rather than sockets:
 * four decodes at a time fills a grid without holding a dozen full bitmaps.
 */
object LocalThumbs {

    private const val THUMB_PX = 320
    private const val FULL_PX = 1600

    private val gate = Semaphore(permits = 4)

    private val cache = object : LruCache<String, Bitmap>(calcCacheKb()) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount / 1024
    }

    private fun calcCacheKb(): Int {
        val maxKb = (Runtime.getRuntime().maxMemory() / 1024).toInt()
        // A quarter of the heap: the remote loader holds an eighth and the two
        // caches are live at once on the Files screen.
        return (maxKb / 4).coerceIn(8 * 1024, 48 * 1024)
    }

    private fun key(id: Long, full: Boolean) = (if (full) "f:" else "t:") + id

    /** The content:// URI for a MediaStore row, which is what a player wants. */
    fun uri(id: Long, isVideo: Boolean): Uri {
        val collection = if (isVideo) MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        return ContentUris.withAppendedId(collection, id)
    }

    fun cached(id: Long, full: Boolean = false): Bitmap? = cache.get(key(id, full))

    /**
     * Grid-sized bitmap for one MediaStore row. Null when the item is gone or the
     * decode fails, which the caller draws as a glyph rather than a blank cell.
     */
    suspend fun thumb(context: Context, id: Long, isVideo: Boolean): Bitmap? =
        withContext(Dispatchers.IO) {
            val k = key(id, false)
            cache.get(k) ?: gate.withPermit {
                // Re-check inside the permit: a decode that queued behind this one
                // may already have filled the cache.
                cache.get(k) ?: decodeThumb(context, id, isVideo)?.also { cache.put(k, it) }
            }
        }

    /**
     * Screen-sized bitmap for the viewer. Videos are not decoded here: the viewer
     * hands a local video to the system player instead of showing a still.
     */
    suspend fun full(context: Context, id: Long): Bitmap? =
        withContext(Dispatchers.IO) {
            val k = key(id, true)
            cache.get(k) ?: gate.withPermit {
                cache.get(k) ?: decodeFull(context, id)?.also { cache.put(k, it) }
            }
        }

    private fun decodeThumb(context: Context, id: Long, isVideo: Boolean): Bitmap? = try {
        val uri = uri(id, isVideo)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            context.contentResolver.loadThumbnail(uri, Size(THUMB_PX, THUMB_PX), null)
        } else {
            // loadThumbnail arrived in API 29; the legacy helpers are the only
            // frame extractor on 26 to 28 and still work on newer releases.
            @Suppress("DEPRECATION")
            if (isVideo) MediaStore.Video.Thumbnails.getThumbnail(
                context.contentResolver, id, MediaStore.Video.Thumbnails.MINI_KIND, null
            ) else MediaStore.Images.Thumbnails.getThumbnail(
                context.contentResolver, id, MediaStore.Images.Thumbnails.MINI_KIND, null
            )
        }
    } catch (e: Exception) {
        null
    }

    /**
     * Sampled read of the original file. MediaStore's own thumbnail tops out
     * around 512px, which is visibly soft on a 1600px screen, so the viewer
     * decodes the file itself at a sample size that lands near [FULL_PX].
     */
    private fun decodeFull(context: Context, id: Long): Bitmap? = try {
        val uri = uri(id, false)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        }
        var sample = 1
        var w = bounds.outWidth
        var h = bounds.outHeight
        while (w / 2 >= FULL_PX || h / 2 >= FULL_PX) {
            w /= 2
            h /= 2
            sample *= 2
        }
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, opts)
        }
    } catch (e: Exception) {
        null
    }
}
