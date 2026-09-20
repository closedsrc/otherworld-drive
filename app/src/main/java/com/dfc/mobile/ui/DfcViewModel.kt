package com.dfc.mobile.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dfc.mobile.DfcApi
import com.dfc.mobile.RemoteFile
import com.dfc.mobile.Prefs
import com.dfc.mobile.backup.BackupWorker
import com.dfc.mobile.backup.MediaScanner
import com.dfc.mobile.backup.UploadTracker
import com.dfc.mobile.data.AppDb
import com.dfc.mobile.data.MediaItem
import com.dfc.mobile.ui.screens.ViewerItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/**
 * Screen state for the whole app. A folder listing is a network round-trip to
 * a remote database, so each folder is fetched once per visit and cached in
 * [folderCache] rather than refetched on every recomposition or tab switch.
 */
class DfcViewModel(app: Application) : AndroidViewModel(app) {

    /** Which kind of thing a row is, derived from the server's own fields. */
    enum class Kind { FOLDER, IMAGE, VIDEO, DOCUMENT, ARCHIVE, OTHER }

    /** What the library grid is narrowed to. Counts are shown on the chips. */
    enum class LibraryFilter { ALL, PHOTOS, VIDEOS, PENDING }

    /**
     * One album as the phone filed it: the MediaStore bucket name, how much is in
     * it, and the newest few items, which are all a cover mosaic needs.
     */
    data class Album(
        val name: String,
        val count: Int,
        val videos: Int,
        val newest: Long,
        val cover: List<MediaItem>,
    )

    data class Ui(
        val configured: Boolean = false,
        val loading: Boolean = true,
        /** The library read, kept apart from [loading] (a folder listing). */
        val indexLoading: Boolean = true,
        val error: String? = null,
        /** Failure of the local index read, kept apart from [error] (a folder listing). */
        val indexError: String? = null,
        /** Failure of the server totals, kept apart from [error] (a folder listing). */
        val storageError: String? = null,
        val storage: DfcApi.Stats? = null,
        val backedUp: Int = 0,
        val pending: Int = 0,
        val lastBackupAt: Long = 0L,
        val wifiOnly: Boolean = true,
        val folderPath: List<RemoteFile> = emptyList(),
        val entries: List<RemoteFile> = emptyList(),
        /** Every image and video this phone can see, newest first. */
        val timeline: List<MediaItem> = emptyList(),
        /** [timeline] grouped by the album MediaStore filed it in. */
        val albums: List<Album> = emptyList(),
        val shares: List<DfcApi.Share> = emptyList(),
        val sharesLoaded: Boolean = false,
        val sharesError: String? = null,
        /** URL of the share just created, shown once. */
        val shareUrl: String? = null,
    )

    private val _ui = MutableStateFlow(Ui())
    val ui: StateFlow<Ui> = _ui.asStateFlow()

    private val folderCache = HashMap<String, List<RemoteFile>>()

    private val prefs: Prefs get() = Prefs.get(getApplication())

    val api: DfcApi get() = DfcApi.get(getApplication())

    init {
        // Re-arm the periodic backup on every launch, not just at setup.
        //
        // schedule() used to run only when the user finished setup, toggled
        // Wi-Fi-only, or the phone rebooted. WorkManager persists its own jobs,
        // but anything that clears them — a force-stop, a battery-optimisation
        // sweep, an OEM "clean up" pass, clearing app data — left the phone with
        // no periodic work and no path back to it short of reinstalling or
        // rebooting. The app would look perfectly healthy, keep indexing photos
        // as they were taken, and never upload them again.
        //
        // ExistingPeriodicWorkPolicy.UPDATE makes this idempotent: the same
        // request simply updates the existing job in place, so an already
        // scheduled backup is not restarted or duplicated by opening the app.
        if (prefs.isConfigured) {
            BackupWorker.schedule(getApplication(), prefs.wifiOnly)
        }
        refresh()
        // The strip reports the pending count the last reload saw, and a run that
        // finishes while this screen is open changes it — 46 items can be down to
        // none with the strip still saying 46. A run ending is the signal to
        // re-read, and it does not start another run: this path never auto-runs.
        viewModelScope.launch {
            UploadTracker.runActive.drop(1).collect { active ->
                if (!active) reloadIndex(allowAutoRun = false)
            }
        }
    }

