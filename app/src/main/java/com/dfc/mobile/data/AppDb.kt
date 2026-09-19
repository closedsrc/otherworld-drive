package com.dfc.mobile.data

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import android.content.Context

/**
 * One row per MediaStore item we know about. Primary key is the MediaStore
 * _ID so re-scans reconcile cheaply. state: 0 = pending, 1 = uploaded,
 * 2 = failed (retried on a later pass), 3 = removed from the drive by the user
 * (kept out of the queue until it is asked for again).
 *
 * [album] and [durationMs] exist for the library view: a photo timeline groups
 * by the day it was taken and an albums screen groups by the folder the camera
 * app filed it in, and a video cell needs its runtime. Both are read from
 * MediaStore, never guessed.
 */
@Entity(tableName = "media")
data class MediaItem(
    @PrimaryKey val id: Long,              // MediaStore _ID
    @ColumnInfo(name = "is_video") val isVideo: Boolean,
    val size: Long,
    @ColumnInfo(name = "date_taken") val dateTaken: Long, // epoch seconds, for folder bucketing
    val displayName: String,
    @ColumnInfo(name = "remote_id") val remoteId: String?,
    val state: Int,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    /** MediaStore bucket: Camera, Screenshots, WhatsApp. "Other" when the phone reports none. */
    @ColumnInfo(name = "album", defaultValue = "") val album: String = "",
    @ColumnInfo(name = "duration_ms", defaultValue = "0") val durationMs: Long = 0L,
)

@Dao
interface MediaDao {
    @Query("SELECT * FROM media")
    suspend fun allItems(): List<MediaItem>

    /**
     * Everything the library view can show, newest first. Fed to the timeline in
     * one read rather than one query per scroll position, because the server's
     * Postgres is far away and every round trip is visible.
     */
    @Query("SELECT * FROM media ORDER BY date_taken DESC, id DESC LIMIT :limit")
    suspend fun timeline(limit: Int): List<MediaItem>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<MediaItem>)

    /**
     * Drop rows for media this phone no longer has. Callers chunk the list: a
     * single IN with tens of thousands of ids exceeds SQLite's bound-variable
     * limit.
     */
    @Query("DELETE FROM media WHERE id IN (:ids)")
    suspend fun deleteIds(ids: List<Long>)

    @Query("UPDATE media SET state = :state, remote_id = :remoteId, updated_at = :at WHERE id = :id")
    suspend fun markUploaded(id: Long, remoteId: String, state: Int, at: Long)

    /**
     * Fill in library metadata on a row we already have, without touching its
     * upload state. Re-upserting the whole row to backfill these two fields
     * would overwrite an upload the worker recorded mid-walk.
     */
    @Query("UPDATE media SET album = :album, duration_ms = :durationMs WHERE id = :id")
    suspend fun fillMetadata(id: Long, album: String, durationMs: Long)

    /** Rows indexed before albums existed. Non-zero means a full walk is due. */
    @Query("SELECT COUNT(*) FROM media WHERE album = ''")
    suspend fun countWithoutAlbum(): Int

    /**
     * Give any row still missing an album a value, so [countWithoutAlbum] can
     * fall back to zero. Rows for media deleted from the phone are never seen by
     * a later walk, and without this the check would force a full re-walk on
     * every scan forever.
     */
    @Query("UPDATE media SET album = 'Other' WHERE album = ''")
    suspend fun backfillUnknownAlbum()

    /**
     * A file the user removed from the drive is no longer backed up, whatever the
     * index last recorded. Re-arming it as pending would just hand it back to the
     * next backup run, so it moves to [STATE_REMOVED] instead: the library view
     * stops claiming it is safe, and the engine stops trying to send it. The
     * viewer offers to send it again, which sets it back to 0.
     */
    @Query("UPDATE media SET state = :removed, remote_id = NULL WHERE remote_id IN (:remoteIds)")
    suspend fun markRemovedByRemoteIds(remoteIds: List<String>, removed: Int)

    /** Undo of [markRemovedByRemoteIds]: queue this item for the next run again. */
    @Query("UPDATE media SET state = 0 WHERE id = :id")
    suspend fun rearm(id: Long)

    /**
     * Oldest first, so a first import drains in capture order. Failed rows sort
     * last: otherwise 40 permanently failing items (an oversized video, a source
     * the user deleted) would occupy every batch forever and new photos would
     * never be reached.
     */
    @Query(
        "SELECT * FROM media WHERE state != 1 AND state != 3 " +
            "ORDER BY CASE WHEN state = 2 THEN 1 ELSE 0 END ASC, date_taken ASC LIMIT :limit"
    )
    suspend fun pending(limit: Int): List<MediaItem>

    /** Waiting work only. A removed file is not waiting for anything. */
    @Query("SELECT COUNT(*) FROM media WHERE state != 1 AND state != 3")
    suspend fun pendingCount(): Int

    @Query("SELECT COUNT(*) FROM media WHERE state = 1")
    suspend fun uploadedCount(): Int
}

@Database(entities = [MediaItem::class], version = 2, exportSchema = true)
abstract class AppDb : RoomDatabase() {
    abstract fun mediaDao(): MediaDao

    companion object {
        @Volatile private var instance: AppDb? = null

        /**
         * Adds the two library columns in place. Dropping the table instead would
         * erase every upload state, so the next run would re-verify every file
         * against the server one request at a time.
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE media ADD COLUMN album TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE media ADD COLUMN duration_ms INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun get(context: Context): AppDb =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext, AppDb::class.java, "dfc-mobile.db"
                )
                    // No destructive fallback, ever. This table is the only record
                    // of what this phone has already uploaded; wiping it on an
                    // unexpected schema jump means re-uploading the library and
                    // paying for it in bandwidth. A version bump without a
                    // migration must crash loudly in development, not silently
                    // forget everything in production.
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { instance = it }
            }
    }
}
