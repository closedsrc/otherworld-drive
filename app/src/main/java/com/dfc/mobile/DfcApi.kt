package com.dfc.mobile

import android.content.Context
import android.util.Log
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/** One row of the server's file listing (subset of db.FileRecord). */
data class RemoteFile(
    val id: String,
    val parentId: String,
    val name: String,
    val size: Long,
    val isDir: Boolean,
    val modTime: Long,
    val mimeType: String,
)

class ApiException(message: String) : IOException(message)

/**
 * The server looked at a file and refused it, so sending it again unchanged will
 * fail again. Deliberately NOT an [IOException]: the backup run reads an IO
 * failure as "the server looks unreachable" and abandons the batch after three,
 * so three permanently unacceptable files would discard every remaining item.
 */
class ServerRejectedFile(message: String) : Exception(message)

/**
 * Minimal client for the Discord-Free-Cloud HTTP API. Every call carries the
 * X-API-Token header; the server rejects query-param tokens outright, which is
 * why ThumbLoader and VideoView go through this client / explicit headers.
 */
class DfcApi(private val prefs: Prefs) {

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .callTimeout(0, TimeUnit.MILLISECONDS) // uploads of big videos must not time out
        // Grids fire many renditions at once and the tree prefetch fans out too:
        // the default 5-requests-per-host cap would serialize them.
        .dispatcher(okhttp3.Dispatcher().apply { maxRequestsPerHost = 16 })
        .build()

    /** Cache of "Mobile Backup/2026-09-14" → folder id, per worker run. */
    private val folderCache = HashMap<String, String>()

    /**
     * Folder listings reused within one backup run.
     *
     * The skip check and the id lookup for one file are two questions about the
     * same folder, and asking the server separately for each meant two round
     * trips per file. Over a 40-item batch that was around 80 requests to a
     * database that is not on this phone, all to learn one listing 40 times.
     * The worker drops a folder's entry the moment it writes to it, so a cached
     * listing is never used to decide anything about a file just uploaded.
     */
    private val listingCache = HashMap<String, List<RemoteFile>>()

    private fun listingFor(parentId: String): List<RemoteFile> {
        synchronized(listingCache) { listingCache[parentId]?.let { return it } }
        val fresh = listFiles(parentId)
        synchronized(listingCache) { listingCache[parentId] = fresh }
        return fresh
    }

    /** Forget one folder's listing after this app changed its contents. */
    fun forgetListing(parentId: String) {
        synchronized(listingCache) { listingCache.remove(parentId) }
    }

    // The host is configurable (setup screen); only the token comes from prefs.
    private fun base(): String = Server.baseUrl(prefs)

    private fun req(url: String): Request.Builder =
        Request.Builder().url(url).header("X-API-Token", prefs.token)

    // ---- status ------------------------------------------------------------

    fun status(): JSONObject {
        val resp = client.newCall(req("${base()}/api/status").get().build()).execute()
        resp.use { return bodyJson(it) }
    }

    // ---- files -------------------------------------------------------------

    fun listFiles(parentId: String): List<RemoteFile> {
        val url = "${base()}/api/files?parent_id=${urlEnc(parentId)}"
        val resp = client.newCall(req(url).get().build()).execute()
        resp.use { return parseFiles(bodyText(it)) }
    }

    /**
     * Whole-drive listing in ONE round trip, via the same view endpoint the web
     * dashboard uses for its search box. The desktop client loads the entire
     * tree with a single bulk call and filters locally; this is the mobile
     * equivalent — one request instead of one per folder, and results cover
     * folders the phone has never opened.
     */
    fun searchFiles(query: String): List<RemoteFile> {
        val url = "${base()}/api/files/view?search=${urlEnc(query)}"
        val resp = client.newCall(req(url).get().build()).execute()
        resp.use { return parseFiles(bodyText(it)) }
    }

