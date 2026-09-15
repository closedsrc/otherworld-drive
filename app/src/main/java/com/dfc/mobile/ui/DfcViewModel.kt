package com.dfc.mobile.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dfc.mobile.DfcApi
import com.dfc.mobile.RemoteFile
import com.dfc.mobile.Prefs
import com.dfc.mobile.backup.BackupWorker
import com.dfc.mobile.backup.MediaScanner
import com.dfc.mobile.data.AppDb
import com.dfc.mobile.data.MediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Screen state for the whole app. A folder listing is a network round-trip to
 * a remote database, so each folder is fetched once per visit and cached in
 * [folderCache] rather than refetched on every recomposition or tab switch.
 */
class DfcViewModel(app: Application) : AndroidViewModel(app) {

    /** Which kind of thing a row is, derived from the server's own fields. */
    enum class Kind { FOLDER, IMAGE, VIDEO, DOCUMENT, ARCHIVE, OTHER }

    data class Ui(
        val configured: Boolean = false,
        val loading: Boolean = true,
        val error: String? = null,
        val storage: DfcApi.Stats? = null,
        val backedUp: Int = 0,
        val pending: Int = 0,
        val lastBackupAt: Long = 0L,
        val wifiOnly: Boolean = true,
        val folderPath: List<RemoteFile> = emptyList(),
        val entries: List<RemoteFile> = emptyList(),
        val gallery: List<MediaItem> = emptyList(),
        val shares: List<DfcApi.Share> = emptyList(),
        val sharesLoaded: Boolean = false,
        val sharesError: String? = null,
    )

    private val _ui = MutableStateFlow(Ui())
    val ui: StateFlow<Ui> = _ui.asStateFlow()

    private val folderCache = HashMap<String, List<RemoteFile>>()

    private val prefs: Prefs get() = Prefs.get(getApplication())

    val api: DfcApi get() = DfcApi.get(getApplication())

    init {
        refresh()
    }

    fun refresh() {
        val app = getApplication<Application>()
        _ui.update { it.copy(configured = prefs.isConfigured) }
        if (!prefs.isConfigured) {
            _ui.update { it.copy(loading = false, entries = emptyList(), storage = null) }
            return
        }
        viewModelScope.launch {
            _ui.update { it.copy(loading = it.entries.isEmpty() && it.gallery.isEmpty(), error = null) }

            val db = AppDb.get(app)
            // Every open is a backup opportunity: reconcile the index first, then
            // let the worker drain whatever is pending (Wi-Fi gate still applies).
            withContext(Dispatchers.IO) {
                runCatching { MediaScanner.scan(app) }
            }
            val pending = withContext(Dispatchers.IO) { db.mediaDao().pendingCount() }
            val done = withContext(Dispatchers.IO) { db.mediaDao().uploadedCount() }
            val gallery = withContext(Dispatchers.IO) { db.mediaDao().uploaded(2000) }

            // Firing a manual run on every open would race the 15-minute worker
            // and double-upload, so it only piggybacks when the periodic job is
            // stale and work is actually waiting.
            val stale = prefs.lastBackupAt < System.currentTimeMillis() - 10 * 60_000
            if (pending > 0 && stale) BackupWorker.runNow(app)

            _ui.update {
                it.copy(
                    backedUp = done,
                    pending = pending,
                    gallery = gallery,
                    lastBackupAt = prefs.lastBackupAt,
                    wifiOnly = prefs.wifiOnly,
                    loading = false,
                )
            }

            // Server totals: separate call, and a failure here must not wipe the
            // local view, so it updates only the storage slot.
            runCatching { withContext(Dispatchers.IO) { api.stats() } }
                .onSuccess { s -> _ui.update { it.copy(storage = s) } }
                .onFailure { e ->
                    _ui.update { it.copy(error = e.message ?: "Could not reach the server") }
                }
        }
    }

    /** Load the root listing on first visit to Files. */
    fun loadRootIfNeeded() {
        if (_ui.value.entries.isEmpty() && _ui.value.folderPath.isEmpty()) openFolder(null)
    }

    fun openFolder(folder: RemoteFile?) {
        val parentId = folder?.id ?: ""
        viewModelScope.launch {
            _ui.update { it.copy(loading = true, error = null) }
            val result = withContext(Dispatchers.IO) {
                runCatching { folderCache[parentId] ?: api.listFiles(parentId).also { folderCache[parentId] = it } }
            }
            result
                .onSuccess { list ->
                    _ui.update {
                        it.copy(
                            loading = false,
                            entries = list,
                            folderPath = if (folder == null) emptyList()
                            else it.folderPath + folder,
                        )
                    }
                }
                .onFailure { e ->
                    _ui.update {
                        it.copy(loading = false, error = e.message ?: "Could not list this folder")
                    }
                }
        }
    }

    /** Pop back to [depth] levels up; 0 is the root. */
    fun navigateToDepth(depth: Int) {
        val stack = _ui.value.folderPath
        if (depth >= stack.size) return
        if (depth == 0) {
            _ui.update { it.copy(folderPath = emptyList()) }
            openFolder(null)
        } else {
            val target = stack[depth - 1]
            _ui.update { it.copy(folderPath = stack.take(depth)) }
            openFolder(target)
        }
    }

