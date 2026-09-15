package com.dfc.mobile

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Deliberately tiny image loader: ~1/8 heap LRU + OkHttp GET with the API
 * token header. No disk cache (the server caches renditions itself), no
 * thread storm: at most 2 concurrent decodes via the dispatcher-limited
 * mutex below.
 */
object ThumbLoader {

    private val cache = object : LruCache<String, Bitmap>(calcCacheKb()) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount / 1024
    }

    private val mutex = kotlinx.coroutines.sync.Mutex()
    private var inFlight = 0

    private fun calcCacheKb(): Int {
        val maxKb = (Runtime.getRuntime().maxMemory() / 1024).toInt()
        return (maxKb / 8).coerceIn(4 * 1024, 32 * 1024)
    }

    suspend fun load(context: android.content.Context, fileId: String, preview: Boolean = false): Bitmap? = withContext(Dispatchers.IO) {
        val key = (if (preview) "p:" else "t:") + fileId
        cache.get(key)?.let { return@withContext it }

        // Throttle concurrent network decodes to 2.
        while (inFlight >= 2) kotlinx.coroutines.delay(50)
        inFlight++
        try {
            val api = DfcApi.get(context)
            val url = if (preview) api.previewUrl(fileId) else api.thumbUrl(fileId)
            val bytes = api.fetchBytes(url)
            val bmp = decode(bytes, preview) ?: return@withContext null
            cache.put(key, bmp)
            bmp
        } catch (e: Exception) {
            null
        } finally {
            inFlight--
        }
    }

    fun cached(fileId: String, preview: Boolean = false): Bitmap? =
        cache.get((if (preview) "p:" else "t:") + fileId)

    private fun decode(bytes: ByteArray, fullSize: Boolean): Bitmap? {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
        var sample = 1
        val target = if (fullSize) 1600 else 320
        if (opts.outWidth > target || opts.outHeight > target) {
            var w = opts.outWidth; var h = opts.outHeight
            while (w / 2 >= target || h / 2 >= target) { w /= 2; h /= 2; sample *= 2 }
        }
        val opts2 = BitmapFactory.Options().apply { inSampleSize = sample }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts2)
    }
}
