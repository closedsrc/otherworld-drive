package com.dfc.mobile.backup

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Live view of what the worker is doing right now. The engine has always
 * reported progress to a notification; the Uploads screen needs the same
 * numbers in-process, so the worker publishes them here instead of the UI
 * guessing from a pending count.
 *
 * Everything in here is measured, never estimated: byte totals come from the
 * bytes actually handed to the socket, and the speed figure is bytes divided
 * by elapsed time for the file in flight.
 */
object UploadTracker {

    data class Job(
        val displayName: String,
        val isVideo: Boolean,
        val totalBytes: Long,
        val sentBytes: Long,
        val startedAt: Long,
        val indexInRun: Int,
        val runSize: Int,
    ) {
        val fraction: Float
            get() = if (totalBytes <= 0L) 0f
            else (sentBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)

        /** Bytes per second over the life of this file, or 0 before any data moves. */
        val bytesPerSecond: Long
            get() {
                val elapsed = (System.currentTimeMillis() - startedAt) / 1000.0
                return if (elapsed < 0.5) 0L else (sentBytes / elapsed).toLong()
            }

        val remainingSeconds: Long
            get() {
                val rate = bytesPerSecond
                if (rate <= 0L) return 0L
                return ((totalBytes - sentBytes) / rate).coerceAtLeast(0L)
            }
    }

    /** A finished upload, kept for the session so "Completed" shows real work. */
    data class Done(
        val displayName: String,
        val sizeBytes: Long,
        val at: Long,
        val ok: Boolean,
    )

    private val _current = MutableStateFlow<Job?>(null)
    val current: StateFlow<Job?> = _current.asStateFlow()

    private val _completed = MutableStateFlow<List<Done>>(emptyList())
    val completed: StateFlow<List<Done>> = _completed.asStateFlow()

    private val _runActive = MutableStateFlow(false)
    val runActive: StateFlow<Boolean> = _runActive.asStateFlow()

    fun startRun() {
        _runActive.value = true
    }

    fun beginUpload(
        displayName: String,
        isVideo: Boolean,
        totalBytes: Long,
        indexInRun: Int,
        runSize: Int,
    ) {
        _current.value = Job(
            displayName = displayName,
            isVideo = isVideo,
            totalBytes = totalBytes,
            sentBytes = 0L,
            startedAt = System.currentTimeMillis(),
            indexInRun = indexInRun,
            runSize = runSize,
        )
    }

    /** Called from the upload sink; [delta] is what the socket just accepted. */
    fun addBytes(delta: Long) {
        _current.update { job ->
            job?.copy(sentBytes = (job.sentBytes + delta).coerceAtMost(job.totalBytes))
        }
    }

    fun finishUpload(ok: Boolean) {
        val job = _current.value
        _current.value = null
        if (job == null) return
        _completed.update { list ->
            (listOf(
                Done(
                    displayName = job.displayName,
                    sizeBytes = job.totalBytes,
                    at = System.currentTimeMillis(),
                    ok = ok,
                )
            ) + list).take(50)
        }
    }

    /** Skips (already on the server) count as completed without a transfer. */
    fun recordSkipped(displayName: String, sizeBytes: Long) {
        _completed.update { list ->
            (listOf(Done(displayName, sizeBytes, System.currentTimeMillis(), true)) + list).take(50)
        }
    }

    fun endRun() {
        _current.value = null
        _runActive.value = false
    }
}
