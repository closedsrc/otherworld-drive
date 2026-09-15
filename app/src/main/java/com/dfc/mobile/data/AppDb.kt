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
import android.content.Context

/**
 * One row per MediaStore item we know about. Primary key is the MediaStore
 * _ID so re-scans reconcile cheaply. state: 0 = pending, 1 = uploaded,
 * 2 = failed (retried on a later pass).
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
)

@Dao
interface MediaDao {
    @Query("SELECT * FROM media WHERE id = :id LIMIT 1")
    suspend fun byId(id: Long): MediaItem?

    @Query("SELECT * FROM media")
    suspend fun allItems(): List<MediaItem>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<MediaItem>)

    @Query("UPDATE media SET state = :state, remote_id = :remoteId, updated_at = :at WHERE id = :id")
    suspend fun markUploaded(id: Long, remoteId: String, state: Int, at: Long)

    @Query("SELECT * FROM media WHERE state != 1 ORDER BY date_taken ASC LIMIT :limit")
    suspend fun pending(limit: Int): List<MediaItem>

    @Query("SELECT COUNT(*) FROM media WHERE state != 1")
    suspend fun pendingCount(): Int

    @Query("SELECT COUNT(*) FROM media WHERE state = 1")
    suspend fun uploadedCount(): Int

    @Query("SELECT * FROM media WHERE state = 1 ORDER BY date_taken DESC LIMIT :limit")
    suspend fun uploaded(limit: Int): List<MediaItem>

    @Query("DELETE FROM media WHERE id IN (SELECT id FROM media)")
    suspend fun clearAll()
}

@Database(entities = [MediaItem::class], version = 1, exportSchema = false)
abstract class AppDb : RoomDatabase() {
    abstract fun mediaDao(): MediaDao

    companion object {
        @Volatile private var instance: AppDb? = null

        fun get(context: Context): AppDb =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext, AppDb::class.java, "dfc-mobile.db"
                ).fallbackToDestructiveMigration().build().also { instance = it }
            }
    }
}