    fun refresh() = reloadIndex(allowAutoRun = true)

    private var libraryChangeJob: Job? = null

    /**
     * The phone's library changed while the app was open: a photo just came off
     * the camera. Debounced, because a burst of saves notifies once per file and
     * the walk over MediaStore is the expensive part.
     *
     * Unlike opening a screen, this queues a run even if one ran seconds ago, and
     * queues it behind any run in flight: a photo taken now is exactly the thing
     * the user expects to see leave the phone immediately.
     */
    fun onLibraryChanged() {
        libraryChangeJob?.cancel()
        libraryChangeJob = viewModelScope.launch {
            delay(LIBRARY_CHANGE_DEBOUNCE_MS)
            reloadIndex(allowAutoRun = true, queueRun = true)
        }
    }

    /**
     * Read the local index and publish it. [allowAutoRun] is false on the paths
     * that exist because the user just deleted something, where starting a backup
     * would work against what they asked for. [queueRun] skips the staleness
     * check below, for a change that has just happened.
     */
    private fun reloadIndex(allowAutoRun: Boolean, queueRun: Boolean = false) {
        val app = getApplication<Application>()
        _ui.update { it.copy(configured = prefs.isConfigured) }
        if (!prefs.isConfigured) {
            _ui.update {
                it.copy(
                    loading = false,
                    indexLoading = false,
                    entries = emptyList(),
                    timeline = emptyList(),
                    albums = emptyList(),
                    storage = null,
                )
            }
            return
        }
        viewModelScope.launch {
            _ui.update { it.copy(error = null, indexLoading = it.timeline.isEmpty()) }

            // The index reads sit inside the same guard as the scan itself. A Room
            // failure here used to abandon this coroutine before `loading` was
            // cleared, leaving the Files tab spinning with no error path at all.
            try {
                val db = AppDb.get(app)
                // Every open is a backup opportunity: reconcile the index first,
                // then let the worker drain what is pending (Wi-Fi gate applies).
                val (pending, done, items) = withContext(Dispatchers.IO) {
                    runCatching { MediaScanner.scan(app) }
                    Triple(
                        db.mediaDao().pendingCount(),
                        db.mediaDao().uploadedCount(),
                        db.mediaDao().timeline(TIMELINE_CAP),
                    )
                }

                // Firing a manual run on every open would race the 15-minute
                // worker and double-upload, so it only piggybacks when the
                // periodic job is stale and work is actually waiting.
                val stale = prefs.lastBackupAt < System.currentTimeMillis() - 10 * 60_000
                if (allowAutoRun && pending > 0 && (queueRun || stale)) {
                    BackupWorker.runNow(app, queueAfterCurrent = queueRun)
                }

                _ui.update {
                    it.copy(
                        backedUp = done,
                        pending = pending,
                        timeline = items,
                        albums = albumsOf(items),
                        lastBackupAt = prefs.lastBackupAt,
                        wifiOnly = prefs.wifiOnly,
                        loading = false,
                        indexLoading = false,
                        indexError = null,
                    )
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _ui.update {
                    it.copy(
                        loading = false,
                        indexLoading = false,
                        indexError = e.message ?: "Could not read this phone's photo index",
                    )
                }
            }

            // Server totals: a separate call, and a failure here must not wipe the
            // local view. It gets its own field, because routing it through `error`
            // made the Files tab report the current folder as unloadable.
            runCatching { withContext(Dispatchers.IO) { api.stats() } }
                .onSuccess { s -> _ui.update { it.copy(storage = s, storageError = null) } }
                .onFailure { e ->
                    _ui.update {
                        it.copy(storageError = e.message ?: "Could not reach the server")
                    }
                }
        }
    }

    /** Load the root listing on first visit to Files. */
    fun loadRootIfNeeded() {
        if (_ui.value.entries.isEmpty() && _ui.value.folderPath.isEmpty()) openFolder(null)
    }

    fun openFolder(folder: RemoteFile?) {
        val stack = _ui.value.folderPath
        val next = if (folder == null) emptyList() else stack + folder
        loadFolder(folder?.id ?: "", next)
    }

    /**
     * Load one folder with an explicit breadcrumb stack. The stack is passed in
     * rather than appended to because navigating back must REPLACE the path:
     * appending on every load duplicated crumbs ([A] opened as [A, A]) and the
     * trail grew a copy of the folder on each revisit.
     *
     * Cached listings paint immediately with a background revalidation, so
     * going back to a visited folder feels instant instead of round-tripping
     * first. A generation counter drops stale responses when the user taps
     * through folders faster than the server answers.
     */
    private var folderRequest = 0

    private fun loadFolder(parentId: String, stack: List<RemoteFile>) {
        val gen = ++folderRequest
        val cached = folderCache[parentId]
        if (cached != null) {
            _ui.update { it.copy(loading = false, error = null, entries = cached, folderPath = stack) }
        } else {
            _ui.update { it.copy(loading = true, error = null) }
        }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { api.listFiles(parentId) }
            }
            if (gen != folderRequest) return@launch
            result
                .onSuccess { list ->
                    folderCache[parentId] = list
                    if (_ui.value.folderPath == stack || cached == null) {
                        _ui.update { it.copy(loading = false, entries = list, folderPath = stack) }
                    } else {
                        _ui.update { it.copy(loading = false) }
                    }
                    // The root landing warms the whole tree in the background,
                    // desktop-style: every later folder then opens from cache.
                    if (parentId.isEmpty()) warmTree()
                }
                .onFailure { e ->
                    // A cached listing stays on screen; the error only replaces
                    // content when there is nothing to show (first load).
                    _ui.update {
                        it.copy(
                            loading = false,
                            error = if (it.entries.isEmpty()) {
                                e.message ?: "Could not list this folder"
                            } else it.error,
                        )
                    }
                }
        }
    }

    /**
     * Reload the open folder from the server. This is what the Files
     * retry/refresh action runs: the generic refresh() only re-reads this
     * phone's index and the server totals, so a failed listing used to have
     * no working retry at all.
     */
    fun refreshFolder() {
        val stack = _ui.value.folderPath
        val parentId = stack.lastOrNull()?.id ?: ""
        folderCache.remove(parentId)
        loadFolder(parentId, stack)
    }

    /**
     * Desktop-equivalent bulk load: after the root listing lands, walk the
     * folder tree in the background with bounded parallelism and fill the
     * listing cache. Folder opens become instant, search sees the full drive
     * even if the server search ever fails, and each folder costs one small
     * request instead of one navigation stall.
     */
    private fun warmTree() {
        viewModelScope.launch(Dispatchers.IO) {
            val root = folderCache[""] ?: return@launch
            val seen = HashSet<String>()
            seen.add("")
            val queue = ArrayDeque<String>()
            root.filter { it.isDir }.forEach { queue.add(it.id) }
            val gate = Semaphore(4)
            var fetched = 0
            while (queue.isNotEmpty() && fetched < TREE_PREFETCH_CAP) {
                val batch = mutableListOf<String>()
                while (batch.size < 8 && queue.isNotEmpty()) {
                    val id = queue.removeFirst()
                    if (seen.add(id)) batch.add(id)
                }
                if (batch.isEmpty()) break
                val results = batch.map { id ->
                    async {
                        gate.withPermit {
                            runCatching { api.listFiles(id) }.getOrNull()?.let { id to it }
                        }
                    }
                }.awaitAll().filterNotNull()
                if (results.isEmpty()) break
                fetched += results.size
                for ((id, list) in results) {
                    folderCache[id] = list
                    list.filter { it.isDir }.forEach { if (it.id !in seen) queue.add(it.id) }
                }
            }
        }
    }

    /** Pop back to [depth] levels up; 0 is the root. */
    fun navigateToDepth(depth: Int) {
        val stack = _ui.value.folderPath
        if (depth < 0 || depth > stack.size) return
        val next = stack.take(depth)
        loadFolder(next.lastOrNull()?.id ?: "", next)
    }

    /** Current folder depth, for the system back gesture. */
    fun folderDepth(): Int = _ui.value.folderPath.size

    /** Back one folder level. No-op at the root. */
    fun navigateUp() {
        val stack = _ui.value.folderPath
        if (stack.isEmpty()) return
        navigateToDepth(stack.size - 1)
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

    fun delete(files: List<RemoteFile>, onDone: (Pair<Int, Int>) -> Unit) =
        deleteIds(files.map { it.id }, onDone)

    /**
     * Delete by server file id.
     *
     * A successful delete also moves the matching local rows to "removed", so the
     * library stops reporting a file that is sitting in the drive's trash as
     * backed up. It deliberately does not re-queue them: the backup engine's job
     * is to send anything on this phone, so re-queueing would undo the delete a
     * quarter of an hour later. Sending it again is an explicit action instead.
     */
    fun deleteIds(ids: List<String>, onDone: (Pair<Int, Int>) -> Unit) {
        if (ids.isEmpty()) {
            onDone(0 to 0)
            return
        }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { api.deleteFiles(ids) }.getOrElse { 0 to ids.size }
            }
            if (result.first > 0) {
                withContext(Dispatchers.IO) {
                    runCatching {
                        AppDb.get(getApplication()).mediaDao()
                            .markRemovedByRemoteIds(ids, MediaScanner.STATE_REMOVED)
                    }
                }
            }
            folderCache.clear()
            refreshCurrentFolder()
            // Reload the index without letting it start a run: this path exists
            // because the user just deleted something.
            reloadIndex(allowAutoRun = false)
            onDone(result)
        }
    }

    /** Put a removed item back in the queue, from the viewer. */
    fun sendAgain(item: MediaItem) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                runCatching { AppDb.get(getApplication()).mediaDao().rearm(item.id) }
            }
            reloadIndex(allowAutoRun = false)
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
            // The user may have navigated elsewhere while this refetched; never
            // paint a listing onto the wrong folder.
            if (_ui.value.folderPath == stack) _ui.update { it.copy(entries = list) }
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

    fun share(file: RemoteFile, ttlDays: Int, onDone: (String?) -> Unit) =
        shareId(file.id, ttlDays, onDone)

    /** Same as [share], for a library item that only carries its remote id. */
    fun shareId(fileId: String, ttlDays: Int, onDone: (String?) -> Unit) {
        viewModelScope.launch {
            val url = withContext(Dispatchers.IO) {
                runCatching { api.createShare(fileId, ttlDays) }.getOrNull()
            }
            if (url != null) loadShares(force = true)
            onDone(url)
        }
    }

    /**
     * Transient UI requests the host activity turns into dialogs or system
     * intents. Kept in the view model so a screen can ask for an action without
     * owning the plumbing, and so the request survives rotation.
     */
    private val _action = MutableStateFlow<UiAction?>(null)
    val action: StateFlow<UiAction?> = _action.asStateFlow()

    /** Last share URL created, for the "copy it now" surface. */
    var lastShareUrl: String?
        get() = _ui.value.shareUrl
        set(value) { _ui.update { it.copy(shareUrl = value) } }

    fun requestDownload(file: RemoteFile) { _action.value = UiAction.Download(file) }
    fun requestRename(file: RemoteFile) { _action.value = UiAction.Rename(file) }
    fun requestDetails(file: RemoteFile) { _action.value = UiAction.Details(file) }
    fun requestNewFolder() { _action.value = UiAction.NewFolder }
    fun requestViewerDetails(item: ViewerRequest) { _action.value = UiAction.ViewerDetails(item) }
    fun consumeAction() { _action.value = null }

    fun setMessage(text: String) { _action.value = UiAction.Message(text) }

    /** Flip the Wi-Fi-only gate and re-arm the periodic job to match. */
    fun setWifiOnly(enabled: Boolean) {
        prefs.wifiOnly = enabled
        com.dfc.mobile.backup.BackupWorker.schedule(getApplication(), enabled)
        refresh()
    }

    /** Drop this device's key; revocation is a server-side action. */
    fun signOut() {
        prefs.token = ""
        prefs.deviceId = ""
        refresh()
    }

    /** Build the viewer's item list for a route (local media or drive file). */
    fun viewerItems(route: com.dfc.mobile.ui.ViewerRoute): List<ViewerItem> {
        if (route.remote) {
            val file = _ui.value.entries.firstOrNull { it.id == route.fileId }
                ?: return emptyList()
            return listOf(
                ViewerItem(
                    displayName = file.name,
                    size = file.size,
                    dateTaken = file.modTime,
                    isVideo = classify(file) == Kind.VIDEO,
                    remoteId = file.id,
                )
            )
        }
        // Local: show the whole timeline so the pager can actually page.
        return _ui.value.timeline.map {
            ViewerItem(
                displayName = it.displayName,
                size = it.size,
                dateTaken = it.dateTaken,
                isVideo = it.isVideo,
                remoteId = it.remoteId,
                localId = it.id,
            )
        }
    }

    fun shareViewer(item: ViewerItem) {
        val id = item.remoteId
        if (id.isNullOrBlank()) setMessage("This item is not on the drive yet")
        else shareId(id, 7) { lastShareUrl = it }
    }

    fun downloadViewer(item: ViewerItem) {
        val id = item.remoteId ?: return
        requestDownload(RemoteFile(id, "", item.displayName, item.size, false, item.dateTaken, ""))
    }

    fun deleteViewer(item: ViewerItem) {
        val id = item.remoteId ?: return
        delete(listOf(RemoteFile(id, "", item.displayName, item.size, false, item.dateTaken, ""))) { }
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

    /**
     * Whole-drive search in one server round trip (the same view endpoint the
     * web dashboard's search box uses), with the visited-folders cache as the
     * offline fallback. It used to scan only listings opened this session, so
     * most files were unfindable until the user had browsed to them by hand.
     */
    suspend fun searchRemote(query: String): List<RemoteFile> =
        withContext(Dispatchers.IO) {
            val needle = query.trim()
            if (needle.isEmpty()) return@withContext emptyList()
            runCatching { api.searchFiles(needle) }.getOrNull()
                ?: folderCache.values
                    .flatten()
                    .filter { it.name.contains(needle, ignoreCase = true) }
                    .distinctBy { it.id }
        }

    /**
     * Group the timeline into albums the way the phone filed it. This runs in
     * memory over the single read the timeline already made: one query per album
     * would be one network round trip per album, because the database behind this
     * is not on the device.
     */
    private fun albumsOf(items: List<MediaItem>): List<Album> =
        items.groupBy { it.album.ifEmpty { "Other" } }
            .map { (name, members) ->
                Album(
                    name = name,
                    count = members.size,
                    videos = members.count { it.isVideo },
                    // The timeline is newest first, so the first member is the
                    // album's newest item and the first four are its cover.
                    newest = members.first().dateTaken,
                    cover = members.take(4),
                )
            }
            .sortedByDescending { it.newest }

    companion object {
        /**
         * How much of the library is held in memory at once. A Compose grid only
         * renders what is visible, so this is about the size of the list itself,
         * not of the screen.
         */
        const val TIMELINE_CAP = 5000

        /**
         * How long a library change is allowed to settle before the index is
         * re-read. Saving several photos at once notifies once per file, and the
         * scan walk is the part worth not repeating.
         */
        const val LIBRARY_CHANGE_DEBOUNCE_MS = 3_000L

        /**
         * How many folders the background tree prefetch walks before stopping.
         * Past this the drive is large enough that on-demand loads take over.
         */
        const val TREE_PREFETCH_CAP = 300

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


/** A one-shot request a screen makes of the host (dialog, intent, snackbar). */
sealed interface UiAction {
    data class Download(val file: RemoteFile) : UiAction
    data class Rename(val file: RemoteFile) : UiAction
    data class Details(val file: RemoteFile) : UiAction
    data object NewFolder : UiAction
    data class ViewerDetails(val item: ViewerRequest) : UiAction
    data class Message(val text: String) : UiAction
}

/** What the viewer is showing, so Details can be requested without the item type. */
data class ViewerRequest(
    val displayName: String,
    val size: Long,
    val dateTaken: Long,
    val remoteId: String?,
)
