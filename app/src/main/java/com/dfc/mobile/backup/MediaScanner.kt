package com.dfc.mobile.backup

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import com.dfc.mobile.data.AppDb
import com.dfc.mobile.data.MediaItem

/** Scans MediaStore and reconciles the local Room index. */
object MediaScanner {

    data class ScanResult(val added: Int, val total: Int)

    /**
     * Pull every image+video the app can see and insert rows for items the
     * index has never heard of. Existing rows keep their upload state; items
     * whose size changed get reset to pending so edited photos re-upload.
     */
    suspend fun scan(context: Context): ScanResult {
        val db = AppDb.get(context)
        val prefs = com.dfc.mobile.Prefs.get(context)

        // MediaStore exposes a monotonically-increasing generation per volume;
        // when nothing changed since our last scan, skip the full walk entirely.
        // This is what makes app-open scans cheap on a phone with 50k photos.
        var generation: Long = -1
        try {
            val bundle = context.contentResolver.call(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "get_media_generation", null, null
            )
            generation = bundle?.getLong("generation", -1L) ?: -1L
        } catch (_: Exception) {}
        val lastGen = prefs.lastScanGeneration
        if (generation in 0..lastGen) return ScanResult(0, db.mediaDao().uploadedCount() + db.mediaDao().pendingCount())
        if (generation > 0) prefs.lastScanGeneration = generation

        var added = 0
        val now = System.currentTimeMillis() / 1000
        val batch = ArrayList<MediaItem>(256)

        // One pre-load of the index: a first scan on a 50k-photo library must
        // not issue one SELECT per row.
        val known = db.mediaDao().allItems().associateBy { it.id }

        val sources = listOf(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI to false,
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI to true,
        )
        for ((collection, isVideo) in sources) {
            val projection = arrayOf(
                MediaStore.MediaColumns._ID,
                MediaStore.MediaColumns.DISPLAY_NAME,
                MediaStore.MediaColumns.SIZE,
                MediaStore.MediaColumns.DATE_TAKEN,
                MediaStore.MediaColumns.DATE_MODIFIED,
            )
            context.contentResolver.query(collection, projection, null, null, null)?.use { cursor ->
                val colId = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val colName = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                val colSize = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
                val colDateTaken = cursor.getColumnIndex(MediaStore.MediaColumns.DATE_TAKEN)
                val colDateMod = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(colId)
                    val size = cursor.getLong(colSize)
                    if (size <= 0) continue
                    var dateTaken = if (colDateTaken >= 0) cursor.getLong(colDateTaken) else 0L
                    if (dateTaken <= 0) dateTaken = cursor.getLong(colDateMod)

                    val name = cursor.getString(colName) ?: "media_$id"
                    val existing = known[id]
                    val unchanged = existing != null && existing.size == size
                    val keepState = if (unchanged) existing else null
                    batch.add(
                        MediaItem(
                            id = id,
                            isVideo = isVideo,
                            size = size,
                            dateTaken = normalizeSeconds(dateTaken),
                            displayName = name,
                            remoteId = keepState?.remoteId,
                            state = keepState?.state ?: STATE_PENDING,
                            updatedAt = now,
                        )
                    )
                    if (batch.size >= 256) {
                        db.mediaDao().upsertAll(batch)
                        added += batch.size
                        batch.clear()
                    }
                }
            } ?: Log.w("MediaScanner", "MediaStore query returned null cursor for $collection")
        }

        if (batch.isNotEmpty()) {
            db.mediaDao().upsertAll(batch)
            added += batch.size
        }
        val total = db.mediaDao().uploadedCount() + db.mediaDao().pendingCount()
        return ScanResult(added, total)
    }

    /** DATE_TAKEN can arrive in ms on some devices; coerce to seconds. */
    private fun normalizeSeconds(v: Long): Long =
        if (v > 4_102_444_800L) v / 1000 else v  // > 2100-01-01 means milliseconds

    const val STATE_PENDING = 0
    const val STATE_UPLOADED = 1
    const val STATE_FAILED = 2
}
