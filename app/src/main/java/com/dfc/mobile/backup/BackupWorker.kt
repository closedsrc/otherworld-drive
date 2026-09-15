package com.dfc.mobile.backup

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ContentUris
import android.content.Context
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.dfc.mobile.DfcApi
import com.dfc.mobile.Prefs
import com.dfc.mobile.R
import com.dfc.mobile.data.AppDb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okio.BufferedSink
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

/**
 * The low-usage auto-backup engine. Runs at most every 15 minutes, only on
 * unmetered networks (by default), and uploads strictly sequentially — one
 * file at a time, small batches, then exits. No gallery-crawling loops, no
 * parallel connection storms.
 */
class BackupWorker(appContext: Context, params: WorkerParameters) :
    CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val prefs = Prefs.get(applicationContext)
        if (!prefs.isConfigured) return@withContext Result.success()

        val db = AppDb.get(applicationContext)
        val api = DfcApi.get(applicationContext)

        // 1. Reconcile the index (cheap diff; MediaStore does the walking).
        try {
            MediaScanner.scan(applicationContext)
        } catch (e: Exception) {
            Log.w(TAG, "scan failed", e)
        }

        // 2. Upload pending items sequentially, capped per run to stay light.
        val pending = db.mediaDao().pending(MAX_PER_RUN)
        if (pending.isEmpty()) return@withContext Result.success()

        try {
            setForeground(createForegroundInfo(0, pending.size))
        } catch (e: Exception) {
            // Foreground can be refused (e.g. notification permission missing);
            // the work still proceeds as a normal background worker.
            Log.i(TAG, "foreground not granted: ${e.message}")
        }

        val dateFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        val zone = ZoneId.systemDefault()
        var uploaded = 0
        var failed = 0

        for ((index, item) in pending.withIndex()) {
            if (isStopped) break
            try {
                setForeground(createForegroundInfo(index, pending.size))
            } catch (_: Exception) {}

            // Items without a capture date land in "undated", never 1970-01-01.
            val day = if (item.dateTaken > 0)
                Instant.ofEpochSecond(item.dateTaken).atZone(zone).toLocalDate()
            else null
            val folderPath = if (day != null)
                "Mobile Backup/${day.format(dateFmt)}"
            else "Mobile Backup/undated"
            val parentId = api.ensureFolderPath(folderPath)
            if (parentId.isEmpty()) {
                failed++; continue
            }

            val remoteName = api.uniqueRemoteName(item.displayName, item.id, item.size)

            // The server keys files by path, so an identical name+size already
            // in the folder means the bytes are there — skip instead of
            // re-uploading (covers reinstalls where the local index was lost).
            if (runCatching { api.alreadyBackedUp(parentId, remoteName, item.size) }.getOrDefault(false)) {
                // Resolve the real file id so thumbnails work; if the listing
                // lags, leave it pending for the next pass instead of storing
                // an empty remoteId that can never load.
                val recovered = api.findFileId(parentId, remoteName)
                if (recovered.isNullOrBlank()) continue
                db.mediaDao().markUploaded(item.id, recovered, MediaScanner.STATE_UPLOADED, now())
                prefs.lastBackupAt = System.currentTimeMillis()
                uploaded++
                continue
            }

            try {
                uploadStream(parentId, remoteName, item)
                // The upload only returns a job id; resolve the real file record
                // id (staging may lag a beat, so poll briefly).
                var remoteId: String? = null
                for (attempt in 0 until 10) {
                    remoteId = api.findFileId(parentId, remoteName)
                    if (!remoteId.isNullOrBlank()) break
                    delay(1000)
                }
                if (remoteId.isNullOrBlank()) throw java.io.IOException("file record not found after upload")
                db.mediaDao().markUploaded(item.id, remoteId, MediaScanner.STATE_UPLOADED, now())
                prefs.lastBackupAt = System.currentTimeMillis()
                uploaded++
            } catch (e: Exception) {
                Log.w(TAG, "upload failed for ${item.displayName}", e)
                db.mediaDao().markUploaded(item.id, "", MediaScanner.STATE_FAILED, now())
                failed++
                // If the server is unreachable, bail out; WorkManager reschedules.
                if (e is java.io.IOException) {
                    if (failed >= 3) break
                }
            }
        }

        Log.i(TAG, "run complete: uploaded=$uploaded failed=$failed pendingLeft=${db.mediaDao().pendingCount()}")
        Result.success()
    }

    /**
     * Stream the MediaStore item straight into the multipart body — no cache
     * copy, so a 4 GB video never doubles storage on the phone. Chunked
     * transfer; the Go multipart parser accepts it and stages to disk.
     */
    private suspend fun uploadStream(parentId: String, remoteName: String, item: com.dfc.mobile.data.MediaItem) {
        val uri: Uri = ContentUris.withAppendedId(MediaStore.Files.getContentUri("external"), item.id)
        val resolver = applicationContext.contentResolver
        val mime = DfcApi.get(applicationContext).mimeFor(remoteName).toMediaType()
        val part = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("parent_id", parentId)
            .addFormDataPart("file", remoteName, object : okhttp3.RequestBody() {
                override fun contentType() = mime
                // -1 → chunked transfer; the server stages to disk either way.
                override fun contentLength() = -1L
                override fun writeTo(sink: BufferedSink) {
                    // The photo can vanish (user deletes mid-upload); a missing
                    // stream fails this one upload instead of crashing the run.
                    val input = resolver.openInputStream(uri)
                        ?: throw java.io.IOException("source gone: ${item.displayName}")
                    input.use { it.copyTo(sink.outputStream()) }
                }
            })
            .build()
        val req: Request = Request.Builder()
            .url("${apiBaseUrl()}/api/upload/file")
            .header("X-API-Token", apiToken())
            .post(part)
            .build()
        // One client for the whole run so connections are reused across the
        // 40 uploads, instead of a fresh pool per file.
        uploadClient.newCall(req).execute().use { resp ->
            val text = resp.body?.string() ?: throw java.io.IOException("empty response")
            if (!resp.isSuccessful) throw java.io.IOException("HTTP ${resp.code}: ${text.take(200)}")
        }
    }

    private val uploadClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            .callTimeout(0, TimeUnit.MILLISECONDS)
            .build()
    }

    private fun apiBaseUrl(): String = Prefs.get(applicationContext).serverUrl
    private fun apiToken(): String = Prefs.get(applicationContext).token

    private fun now(): Long = System.currentTimeMillis() / 1000

    private fun createForegroundInfo(done: Int, total: Int): ForegroundInfo {
        val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Backups", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val notification: Notification =
            NotificationCompat.Builder(applicationContext, CHANNEL_ID)
                .setContentTitle("Backing up to ddrive")
                .setContentText(if (total > 0) "$done / $total" else "Starting…")
                .setSmallIcon(android.R.drawable.stat_sys_upload)
                .setOngoing(true)
                .setProgress(total, done, total == 0)
                .build()
        return if (Build.VERSION.SDK_INT >= 29) {
            ForegroundInfo(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIF_ID, notification)
        }
    }

    companion object {
        private const val TAG = "BackupWorker"
        private const val CHANNEL_ID = "dfc_backup"
        private const val NOTIF_ID = 42
        private const val WORK_NAME = "dfc_auto_backup"

        /** Cap per run: keeps battery/network usage tiny on first big import. */
        const val MAX_PER_RUN = 40

        /** Enqueue the periodic scan+backup; call after setup completes. */
        fun schedule(context: Context, wifiOnly: Boolean) {
            val constraint = androidx.work.Constraints.Builder()
                .setRequiredNetworkType(
                    if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED
                )
                .setRequiresBatteryNotLow(true)
                .build()
            val request = PeriodicWorkRequestBuilder<BackupWorker>(15, TimeUnit.MINUTES)
                .setConstraints(constraint)
                .setBackoffCriteria(
                    androidx.work.BackoffPolicy.EXPONENTIAL,
                    30, TimeUnit.SECONDS
                )
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request
            )
        }

        /** One-off immediate run (the "Back up now" button). */
        fun runNow(context: Context) {
            val request = androidx.work.OneTimeWorkRequestBuilder<BackupWorker>()
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                "dfc_manual_backup", androidx.work.ExistingWorkPolicy.KEEP, request
            )
        }
    }
}