    private fun parseFiles(text: String): List<RemoteFile> {
        val arr = JSONArray(text)
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            RemoteFile(
                id = o.getString("id"),
                parentId = o.optString("parent_id"),
                name = o.getString("name"),
                size = o.optLong("size"),
                isDir = o.optBoolean("is_dir"),
                modTime = o.optLong("mod_time"),
                mimeType = o.optString("mime_type"),
            )
        }
    }

    /** Resolve (and create when missing) "Mobile Backup/Camera", cached. */
    @Synchronized
    fun ensureFolderPath(path: String): String {
        folderCache[path]?.let { return it }
        var parentId = ""
        val segments = path.split('/').filter { it.isNotBlank() }
        for (segment in segments) {
            val existing = listFiles(parentId).firstOrNull { it.isDir && it.name == segment }
            parentId = existing?.id ?: createFolder(segment, parentId)
        }
        folderCache[path] = parentId
        return parentId
    }

    /**
     * Resolve a path that must already exist, or null. Unlike [ensureFolderPath]
     * this creates nothing, which is what lets the worker look for a file it
     * uploaded under the old date-folder layout without recreating that layout
     * on a drive that never had one.
     */
    @Synchronized
    fun existingFolderPath(path: String): String? {
        folderCache[path]?.let { return it }
        var parentId = ""
        for (segment in path.split('/').filter { it.isNotBlank() }) {
            val found = listFiles(parentId).firstOrNull { it.isDir && it.name == segment }
                ?: return null
            parentId = found.id
        }
        folderCache[path] = parentId
        return parentId
    }

    /**
     * True when the backup folder still holds date-named folders from the layout
     * that filed by capture date. Costs one listing per run and lets a library
     * that has already migrated stop paying for the old-layout lookups at all.
     */
    fun hasDatedFolders(): Boolean {
        val root = existingFolderPath(LEGACY_ROOT) ?: return false
        return listingFor(root).any { it.isDir && LEGACY_DAY.matches(it.name) }
    }

    fun createFolder(name: String, parentId: String): String {
        val payload = JSONObject().put("name", name).put("parent_id", parentId)
        val body = payload.toString().toRequestBody("application/json".toMediaType())
        val resp = client.newCall(req("${base()}/api/folders/create").post(body).build()).execute()
        resp.use {
            val json = bodyJson(it)
            return json.getString("id")
        }
    }

    /**
     * Drop the folder-id cache. The client is a singleton, so it outlives a
     * Setup re-connect: without this, a server switch keeps the old server's
     * folder ids and uploads target the wrong tree.
     */
    @Synchronized
    fun invalidateCache() {
        folderCache.clear()
        synchronized(listingCache) { listingCache.clear() }
    }

    /** MIME for a filename; shared with the worker's streaming upload. */
    fun mimeFor(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "gif" -> "image/gif"
        "webp" -> "image/webp"
        "heic", "heif" -> "image/heic"
        "mp4" -> "video/mp4"
        "webm" -> "video/webm"
        "mov" -> "video/quicktime"
        "mkv" -> "video/x-matroska"
        "3gp" -> "video/3gpp"
        else -> "application/octet-stream"
    }

    /**
     * The upload endpoint only returns a job_id; the file record gets its own
     * UUID once the job is queued, so resolve the real id from the folder
     * listing — thumbnails and previews are keyed by the file id, not the job.
     */
    fun findFileId(parentId: String, name: String): String? =
        listingFor(parentId).firstOrNull { it.name == name && !it.isDir }?.id

    /**
     * Server-side files are keyed by path, so two same-named photos in one day
     * folder would replace each other. The fragment is DERIVED from the media
     * id + size (not random), so every run — retry, reinstall, or a racing
     * concurrent worker — converges on the same name and the skip-check
     * dedupes instead of duplicating storage.
     */
    fun uniqueRemoteName(originalName: String, mediaId: Long, size: Long): String {
        val dot = originalName.lastIndexOf('.')
        val stem = if (dot > 0) originalName.substring(0, dot) else originalName
        val ext = if (dot > 0) originalName.substring(dot) else ""
        val frag = "%08x".format((mediaId * 31 + size).toString().hashCode().toLong() and 0xFFFFFFFFL).take(6)
        return "${stem}_dfc$frag$ext"
    }

    /**
     * True when the server already holds this exact file: same name in the
     * destination folder AND same byte size. Lets the worker skip re-uploads
     * after a reinstall (local index wiped, server content intact).
     */
    fun alreadyBackedUp(parentId: String, name: String, size: Long): Boolean {
        val listing = listingFor(parentId)
        return listing.any { !it.isDir && it.name == name && it.size == size }
    }

    /**
     * The folder id that already holds this file, or null when it has not been
     * sent yet. [parentPath] is where this build files things; [legacyPath] is
     * where a build before it filed them, checked second so a file uploaded
     * under the old layout is adopted where it lies rather than uploaded twice.
     * Neither path is created.
     */
    fun findHoldingFolder(
        parentPath: String,
        legacyPath: String?,
        name: String,
        size: Long,
    ): String? {
        existingFolderPath(parentPath)?.let { if (alreadyBackedUp(it, name, size)) return it }
        val legacy = legacyPath?.let { existingFolderPath(it) } ?: return null
        return legacy.takeIf { alreadyBackedUp(it, name, size) }
    }

    // ---- renditions & downloads -------------------------------------------

    /** URL of the 320px server-side thumbnail rendition. */
    fun thumbUrl(fileId: String): String =
        "${base()}/api/thumb?file_id=${urlEnc(fileId)}"

    /** URL of the ~1600px screen-sized preview rendition. */
    fun previewUrl(fileId: String): String =
        "${base()}/api/preview?file_id=${urlEnc(fileId)}"

    /** URL of the raw file stream (range-request capable, for video). */
    fun streamUrl(fileId: String): String =
        "${base()}/api/download/file?file_id=${urlEnc(fileId)}"

    // ---- auth helpers ------------------------------------------------------

    /** Cheap check used by Setup screen: verifies token scope against /api/status. */
    fun verify(): Pair<Boolean, String> {
        return try {
            val json = status()
            val ok = json.optBoolean("ok")
            if (ok) true to "Connected. The drive holds ${json.optInt("files_count")} files."
            else false to "Server replied but not ok"
        } catch (e: Exception) {
            Log.w("DfcApi", "verify failed", e)
            false to (e.message ?: "connection failed")
        }
    }

    /**
     * Soft delete to the server's trash. The route takes a list; a single id
     * still goes through as a one-element list so both paths behave the same.
     */
    fun deleteFiles(fileIds: List<String>): Pair<Int, Int> {
        if (fileIds.isEmpty()) return 0 to 0
        val payload = JSONObject().put("file_ids", JSONArray(fileIds))
        val body = payload.toString().toRequestBody("application/json".toMediaType())
        val resp = client.newCall(req("${base()}/api/delete").post(body).build()).execute()
        resp.use {
            val json = bodyJson(it)
            return json.optInt("deleted") to json.optInt("failed")
        }
    }

    /**
     * Rename one file or folder. The server basenames the input, so a path
     * smuggled in the name cannot move anything.
     */
    fun renameFile(id: String, newName: String): Boolean {
        val payload = JSONObject().put("id", id).put("name", newName)
        val body = payload.toString().toRequestBody("application/json".toMediaType())
        val resp = client.newCall(req("${base()}/api/files/rename").post(body).build()).execute()
        resp.use { return it.isSuccessful }
    }

    /** Everything currently in the drive's trash, newest trashed first. */
    fun listTrash(): List<RemoteFile> {
        val resp = client.newCall(req("${base()}/api/trash").get().build()).execute()
        resp.use {
            val json = bodyJson(it)
            val arr = json.optJSONArray("files") ?: JSONArray()
            return (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                RemoteFile(
                    id = o.getString("id"),
                    parentId = o.optString("parent_id"),
                    name = o.getString("name"),
                    size = o.optLong("size"),
                    isDir = o.optBoolean("is_dir"),
                    modTime = o.optLong("mod_time"),
                    mimeType = o.optString("mime_type"),
                )
            }
        }
    }

    /** Move items out of the trash, back where they were. */
    fun restoreFiles(ids: List<String>): Boolean {
        if (ids.isEmpty()) return true
        val payload = JSONObject().put("ids", JSONArray(ids))
        val body = payload.toString().toRequestBody("application/json".toMediaType())
        val resp = client.newCall(req("${base()}/api/files/restore").post(body).build()).execute()
        resp.use { return it.isSuccessful }
    }

    /**
     * Destroy trashed items for good. Empty [ids] empties the whole trash.
     * The server queues the stored bytes for deletion and reconciles in the
     * background, so this returns once the catalog rows are gone.
     */
    fun purgeTrash(ids: List<String>): Boolean {
        val payload = if (ids.isEmpty()) JSONObject() else JSONObject().put("ids", JSONArray(ids))
        val body = payload.toString().toRequestBody("application/json".toMediaType())
        val resp = client.newCall(req("${base()}/api/trash/purge").post(body).build()).execute()
        resp.use { return it.isSuccessful }
    }

    /**
     * Stream one file to [dest]. Range-less full download; the caller owns
     * progress UI. Returns true when the bytes landed completely.
     */
    fun downloadToFile(fileId: String, dest: java.io.File): Boolean {
        java.io.FileOutputStream(dest).use { downloadTo(fileId, it) }
        return dest.length() > 0
    }

    /** Stream one file into [out]; caller owns the stream. */
    fun downloadTo(fileId: String, out: java.io.OutputStream) {
        val request = req(streamUrl(fileId)).get().build()
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) throw ApiException("HTTP ${resp.code}")
            val source = resp.body?.byteStream() ?: throw ApiException("empty response")
            source.use { input -> input.copyTo(out, 64 * 1024) }
        }
    }

    /**
     * SHA-256 of the file's bytes. Content identity, not name+size: an edited
     * photo that kept its size used to be skipped as "already backed up".
     */
    fun contentHash(context: android.content.Context, uri: android.net.Uri): String? {
        return try {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        context.contentResolver.openInputStream(uri)?.use { input ->
            val buffer = ByteArray(256 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        } ?: return null
            digest.digest().joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            Log.w("DfcApi", "content hash failed", e)
            null
        }
    }

    /** Is this exact content already on the drive? Returns the file id, or null. */
    fun findByContent(hash: String): String? {
        if (hash.length != 64) return null
        val resp = client.newCall(
            req("${base()}/api/files/by_content?hash=$hash").get().build()
        ).execute()
        resp.use {
            val json = bodyJson(it)
            if (!json.optBoolean("found")) return null
            val id = json.optString("id")
            return id.ifBlank { null }
        }
    }

    // ---- resumable uploads -----------------------------------------------

    /** Open an upload session for [size] bytes. Returns the session id. */
    fun openUploadSession(name: String, size: Long, parentId: String): String? {
        val payload = JSONObject()
            .put("name", name)
            .put("size", size)
            .put("parent_id", parentId)
        val body = payload.toString().toRequestBody("application/json".toMediaType())
        val resp = client.newCall(req("${base()}/api/uploads/session").post(body).build()).execute()
        resp.use { return bodyJson(it).optString("id").ifBlank { null } }
    }

    /**
     * How many bytes the server already holds for a freshly opened session.
     *
     * Always zero, and deliberately not a request. The session route serves PUT
     * only, so a GET is answered with 405 — which the run counted as a network
     * failure and aborted the entire batch after three files. A session this
     * client just opened has no bytes on the server by construction; the offset
     * that matters is the one each PUT returns, and a rejected chunk recovers
     * the server's expectation from the 416 body (see [putUploadChunk]).
     */
    fun uploadSessionOffset(sessionId: String): Long = 0L

    /**
     * Send one chunk at [offset]. Returns the server's new total, or -1 on
     * failure. A 416 carries the offset the server actually expects, so a
     * desynced client resumes from there instead of restarting the file.
     */
    fun putUploadChunk(sessionId: String, offset: Long, data: ByteArray, length: Int): Long {
        val body = data.copyOfRange(0, length)
            .toRequestBody("application/octet-stream".toMediaType())
        val req = req("${base()}/api/uploads/session/$sessionId")
            .put(body)
            .header("Content-Range", "bytes $offset-${offset + length - 1}/")
            .build()
        val resp = client.newCall(req).execute()
        resp.use { r ->
            if (r.isSuccessful) return bodyJson(r).optLong("received", -1L)
            // 416 = "a gap would corrupt the file"; its body names the offset the
            // server wants. Returning it lets the caller seek and carry on.
            if (r.code == 416) {
                val text = r.body?.string().orEmpty()
                Regex("""expected offset (\d+)""")
                    .find(text)?.groupValues?.get(1)?.toLongOrNull()?.let { return it }
            }
            return -1L
        }
    }

    /** Finish a complete session; returns the publish job id. */
    fun commitUploadSession(sessionId: String): Boolean {
        val resp = client.newCall(
            req("${base()}/api/uploads/session/commit/$sessionId").post(
                ByteArray(0).toRequestBody("application/octet-stream".toMediaType())
            ).build()
        ).execute()
        resp.use { return it.isSuccessful }
    }

    /** Drop a session and its staged bytes. */
    fun abortUploadSession(sessionId: String) {
        val resp = client.newCall(
            req("${base()}/api/uploads/session/abort/$sessionId").post(
                ByteArray(0).toRequestBody("application/octet-stream".toMediaType())
            ).build()
        ).execute()
        resp.use { bodyJson(it) }
    }


    // ---- resumable uploads ------------------------------------------------

    // ---- public links (shares) --------------------------------------------

    data class Share(
        val id: String,
        val fileId: String,
        val fileName: String,
        val createdAt: Long,
        val expiresAt: Long,
        val downloads: Long,
        val expired: Boolean,
    )

    /**
     * Create a public link for one file. The server returns the token exactly
     * once; it is never recoverable afterwards, so callers must show the URL
     * immediately or it is lost.
     */
    fun createShare(fileId: String, ttlDays: Int = 7): String {
        val payload = JSONObject()
            .put("file_id", fileId)
            .put("expires_in_seconds", ttlDays * 24 * 3600)
        val body = payload.toString().toRequestBody("application/json".toMediaType())
        val resp = client.newCall(req("${base()}/api/shares/create").post(body).build()).execute()
        resp.use { return bodyJson(it).getString("url") }
    }

    /** Every active link, newest first, with its file name and download count. */
    fun listShares(): List<Share> {
        val resp = client.newCall(req("${base()}/api/shares/list_all").get().build()).execute()
        resp.use {
            val json = bodyJson(it)
            val arr = json.optJSONArray("shares") ?: JSONArray()
            return (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Share(
                    id = o.getString("id"),
                    fileId = o.optString("file_id"),
                    fileName = o.optString("file_name"),
                    createdAt = o.optLong("created_at"),
                    expiresAt = o.optLong("expires_at"),
                    downloads = o.optLong("downloads"),
                    expired = o.optBoolean("expired"),
                )
            }
        }
    }

    fun revokeShare(shareId: String) {
        val payload = JSONObject().put("id", shareId)
        val body = payload.toString().toRequestBody("application/json".toMediaType())
        val resp = client.newCall(req("${base()}/api/shares/revoke").post(body).build()).execute()
        resp.use { bodyJson(it) }
    }

    fun fetchBytes(url: String): ByteArray {
        val resp = client.newCall(req(url).get().build()).execute()
        resp.use {
            if (!it.isSuccessful) throw ApiException("HTTP ${it.code}")
            return it.body?.bytes() ?: throw ApiException("empty body")
        }
    }

    /**
     * Raw authed call builder for the loopback media proxy: the player-facing
     * socket cannot set headers, so the proxy reuses this pooled client (which
     * already carries the token) to stream bytes with range support.
     */
    fun authedCall(url: String, range: String?): okhttp3.Call {
        val builder = req(url).get()
        if (!range.isNullOrBlank()) builder.header("Range", range)
        return client.newCall(builder.build())
    }

    /**
     * Server totals for the storage card. Both figures are measured by the
     * server: the bytes it actually holds and the file rows it has. There is no
     * quota on a node-backed drive, so the UI must not invent a capacity.
     */
    data class Stats(
        val filesCount: Int,
        val totalBytes: Long,
    )

    fun stats(): Stats {
        val json = status()
        return Stats(
            filesCount = json.optInt("files_count"),
            totalBytes = json.optLong("total_storage_bytes"),
        )
    }

    private fun bodyText(resp: okhttp3.Response): String {
        val text = resp.body?.string() ?: throw ApiException("empty response")
        if (!resp.isSuccessful) throw ApiException("HTTP ${resp.code}: ${text.take(200)}")
        return text
    }

    private fun bodyJson(resp: okhttp3.Response): JSONObject {
        val text = resp.body?.string() ?: throw ApiException("empty response")
        if (!resp.isSuccessful) {
            // Server errors come back as {"ok":false,"error":"..."} or plain text.
            val detail = try { JSONObject(text).optString("error") } catch (e: Exception) { "" }
            throw ApiException("HTTP ${resp.code}${if (detail.isNotBlank()) ": $detail" else ""}")
        }
        return JSONObject(text)
    }

    private fun urlEnc(v: String): String = java.net.URLEncoder.encode(v, "UTF-8")

    companion object {
        /** Everything this app uploads lives under one folder on the drive. */
        const val LEGACY_ROOT = "Mobile Backup"

        private val LEGACY_DAY = Regex("""\d{4}-\d{2}-\d{2}""")

        /**
         * Exchange the drive password for THIS device's own token. The server
         * verifies the password (rate-limited, Argon2id) and stores only a
         * hash; the plaintext token is in the response exactly once. Returns
         * (token, deviceId, null) on success or (null, null, error) on failure.
         *
         * The registration route is behind the write scope, and a phone that has
         * never signed in has no credential at all — so asking for a token first
         * is answered with "missing or invalid API token", which is what made
         * password sign-in impossible on a fresh install. The unlock route is
         * public and takes the same password, and its session cookie carries
         * write scope, so the two calls are made in order: unlock to prove the
         * password and obtain a session, then register with that session.
         */
        fun registerDevice(
            baseUrl: String,
            password: String,
            deviceName: String,
        ): Triple<String?, String?, String?> {
            val regClient = OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .build()
            val root = baseUrl.trimEnd('/')

            // 1. Unlock. Public route; verifies the password and hands back the
            //    session cookie that authorises the registration below.
            val session = try {
                val body = JSONObject().put("password", password)
                    .toString().toRequestBody("application/json".toMediaType())
                regClient.newCall(
                    Request.Builder().url("$root/api/auth/unlock").post(body).build()
                ).execute().use { resp ->
                    val text = resp.body?.string().orEmpty()
                    val json = runCatching { JSONObject(text) }.getOrNull()
                    if (!resp.isSuccessful || json?.optBoolean("ok") != true) {
                        return Triple(
                            null, null,
                            json?.optString("error")?.takeIf { it.isNotBlank() }
                                ?: "the drive refused the password",
                        )
                    }
                    resp.header("Set-Cookie")
                        ?.substringAfter("dfc_session=", "")
                        ?.substringBefore(';')
                        ?.takeIf { it.isNotBlank() }
                }
            } catch (e: Exception) {
                Log.w("DfcApi", "unlock failed", e)
                return Triple(null, null, e.message ?: "connection failed")
            }

            // 2. Register this device, carrying the session from step 1.
            val payload = JSONObject()
                .put("name", deviceName)
                .put("password", password)
            val body = payload.toString().toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url("$root/api/devices/register")
                .post(body)
                .apply { if (session != null) header("Cookie", "dfc_session=$session") }
                .build()
            return try {
                regClient.newCall(request).execute().use { resp ->
                    val json = JSONObject(resp.body?.string() ?: "{}")
                    if (resp.isSuccessful && json.optBoolean("ok")) {
                        Triple(
                            json.optString("token"),
                            json.optString("device_id"),
                            null,
                        )
                    } else {
                        Triple(null, null, json.optString("error").ifEmpty {
                            "the drive refused the registration"
                        })
                    }
                }
            } catch (e: Exception) {
                Log.w("DfcApi", "device registration failed", e)
                Triple(null, null, e.message ?: "connection failed")
            }
        }

        /**
         * A folder name the drive will accept, made from a phone album label.
         * Album names come from MediaStore buckets and from folder paths, so a
         * slash, a control character, or a 200-character name would otherwise
         * ask the server for nested or invalid folders. The result is never
         * empty: an unnameable album files under "Other" rather than nowhere.
         */
        fun safeFolderName(album: String): String {
            val cleaned = album.trim().map { c ->
                if (c.isISOControl() || c in ILLEGAL_NAME_CHARS) '-' else c
            }.joinToString("").trim().trim('.', ' ').take(MAX_FOLDER_NAME)
            return cleaned.ifEmpty { "Other" }
        }

        private const val MAX_FOLDER_NAME = 60
        private const val ILLEGAL_NAME_CHARS = "/\\:*?\"<>|"

        @Volatile private var instance: DfcApi? = null

        fun get(context: Context): DfcApi =
            instance ?: synchronized(this) {
                instance ?: DfcApi(Prefs.get(context)).also { instance = it }
            }
    }
}
