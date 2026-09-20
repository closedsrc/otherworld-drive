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
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.dfc.mobile.DfcApi
import com.dfc.mobile.Prefs
import com.dfc.mobile.Server
import com.dfc.mobile.ServerRejectedFile
import com.dfc.mobile.data.AppDb
import com.dfc.mobile.data.MediaItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
        // The periodic job and the "Back up now" button are separate unique work
        // names, so the system will happily run them at the same time. Both would
        // read the same pending batch and upload the same file, and UploadTracker
        // is process-wide state that only describes one run. Serialize here.
        runLock.withLock { runOnce() }
    }

    private suspend fun runOnce(): Result {
        val prefs = Prefs.get(applicationContext)
        if (!prefs.isConfigured) return Result.success()

        val db = AppDb.get(applicationContext)
        val api = DfcApi.get(applicationContext)

        // 1. Reconcile the index (cheap diff; MediaStore does the walking).
        try {
            MediaScanner.scan(applicationContext)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "scan failed", e)
        }

        // 2. Upload pending items sequentially, capped per run to stay light.
        val pending = db.mediaDao().pending(MAX_PER_RUN)
        if (pending.isEmpty()) return Result.success()

        try {
            setForeground(createForegroundInfo(0, pending.size, null))
        } catch (e: Exception) {
            // Foreground can be refused (e.g. notification permission missing);
            // the work still proceeds as a normal background worker.
            Log.i(TAG, "foreground not granted: ${e.message}")
        }

        var uploaded = 0
        var failed = 0
        var ioFailures = 0
        // Bytes are on the server but the listing has not caught up yet, so the
        // file stays pending rather than being recorded with an empty id.
        var missedRecord = 0

        UploadTracker.startRun()
        try {
            for ((index, item) in pending.withIndex()) {
                if (isStopped) break
                try {
                    setForeground(createForegroundInfo(index, pending.size, item.displayName))
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {}

                // Everything for this item sits inside one try: a network hiccup on
                // the folder lookup must fail this file only, never abort the run
                // and lose the per-item accounting below.
                try {
                    val albumPath = destinationPath(item)
                    val remoteName = api.uniqueRemoteName(item.displayName, item.id, item.size)

                    // The server keys files by path, so identical bytes already there
                    // are skipped rather than re-uploaded (covers reinstalls where the
                    // local index was lost). This also adopts anything a build before
                    // this one filed by capture date, instead of sending the same
                    // photo again and leaving two copies on the drive.
                    // Content identity first: hash the bytes and ask the drive.
                    // Name+size alone is what let an EDITED photo be reported as
                    // "already backed up" when it had only kept its size.
                    val uri = ContentUris.withAppendedId(
                        MediaStore.Files.getContentUri("external"), item.id
                    )
                    val hash = api.contentHash(applicationContext, uri)
                    if (!hash.isNullOrBlank()) {
                        val existingId = api.findByContent(hash)
                        if (!existingId.isNullOrBlank()) {
                            db.mediaDao().markUploaded(
                                item.id, existingId, MediaScanner.STATE_UPLOADED, now()
                            )
                            prefs.lastBackupAt = System.currentTimeMillis()
                            UploadTracker.recordSkipped(item.displayName, item.size)
                            uploaded++
                            continue
                        }
                    }

                    val holderId = api.findHoldingFolder(albumPath, legacyPathFor(api, item), remoteName, item.size)
                    if (holderId != null) {
                        // Resolve the real file id so thumbnails work; if the listing
                        // lags, leave it pending for the next pass instead of storing
                        // an empty remoteId that can never load.
                        val recovered = api.findFileId(holderId, remoteName)
                        if (recovered.isNullOrBlank()) {
                            missedRecord++; continue
                        }
                        db.mediaDao().markUploaded(item.id, recovered, MediaScanner.STATE_UPLOADED, now())
                        prefs.lastBackupAt = System.currentTimeMillis()
                        UploadTracker.recordSkipped(item.displayName, item.size)
                        uploaded++
                        continue
                    }

                    // Created only now, so an album nothing new landed in never
                    // leaves an empty folder on the drive.
                    val parentId = api.ensureFolderPath(albumPath)
                    if (parentId.isEmpty()) throw java.io.IOException("no folder id for $albumPath")

                    UploadTracker.beginUpload(
                        displayName = item.displayName,
                        isVideo = item.isVideo,
                        totalBytes = item.size,
                        indexInRun = index,
                        runSize = pending.size,
                    )
                    uploadStream(parentId, remoteName, item)
                    // The upload only returns a job id; resolve the real file record
                    // id (staging may lag a beat, so poll briefly).
                    var remoteId: String? = null
                    for (attempt in 0 until 10) {
                        // Every attempt must look again: the listing this app read
                        // before the upload predates the file it just wrote.
                        api.forgetListing(parentId)
                        remoteId = api.findFileId(parentId, remoteName)
                        if (!remoteId.isNullOrBlank()) break
                        delay(1000)
                    }
                    if (remoteId.isNullOrBlank()) throw java.io.IOException("file record not found after upload")
                    db.mediaDao().markUploaded(item.id, remoteId, MediaScanner.STATE_UPLOADED, now())
                    prefs.lastBackupAt = System.currentTimeMillis()
                    UploadTracker.finishUpload(ok = true)
                    uploaded++
                } catch (e: CancellationException) {
                    // A cancelled run must not relabel the file in flight as failed.
                    UploadTracker.finishUpload(ok = false)
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "upload failed for ${item.displayName}", e)
                    runCatching {
                        db.mediaDao().markUploaded(item.id, "", MediaScanner.STATE_FAILED, now())
                    }
                    UploadTracker.finishUpload(ok = false)
                    failed++
                    if (e is java.io.IOException) {
                        ioFailures++
                        // The server looks unreachable rather than one file being
                        // rejected: stop early and let backoff retry the run.
                        if (ioFailures >= MAX_IO_FAILURES) break
                    }
                }
            }
        } finally {
            UploadTracker.endRun()
        }

        Log.i(
            TAG,
            "run complete: uploaded=$uploaded failed=$failed ioFailures=$ioFailures " +
                "missedRecords=$missedRecord pendingLeft=${db.mediaDao().pendingCount()}",
        )

        // Backoff only applies to Result.retry(), so without this the configured
        // 30s exponential policy could never fire and an unreachable server meant
        // waiting a full 15 minutes with nothing uploaded. The old condition also
        // demanded uploaded == 0, so a run that sent ONE file and then lost the
        // network reported success with 39 items failed — retries became
        // unreachable. Any run that hit the IO-failure ceiling retries, and a
        // run with per-item failures but a live server succeeds (those items are
        // marked failed in the index and retried next run).
        val serverLooksDown = ioFailures >= MAX_IO_FAILURES
        if (serverLooksDown && uploaded > 0) {
            // The network died mid-run. The remaining items stay pending, so
            // queue a one-off run right after this one instead of waiting a
            // full period for them.
            runNow(applicationContext, queueAfterCurrent = true)
        }
        return if (serverLooksDown && uploaded == 0) Result.retry() else Result.success()
    }

    /**
     * Where an item is filed on the drive: one folder per album the phone shows
     * it in. A camera shot lands in "Mobile Backup/Camera", a screenshot in
     * "Mobile Backup/Screenshots", and every other album — and every other kind
     * of media — mirrors across the same way, so the drive reads like the
     * phone's gallery instead of one flat pile of every day's uploads.
     */
    private fun destinationPath(item: MediaItem): String =
        "${DfcApi.LEGACY_ROOT}/${DfcApi.safeFolderName(item.album)}"

    /**
     * The folder this item would have been filed into by the layout that grouped
     * by capture date, or null when the drive holds no such folders and there is
     * nothing left to adopt. Items with no capture date went to "undated", never
     * 1970-01-01.
     */
    private fun legacyPathFor(api: DfcApi, item: MediaItem): String? {
        if (!api.hasDatedFolders()) return null
        val day = if (item.dateTaken > 0)
            Instant.ofEpochSecond(item.dateTaken).atZone(ZoneId.systemDefault()).toLocalDate()
        else null
        return if (day != null) "${DfcApi.LEGACY_ROOT}/${day.format(LEGACY_DAY)}"
        else "${DfcApi.LEGACY_ROOT}/undated"
    }

    /**
     * Stream the MediaStore item straight into the multipart body — no cache
     * copy, so a 4 GB video never doubles storage on the phone. Chunked
     * transfer; the Go multipart parser accepts it and stages to disk.
     */
    /**
     * Upload one item through a resumable session.
     *
     * The old path streamed the whole file with chunked transfer encoding, so a
     * 4 GB video that died at 90% restarted from byte zero — and the run looked
     * like it was making progress the entire time. Now the server keeps the
     * offset: a failure re-reads it and continues from where the bytes actually
     * landed, across network drops, process death and reboots.
     */
    private suspend fun uploadStream(parentId: String, remoteName: String, item: MediaItem) {
        val uri: Uri = ContentUris.withAppendedId(MediaStore.Files.getContentUri("external"), item.id)
        val api = DfcApi.get(applicationContext)

        withContext(Dispatchers.IO) {
            val sessionId = api.openUploadSession(remoteName, item.size, parentId)
                ?: throw java.io.IOException("could not open an upload session")

            // A session this client just opened holds nothing, so the first
            // chunk starts at zero. The offset each PUT returns is what keeps a
            // resumed run honest.
            var offset = 0L

            val input = applicationContext.contentResolver.openInputStream(uri)
                ?: run {
                    api.abortUploadSession(sessionId)
                    throw java.io.IOException("source gone: ${item.displayName}")
                }

            input.use { stream ->
                val buffer = ByteArray(RESUMABLE_CHUNK)
                while (offset < item.size) {
                    if (isStopped) {
                        // Keep the session: the next run resumes from the offset
                        // the server already acknowledged.
                        return@withContext
                    }
                    // skip() may return short on some providers, so loop until
                    // the stream is actually positioned at the server's offset.
                    var toSkip = offset
                    while (toSkip > 0) {
                        val skipped = stream.skip(toSkip)
                        if (skipped <= 0) throw java.io.IOException("could not reach offset $offset")
                        toSkip -= skipped
                    }

                    val read = stream.read(buffer)
                    if (read <= 0) throw java.io.IOException("upload incomplete at $offset/${item.size}")

                    val newOffset = api.putUploadChunk(sessionId, offset, buffer, read)
                    if (newOffset < 0) {
                        // A real failure (network, or a rejected chunk with no
                        // offset in the body). Leave the session alive and let
                        // the next run resume from the server's record.
                        throw java.io.IOException("chunk rejected at $offset")
                    }
                    UploadTracker.addBytes(newOffset - offset)
                    if (newOffset < offset) {
                        // The server is behind what we sent: the stream has to be
                        // rewound, which a non-seekable provider input cannot do.
                        // Reopen and skip to the server's offset instead.
                        stream.close()
                        throw java.io.IOException("upload session rewound to $newOffset")
                    }
                    offset = newOffset
                }
            }

            if (offset < item.size) {
                throw java.io.IOException("upload incomplete at $offset/${item.size}")
            }
            if (!api.commitUploadSession(sessionId)) {
                throw java.io.IOException("server did not accept the finished upload")
            }
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

    private fun now(): Long = System.currentTimeMillis() / 1000

    private fun createForegroundInfo(done: Int, total: Int, currentName: String?): ForegroundInfo {
        val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        // Once per process, not once per file: this ran for every item in the
        // batch, which is 40 binder calls a run to create the same channel.
        if (Build.VERSION.SDK_INT >= 26 && !channelReady) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Backups", NotificationManager.IMPORTANCE_LOW)
            )
            channelReady = true
        }
        val notification: Notification =
            NotificationCompat.Builder(applicationContext, CHANNEL_ID)
                .setContentTitle("Backing up ${currentName ?: "your photos"}")
                .setContentText(if (total > 0) "$done of $total done" else "Starting")
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
        private const val MANUAL_WORK_NAME = "dfc_manual_backup"

        /** The old destination: "Mobile Backup/<yyyy-MM-dd>". */
        private val LEGACY_DAY = DateTimeFormatter.ofPattern("yyyy-MM-dd")

        /** Consecutive network errors before a run gives up and asks for backoff. */
        private const val MAX_IO_FAILURES = 3

        /** 8 MiB chunks: small enough to retry cheaply, large enough to be efficient. */
        private const val RESUMABLE_CHUNK = 8 * 1024 * 1024

        /** Cap per run: keeps battery/network usage tiny on first big import. */
        const val MAX_PER_RUN = 40

        /** One run at a time, whichever path started it. */
        private val runLock = Mutex()

        /** The notification channel is a process-wide resource; create it once. */
        @Volatile private var channelReady = false

        private fun constraints(wifiOnly: Boolean): Constraints =
            Constraints.Builder()
                .setRequiredNetworkType(
                    if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED
                )
                .setRequiresBatteryNotLow(true)
                .build()

        /** Enqueue the periodic scan+backup; call after setup completes. */
        fun schedule(context: Context, wifiOnly: Boolean) {
            val request = PeriodicWorkRequestBuilder<BackupWorker>(15, TimeUnit.MINUTES)
                .setConstraints(constraints(wifiOnly))
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request
            )
        }

        /**
         * One-off immediate run (the "Back up now" button). It carries the same
         * constraints as the periodic job, so a manual tap cannot quietly upload
         * over mobile data while "Wi-Fi only" is on: the work simply waits for an
         * unmetered connection instead.
         *
         * [queueAfterCurrent] is for a trigger that arrives while a run is already
         * in flight, such as a photo saved mid-run. KEEP would drop that request
         * and the photo would sit until the periodic job came round, so it appends
         * instead and starts the moment the run in progress finishes.
         */
        fun runNow(context: Context, queueAfterCurrent: Boolean = false) {
            val request = OneTimeWorkRequestBuilder<BackupWorker>()
                .setConstraints(constraints(Prefs.get(context).wifiOnly))
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                MANUAL_WORK_NAME,
                if (queueAfterCurrent) ExistingWorkPolicy.APPEND_OR_REPLACE
                else ExistingWorkPolicy.KEEP,
                request,
            )
        }
    }
}
