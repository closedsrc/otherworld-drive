package com.dfc.mobile

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

/**
 * Tiny image loader with two tiers: ~1/8 heap memory LRU in front of a
 * ~100 MB disk cache under the app cache dir, fetched over OkHttp with the
 * API token header. The disk tier is what makes grids fast on revisit: the
 * server caches renditions itself, but every cold app start used to refetch
 * every visible cell over the network.
 *
 * Concurrency is a semaphore rather than unbounded: a grid of 200 cells must
 * not open 200 sockets at once, but 2 was over-serializing — thumbnail
 * generation is I/O-bound on the fetch leg, so 6 in flight overlaps nicely
 * while the server still serializes the CPU-bound scaling itself.
 */
object ThumbLoader {

    /**
     * Permits, not a counter. The previous check-then-increment on a plain Int
     * was read and written from many IO coroutines at once, so the cap did not
     * actually hold and the wait was a 50ms spin rather than a suspension.
     */
    private val gate = Semaphore(permits = 6)

    private const val DISK_CAP_BYTES = 100L * 1024 * 1024

    private val cache = object : LruCache<String, Bitmap>(calcCacheKb()) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount / 1024
    }

    private fun calcCacheKb(): Int {
        val maxKb = (Runtime.getRuntime().maxMemory() / 1024).toInt()
        return (maxKb / 8).coerceIn(4 * 1024, 32 * 1024)
    }

    private fun key(fileId: String, preview: Boolean) = (if (preview) "p:" else "t:") + fileId

    suspend fun load(context: Context, fileId: String, preview: Boolean = false): Bitmap? =
        withContext(Dispatchers.IO) {
            val k = key(fileId, preview)
            cache.get(k) ?: gate.withPermit { fetchInto(context, k, fileId, preview) }
        }

    private suspend fun fetchInto(
        context: Context,
        cacheKey: String,
        fileId: String,
        preview: Boolean,
    ): Bitmap? {
        // A decode that started while this call queued on the gate may already
        // have filled the cache, so take a second look before refetching.
        cache.get(cacheKey)?.let { return it }
        // Disk before network: a revisit after process death must not refetch.
        diskGet(context, cacheKey)?.let { bmp ->
            cache.put(cacheKey, bmp)
            return bmp
        }
        return try {
            val api = DfcApi.get(context)
            val url = if (preview) api.previewUrl(fileId) else api.thumbUrl(fileId)
            val bytes = api.fetchBytes(url)
            diskPut(context, cacheKey, bytes)
            val bmp = decode(bytes, preview) ?: return null
            cache.put(cacheKey, bmp)
            bmp
        } catch (e: Exception) {
            null
        }
    }

    fun cached(fileId: String, preview: Boolean = false): Bitmap? =
        cache.get(key(fileId, preview))

    // ---- disk tier ---------------------------------------------------------

    private fun diskDir(context: Context): File =
        File(context.cacheDir, "dfc_thumbs").apply { mkdirs() }

    private fun diskFile(context: Context, cacheKey: String): File {
        val hex = MessageDigest.getInstance("SHA-256")
            .digest(cacheKey.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return File(diskDir(context), "$hex.jpg")
    }

    private fun diskGet(context: Context, cacheKey: String): Bitmap? {
        return try {
            val file = diskFile(context, cacheKey)
            if (!file.isFile) return null
            val bytes = file.readBytes()
            if (bytes.isEmpty()) return null
            file.setLastModified(System.currentTimeMillis())
            decode(bytes, cacheKey.startsWith("p:"))
        } catch (e: Exception) {
            null
        }
    }

    private fun diskPut(context: Context, cacheKey: String, bytes: ByteArray) {
        try {
            val file = diskFile(context, cacheKey)
            file.writeBytes(bytes)
            evictDisk(context)
        } catch (e: Exception) {
            // A full or unavailable cache dir must never break image loading.
        }
    }

    /** Drop oldest files until back under ~80% of the cap. Runs on IO threads. */
    private var lastEvictAt = 0L

    private fun evictDisk(context: Context) {
        val now = System.currentTimeMillis()
        // At most one scan per minute: directory walks on every thumbnail write
        // would cost more than the cache saves.
        if (now - lastEvictAt < 60_000) return
        lastEvictAt = now
        try {
            val dir = diskDir(context)
            val files = dir.listFiles() ?: return
            var total = files.sumOf { it.length() }
            if (total <= DISK_CAP_BYTES) return
            files.sortBy { it.lastModified() }
            for (file in files) {
                if (total <= DISK_CAP_BYTES * 8 / 10) break
                total -= file.length()
                file.delete()
            }
        } catch (e: Exception) {
        }
    }

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
