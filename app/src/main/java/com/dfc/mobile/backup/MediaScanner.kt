package com.dfc.mobile.backup

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.ContextCompat
import com.dfc.mobile.Prefs
import com.dfc.mobile.data.AppDb
import com.dfc.mobile.data.MediaItem

/** Scans MediaStore and reconciles the local Room index. */
object MediaScanner {

    data class ScanResult(val added: Int, val total: Int)

    private const val TAG = "MediaScanner"
    private const val BATCH = 256

    /** Bucket label for an item the phone reports no folder for. */
    private const val UNKNOWN_ALBUM = "Other"

    /** How often the index is reconciled against MediaStore even if nothing moved. */
    private const val FULL_WALK_INTERVAL_MS = 24 * 60 * 60 * 1000L

    /**
     * Pull every image+video the app can see and insert rows for items the
     * index has never heard of.
     *
     * Rows we already have at the same size are left untouched, because
     * re-inserting one would write an upload state captured before this walk over
     * one the worker set while the walk was running. The two exceptions are rows
     * missing an album or a video's runtime, which are filled in with a
     * single-column update that cannot disturb the upload state.
     */
    suspend fun scan(context: Context): ScanResult {
        val db = AppDb.get(context)
        val prefs = Prefs.get(context)
        val dao = db.mediaDao()

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

        // A library indexed before the album column existed has rows to fill in
        // even though MediaStore itself has not changed, so the generation
        // shortcut must not apply until that backfill has run once.
        val needsBackfill = dao.countWithoutAlbum() > 0
        // Once a day the walk runs regardless, because the generation counter
        // only reports changes MediaStore itself made; a photo deleted while the
        // app was closed leaves a row behind that nothing else would clean up.
        val dueForWalk = System.currentTimeMillis() - prefs.lastFullScanAt > FULL_WALK_INTERVAL_MS
        if (!needsBackfill && !dueForWalk && generation in 0..lastGen) {
            return ScanResult(0, dao.uploadedCount() + dao.pendingCount())
        }

        var added = 0
        var filled = 0
        val now = System.currentTimeMillis() / 1000
        val batch = ArrayList<MediaItem>(BATCH)

        // One pre-load of the index: a first scan on a 50k-photo library must
        // not issue one SELECT per row.
        val known = dao.allItems().associateBy { it.id }

        // MediaStore keeps a row after the file behind it is gone, so an index
        // built only by adding can hold entries for photos this phone no longer
        // has. Those rows are why the library once reported "1 item waiting to
        // back up" with no such photo on screen, and why one album cover could
        // not be decoded. Tracking what this walk actually saw lets the stale
        // rows be dropped.
        val seen = HashSet<Long>(known.size)
        val fullAccess = hasFullPhotoAccess(context)

        val sources = listOf(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI to false,
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI to true,
        )
        var walkedEverything = true
        for ((collection, isVideo) in sources) {
            val projection = arrayOf(
                MediaStore.MediaColumns._ID,
                MediaStore.MediaColumns.DISPLAY_NAME,
                MediaStore.MediaColumns.SIZE,
                MediaStore.MediaColumns.DATE_TAKEN,
                MediaStore.MediaColumns.DATE_MODIFIED,
                // Both constants inline to their column names at compile time, so
                // they resolve on every API level this app supports.
                MediaStore.MediaColumns.BUCKET_DISPLAY_NAME,
                MediaStore.MediaColumns.DURATION,
            )
            val cursor = context.contentResolver.query(collection, projection, null, null, null)
            if (cursor == null) {
                Log.w(TAG, "MediaStore query returned null cursor for $collection")
                walkedEverything = false
                continue
            }
            cursor.use { c ->
                val colId = c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val colName = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                val colSize = c.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
                val colDateTaken = c.getColumnIndex(MediaStore.MediaColumns.DATE_TAKEN)
                val colDateMod = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)
                val colBucket = c.getColumnIndex(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)
                val colRelative = c.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH)
                val colDuration = c.getColumnIndex(MediaStore.MediaColumns.DURATION)

                while (c.moveToNext()) {
                    val id = c.getLong(colId)
                    val size = c.getLong(colSize)
                    if (size <= 0) continue
                    seen.add(id)

                    val album = albumName(
                        bucket = if (colBucket >= 0) c.getString(colBucket) else null,
                        relativePath = if (colRelative >= 0) c.getString(colRelative) else null,
                    )
                    val durationMs = if (isVideo && colDuration >= 0) c.getLong(colDuration) else 0L

                    val prior = known[id]
                    if (prior != null && prior.size == size) {
                        // Already indexed. Only library metadata can be missing,
                        // and only that gets written.
                        //
                        // The album is refreshed when it differs, not just when
                        // blank: it names the folder this file is uploaded to, so
                        // a row carrying a wrong or older label would file the
                        // photo somewhere the phone does not show it.
                        val needsAlbum = prior.album != album
                        val needsDuration = isVideo && prior.durationMs <= 0L && durationMs > 0L
                        if (needsAlbum || needsDuration) {
                            dao.fillMetadata(
                                id = id,
                                album = if (needsAlbum) album else prior.album,
                                durationMs = if (needsDuration) durationMs else prior.durationMs,
                            )
                            filled++
                        }
                        continue
                    }

                    var dateTaken = if (colDateTaken >= 0) c.getLong(colDateTaken) else 0L
                    if (dateTaken <= 0) dateTaken = c.getLong(colDateMod)

                    batch.add(
                        MediaItem(
                            id = id,
                            isVideo = isVideo,
                            size = size,
                            dateTaken = normalizeSeconds(dateTaken),
                            displayName = c.getString(colName) ?: "media_$id",
                            remoteId = null,
                            state = STATE_PENDING,
                            updatedAt = now,
                            album = album,
                            durationMs = durationMs,
                        )
                    )
                    if (batch.size >= BATCH) {
                        dao.upsertAll(batch)
                        added += batch.size
                        batch.clear()
                    }
                }
            }
        }

        if (batch.isNotEmpty()) {
            dao.upsertAll(batch)
            added += batch.size
        }

        // Only now is the generation safe to record. Storing it before the walk
        // meant an exception or a killed process left everything past that point
        // permanently unindexed: the next scan saw a matching generation and
        // returned early, so those items were never picked up for upload.
        if (walkedEverything) {
            if (generation > 0) prefs.lastScanGeneration = generation
            prefs.lastFullScanAt = System.currentTimeMillis()
            // The walk saw every row that exists, so anything still unlabelled
            // belongs to media this phone no longer has. Without this the
            // backfill check would force a full walk on every future scan.
            if (needsBackfill) dao.backfillUnknownAlbum()

            // Only a walk that saw the whole library may delete anything, and
            // only with full photo access: on Android 14 a partial grant
            // ("Select photos") makes MediaStore report just the chosen subset,
            // and reconciling against that would erase the rest of the index.
            if (fullAccess) {
                val gone = known.keys.filter { it !in seen }
                if (gone.isNotEmpty()) {
                    gone.chunked(500).forEach { dao.deleteIds(it) }
                    Log.d(TAG, "dropped ${gone.size} rows for media this phone no longer has")
                }
            }
        }

        if (filled > 0) Log.d(TAG, "filled library metadata on $filled indexed rows")

        val total = dao.uploadedCount() + dao.pendingCount()
        return ScanResult(added, total)
    }

    /**
     * True when this app can see the whole library. A partial media grant on
     * Android 14 still answers MediaStore, but only with the items the user
     * picked, which is indistinguishable from "everything else was deleted".
     */
    private fun hasFullPhotoAccess(context: Context): Boolean = when {
        Build.VERSION.SDK_INT >= 33 ->
            granted(context, "android.permission.READ_MEDIA_IMAGES") ||
                granted(context, "android.permission.READ_MEDIA_VIDEO")
        else -> granted(context, android.Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    private fun granted(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) ==
            PackageManager.PERMISSION_GRANTED

    /** DATE_TAKEN can arrive in ms on some devices; coerce to seconds. */
    private fun normalizeSeconds(v: Long): Long =
        if (v > 4_102_444_800L) v / 1000 else v  // > 2100-01-01 means milliseconds

    /**
     * Which album an item belongs to. The bucket name is what a photo app shows,
     * but MediaStore leaves it null for anything not written by a camera or a
     * gallery app, so the folder the file sits in is the fallback. That folder is
     * the LAST segment of the relative path — "DCIM/Camera/" means the Camera
     * album, not DCIM. Items sitting at the volume root have neither, and only
     * those become "Other".
     */
    private fun albumName(bucket: String?, relativePath: String?): String {
        bucket?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        val folder = relativePath?.trim()?.trim('/')?.substringAfterLast('/').orEmpty()
        return folder.ifEmpty { UNKNOWN_ALBUM }
    }

    const val STATE_PENDING = 0
    const val STATE_UPLOADED = 1
    const val STATE_FAILED = 2

    /** Removed from the drive by the user; the queue leaves these alone. */
    const val STATE_REMOVED = 3
}
