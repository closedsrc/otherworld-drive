package com.dfc.mobile

import android.content.Context
import android.util.Log
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
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
    val path: String,
    val size: Long,
    val isDir: Boolean,
    val modTime: Long,
    val mimeType: String,
    val favorite: Boolean,
)

class ApiException(message: String) : IOException(message)

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
        .build()

    /** Cache of "Mobile Backup/2026-09-14" → folder id, per worker run. */
    private val folderCache = HashMap<String, String>()

    private fun base(): String = prefs.serverUrl

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
        resp.use {
            val arr = JSONArray(bodyText(it))
            return (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                RemoteFile(
                    id = o.getString("id"),
                    parentId = o.optString("parent_id"),
                    name = o.getString("name"),
                    path = o.optString("path"),
                    size = o.optLong("size"),
                    isDir = o.optBoolean("is_dir"),
                    modTime = o.optLong("mod_time"),
                    mimeType = o.optString("mime_type"),
                    favorite = o.optBoolean("favorite"),
                )
            }
        }
    }

    /** Resolve (and create when missing) "Mobile Backup/2026-09-14", cached. */
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
        listFiles(parentId).firstOrNull { it.name == name && !it.isDir }?.id

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
        val listing = listFiles(parentId)
        return listing.any { !it.isDir && it.name == name && it.size == size }
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

    fun authHeaders(): Map<String, String> = mapOf("X-API-Token" to prefs.token)

    /**
     * Server totals for the storage card. All of these are measured by the
     * server: bytes it is actually holding, the file rows it has, and the free
     * space on its own disk. There is no quota on a node-backed drive, so the
     * UI must not invent one.
     */
    data class Stats(
        val filesCount: Int,
        val totalBytes: Long,
        val diskFreeBytes: Long,
        val primaryBackend: String,
        val cloudReady: Boolean,
        val activeJobs: Int,
    )

    fun stats(): Stats {
        val json = status()
        return Stats(
            filesCount = json.optInt("files_count"),
            totalBytes = json.optLong("total_storage_bytes"),
            diskFreeBytes = json.optLong("disk_free_bytes"),
            primaryBackend = json.optString("primary_backend", "discord"),
            cloudReady = json.optBoolean("cloud_ready"),
            activeJobs = json.optInt("active_jobs_count"),
        )
    }

    // ---- mutations ---------------------------------------------------------

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

    // ---- auth helpers ------------------------------------------------------

    /** Cheap check used by Setup screen: verifies token scope against /api/status. */
    fun verify(): Pair<Boolean, String> {
        return try {
            val json = status()
            val ok = json.optBoolean("ok")
            if (ok) true to "Connected. Storage ready: ${json.optBoolean("is_configured")}"
            else false to "Server replied but not ok"
        } catch (e: Exception) {
            Log.w("DfcApi", "verify failed", e)
            false to (e.message ?: "connection failed")
        }
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
        @Volatile private var instance: DfcApi? = null

        fun get(context: Context): DfcApi =
            instance ?: synchronized(this) {
                instance ?: DfcApi(Prefs.get(context)).also { instance = it }
            }
    }
}
