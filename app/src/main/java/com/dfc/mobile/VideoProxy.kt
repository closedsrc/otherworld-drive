package com.dfc.mobile

import android.content.Context
import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/**
 * Loopback media proxy, the same architecture the desktop client uses
 * (its `proxy_base`): external players cannot set the X-API-Token header,
 * so handing them the raw https URL only ever 401s. Instead they get a
 * plain http://127.0.0.1 URL on this device; the proxy re-issues the
 * request upstream with the token attached and relays bytes — including
 * Range requests, so seeking works instead of restarting the stream.
 *
 * The URL carries a one-time capability token, not a file id. Every other
 * app on the phone can probe 127.0.0.1, so an URL that said "stream any
 * file by id" would let a hostile neighbour read the whole drive through
 * this process's credential. A capability is minted per player session,
 * resolves to exactly one file, and expires — an app that guesses or steals
 * a URL gets one file it was already handed, for a few minutes.
 */
object VideoProxy {

    private const val TAG = "VideoProxy"
    private const val CAPABILITY_TTL_MS = 15 * 60 * 1000L

    @Volatile private var port: Int = 0
    private val startLock = Any()

    private class Capability(val fileId: String, val expiresAt: Long)

    private val capabilities = ConcurrentHashMap<String, Capability>()
    private val random = SecureRandom()

    /** Player-ready URL for one server file. Starts the proxy on first use. */
    fun url(context: Context, fileId: String): String {
        ensureStarted(context.applicationContext)
        val bytes = ByteArray(32)
        random.nextBytes(bytes)
        val cap = bytes.joinToString("") { "%02x".format(it) }
        capabilities[cap] = Capability(fileId, System.currentTimeMillis() + CAPABILITY_TTL_MS)
        return "http://127.0.0.1:$port/stream?cap=$cap"
    }

    /** Drop expired capabilities so the map cannot grow without bound. */
    private fun resolve(cap: String): String? {
        val now = System.currentTimeMillis()
        capabilities.values.removeIf { it.expiresAt < now }
        return capabilities[cap]?.takeIf { it.expiresAt >= now }?.fileId
    }

    private fun encode(v: String): String = java.net.URLEncoder.encode(v, "UTF-8")

    private fun ensureStarted(appContext: Context) {
        if (port != 0) return
        synchronized(startLock) {
            if (port != 0) return
            val server = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
            port = server.localPort
            val pool = Executors.newCachedThreadPool { r ->
                Thread(r, "video-proxy").apply { isDaemon = true }
            }
            Thread({
                while (!server.isClosed) {
                    try {
                        val socket = server.accept()
                        pool.execute { serve(appContext, socket) }
                    } catch (e: Exception) {
                        Log.w(TAG, "accept failed", e)
                        break
                    }
                }
            }, "video-proxy-accept").apply { isDaemon = true; start() }
            Log.i(TAG, "listening on 127.0.0.1:$port")
        }
    }

    private fun serve(appContext: Context, socket: Socket) {
        try {
            socket.use { s ->
                val reader = BufferedReader(InputStreamReader(s.getInputStream(), Charsets.US_ASCII))
                val requestLine = reader.readLine() ?: return
                // GET /stream?cap=... HTTP/1.1
                val parts = requestLine.split(' ')
                if (parts.size < 2 || !parts[0].equals("GET", ignoreCase = true)) {
                    writeStatus(s, 405, "Method Not Allowed", "only GET is served")
                    return
                }
                val cap = paramOf(parts[1], "cap")
                val fileId = cap?.let { resolve(it) }
                if (fileId.isNullOrBlank()) {
                    writeStatus(s, 403, "Forbidden", "this stream link is no longer valid")
                    return
                }
                var range: String? = null
                while (true) {
                    val line = reader.readLine() ?: break
                    if (line.isEmpty()) break
                    if (line.startsWith("Range:", ignoreCase = true)) {
                        range = line.substringAfter(':').trim()
                    }
                }
                relay(appContext, s, fileId, range)
            }
        } catch (e: Exception) {
            Log.w(TAG, "serve failed", e)
            runCatching { socket.close() }
        }
    }

    private fun paramOf(target: String, key: String): String? {
        val query = target.substringAfter('?', "")
        if (query.isEmpty()) return null
        for (pair in query.split('&')) {
            if (pair.substringBefore('=') == key) {
                return URLDecoder.decode(pair.substringAfter('=', ""), "UTF-8")
            }
        }
        return null
    }

    private fun relay(appContext: Context, socket: Socket, fileId: String, range: String?) {
        val api = DfcApi.get(appContext)
        val upstream = api.authedCall(api.streamUrl(fileId), range).execute()
        upstream.use { resp ->
            val out = socket.getOutputStream()
            val body = resp.body
            if (body == null) {
                writeStatus(socket, resp.code, "Upstream error", "empty upstream body")
                return
            }
            val reason = when (resp.code) {
                200 -> "OK"
                206 -> "Partial Content"
                404 -> "Not Found"
                401, 403 -> "Unauthorized"
                else -> "Upstream error"
            }
            if (!resp.isSuccessful) {
                // Drain a short error; the player only needs the status.
                runCatching { body.close() }
                writeStatus(socket, resp.code, reason, reason)
                return
            }
            val headers = StringBuilder()
            headers.append("HTTP/1.1 ${resp.code} $reason\r\n")
            copyHeader(resp, headers, "Content-Type", "video/mp4")
            copyHeader(resp, headers, "Content-Length", null)
            copyHeader(resp, headers, "Content-Range", null)
            copyHeader(resp, headers, "Accept-Ranges", "bytes")
            headers.append("Connection: close\r\n")
            headers.append("\r\n")
            out.write(headers.toString().toByteArray(Charsets.US_ASCII))
            val buffer = ByteArray(64 * 1024)
            body.byteStream().use { input ->
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    out.write(buffer, 0, read)
                }
            }
            out.flush()
        }
    }

    private fun copyHeader(
        resp: okhttp3.Response,
        out: StringBuilder,
        name: String,
        default: String?,
    ) {
        val value = resp.header(name) ?: default ?: return
        out.append("$name: $value\r\n")
    }

    private fun writeStatus(socket: Socket, code: Int, reason: String, text: String) {
        val body = text.toByteArray(Charsets.UTF_8)
        val head = "HTTP/1.1 $code $reason\r\n" +
            "Content-Type: text/plain\r\n" +
            "Content-Length: ${body.size}\r\n" +
            "Connection: close\r\n\r\n"
        val out = socket.getOutputStream()
        out.write(head.toByteArray(Charsets.US_ASCII))
        out.write(body)
        out.flush()
    }
}