    fun createFolder(name: String, onDone: (Boolean) -> Unit) {
        val parentId = _ui.value.folderPath.lastOrNull()?.id ?: ""
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching { api.createFolder(name, parentId); true }.getOrDefault(false)
            }
            if (ok) {
                // The new folder is not in the cached listing; drop it so the
                // next visit sees the folder the server just created.
                folderCache.remove(parentId)
                refreshCurrentFolder()
            }
            onDone(ok)
        }
    }

    fun delete(files: List<RemoteFile>, onDone: (Pair<Int, Int>) -> Unit) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { api.deleteFiles(files.map { it.id }) }
                    .getOrElse { 0 to files.size }
            }
            folderCache.clear()
            refreshCurrentFolder()
            onDone(result)
        }
    }

    private fun refreshCurrentFolder() {
        val stack = _ui.value.folderPath
        val parentId = stack.lastOrNull()?.id ?: ""
        viewModelScope.launch {
            val list = withContext(Dispatchers.IO) {
                runCatching { api.listFiles(parentId) }.getOrNull()
            } ?: return@launch
            folderCache[parentId] = list
            _ui.update { it.copy(entries = list) }
        }
    }

    /** Public links, loaded once per app session unless forced. */
    fun loadShares(force: Boolean = false) {
        if (_ui.value.sharesLoaded && !force) return
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { api.listShares() } }
            result
                .onSuccess { list ->
                    _ui.update { it.copy(shares = list, sharesLoaded = true, sharesError = null) }
                }
                .onFailure { e ->
                    _ui.update {
                        it.copy(sharesLoaded = true, sharesError = e.message ?: "Could not load links")
                    }
                }
        }
    }

    fun share(file: RemoteFile, ttlDays: Int, onDone: (String?) -> Unit) {
        viewModelScope.launch {
            val url = withContext(Dispatchers.IO) {
                runCatching { api.createShare(file.id, ttlDays) }.getOrNull()
            }
            if (url != null) loadShares(force = true)
            onDone(url)
        }
    }

    fun revoke(share: DfcApi.Share) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { runCatching { api.revokeShare(share.id) } }
            loadShares(force = true)
        }
    }

    fun backupNow() {
        val app = getApplication<Application>()
        if (!prefs.isConfigured) return
        BackupWorker.runNow(app)
        // The run is asynchronous; a short delay is enough for the first item to
        // land in the tracker, and the Uploads screen observes from there.
        viewModelScope.launch {
            kotlinx.coroutines.delay(1200)
            refresh()
        }
    }

    fun classify(file: RemoteFile): Kind {
        if (file.isDir) return Kind.FOLDER
        val mime = file.mimeType.lowercase()
        val ext = file.name.substringAfterLast('.', "").lowercase()
        return when {
            mime.startsWith("image/") || ext in IMAGE_EXT -> Kind.IMAGE
            mime.startsWith("video/") || ext in VIDEO_EXT -> Kind.VIDEO
            ext in ARCHIVE_EXT -> Kind.ARCHIVE
            ext in DOC_EXT || mime.startsWith("text/") || mime.startsWith("application/pdf") -> Kind.DOCUMENT
            else -> Kind.OTHER
        }
    }

    fun isThumbnailable(kind: Kind) = kind == Kind.IMAGE || kind == Kind.VIDEO

    /**
     * The server exposes no search endpoint, so this searches what the app has
     * already downloaded: every folder listing opened since launch. The Search
     * screen states that scope rather than implying a full-drive index.
     */
    suspend fun searchRemote(query: String): List<RemoteFile> =
        withContext(Dispatchers.IO) {
            val needle = query.trim().lowercase()
            if (needle.isEmpty()) return@withContext emptyList()
            folderCache.values
                .flatten()
                .filter { it.name.lowercase().contains(needle) }
                .distinctBy { it.id }
        }

    companion object {
        val IMAGE_EXT = setOf("jpg", "jpeg", "png", "gif", "webp", "heic", "heif", "bmp", "avif")
        val VIDEO_EXT = setOf("mp4", "webm", "mov", "mkv", "3gp", "avi", "m4v")
        val ARCHIVE_EXT = setOf("zip", "rar", "7z", "tar", "gz", "bz2", "xz")
        val DOC_EXT = setOf(
            "pdf", "txt", "md", "json", "csv", "xml", "log", "yaml", "yml",
            "kt", "java", "js", "ts", "py", "go", "rs", "c", "h", "cpp", "sh",
            "html", "css", "sql", "toml", "ini", "conf",
        )
    }
}

/** Human file size. Binary units, because storage tools are read that way. */
fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var unit = 0
    while (value >= 1024 && unit < units.lastIndex) {
        value /= 1024
        unit++
    }
    return if (unit == 0) "${bytes} B"
    else String.format("%.1f %s", value, units[unit])
}

/** Compact duration for transfer estimates: 45s, 3m 20s, 1h 5m. */
fun formatDuration(seconds: Long): String {
    if (seconds <= 0) return "unknown"
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return when {
        h > 0 -> "${h}h ${m}m"
        m > 0 -> "${m}m ${s}s"
        else -> "${s}s"
    }
}

/** Relative time for metadata lines, kept short enough for one row. */
fun relativeTime(epochSeconds: Long): String {
    if (epochSeconds <= 0) return "unknown"
    val now = System.currentTimeMillis() / 1000
    val diff = now - epochSeconds
    return when {
        diff < 60 -> "just now"
        diff < 3600 -> "${diff / 60} min ago"
        diff < 86_400 -> "${diff / 3600} h ago"
        diff < 7 * 86_400 -> "${diff / 86_400} d ago"
        diff < 30 * 86_400 -> "${diff / (7 * 86_400)} w ago"
        else -> "${diff / (30 * 86_400)} mo ago"
    }
}

/** Context helper so callers do not each repeat the prefs lookup. */
fun isConfigured(context: Context): Boolean = Prefs.get(context).isConfigured
