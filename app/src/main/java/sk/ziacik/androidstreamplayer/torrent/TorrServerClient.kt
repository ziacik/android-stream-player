package sk.ziacik.androidstreamplayer.torrent

import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

internal data class TorrServerHttpResponse(
    val code: Int,
    val body: String,
) {
    val isSuccessful: Boolean
        get() = code in 200..299
}

internal fun interface TorrServerHttpTransport {
    suspend fun execute(request: Request): TorrServerHttpResponse
}

internal interface TorrServerControlClient {
    suspend fun awaitReady(timeoutMs: Long = 10_000L)
    suspend fun configureStreamingSettings()
    suspend fun assertRamCache()
    suspend fun shutdown()
}

internal class OkHttpTorrServerTransport(
    private val client: OkHttpClient = OkHttpClient(),
) : TorrServerHttpTransport {
    private val preloadClient = client.newBuilder()
        .readTimeout(PRELOAD_READ_TIMEOUT_MINUTES, TimeUnit.MINUTES)
        .callTimeout(PRELOAD_READ_TIMEOUT_MINUTES, TimeUnit.MINUTES)
        .build()

    override suspend fun execute(request: Request): TorrServerHttpResponse =
        withContext(Dispatchers.IO) {
            val requestClient = if (
                request.url.queryParameterNames.contains("preload") &&
                !request.url.queryParameterNames.contains("play")
            ) {
                preloadClient
            } else {
                client
            }
            requestClient.newCall(request).execute().use { response ->
                TorrServerHttpResponse(
                    code = response.code,
                    body = response.body.string(),
                )
            }
        }

    private companion object {
        const val PRELOAD_READ_TIMEOUT_MINUTES = 10L
    }
}

internal class TorrServerClient(
    private val transport: TorrServerHttpTransport = OkHttpTorrServerTransport(),
    private val baseUrl: HttpUrl = "http://127.0.0.1:18090".toHttpUrl(),
    private val pollIntervalMs: Long = 200L,
) : TorrServerControlClient {
    fun streamUrl(
        link: String,
        fileIndex: Int = 1,
        fileName: String = "video",
    ): String = streamRequestUrl(
        link = link,
        fileIndex = fileIndex,
        fileName = fileName,
        action = "play",
    ).toString()

    suspend fun prepareStreamUrl(
        magnet: String,
        timeoutMs: Long = METADATA_TIMEOUT_MS,
        onStartupStats: (TorrentStartupStats) -> Unit = {},
    ): String {
        require(timeoutMs > 0) { "timeoutMs must be positive" }

        val added = torrentRequest(
            JSONObject()
                .put("action", "add")
                .put("link", magnet)
                .put("save_to_db", false),
        )
        if (added.hash.isBlank()) {
            throw IOException("TorrServer did not return torrent hash")
        }
        onStartupStats(added.startupStats)

        val ready: TorrServerTorrentInfo = if (added.files.isNotEmpty()) {
            added
        } else {
            awaitTorrentFiles(
                hash = added.hash,
                timeoutMs = timeoutMs,
                onStartupStats = onStartupStats,
            )
        }

        val file = ready.files
            .asSequence()
            .filter { it.path.substringAfterLast('.', "").lowercase() in VIDEO_EXTENSIONS }
            .maxByOrNull { it.length }
            ?: throw IOException("Torrent contains no playable video file")
        val fileName = File(file.path).name

        val preloadResponse = preloadWithStatus(
            hash = ready.hash,
            request = Request.Builder()
                .url(
                    streamRequestUrl(
                        link = ready.hash,
                        fileIndex = file.id,
                        fileName = fileName,
                        action = "preload",
                    ),
                )
                .get()
                .build(),
            onStartupStats = onStartupStats,
        )
        if (!preloadResponse.isSuccessful) {
            throw IOException("TorrServer preload failed with HTTP ${preloadResponse.code}")
        }

        return streamUrl(
            link = ready.hash,
            fileIndex = file.id,
            fileName = fileName,
        )
    }

    override suspend fun awaitReady(timeoutMs: Long) {
        require(timeoutMs > 0) { "timeoutMs must be positive" }

        val ready = withTimeoutOrNull(timeoutMs) {
            while (true) {
                val response = try {
                    transport.execute(
                        Request.Builder()
                            .url(url("echo"))
                            .get()
                            .build(),
                    )
                } catch (_: IOException) {
                    null
                }

                if (response?.isSuccessful == true && response.body.isNotBlank()) {
                    return@withTimeoutOrNull true
                }

                delay(pollIntervalMs)
            }
        }

        if (ready != true) {
            throw IOException("TorrServer startup timeout")
        }
    }

    override suspend fun configureStreamingSettings() {
        val currentResponse = settingsRequest(JSONObject().put("action", "get"))
        val settings = try {
            JSONObject(currentResponse.body)
        } catch (error: Exception) {
            throw IOException("Invalid TorrServer settings response", error)
        }

        settings.put("UseDisk", false)
        settings.put("DisableUpload", true)
        settings.put("TorrentDisconnectTimeout", TORRENT_DISCONNECT_TIMEOUT_SECONDS)

        settingsRequest(
            JSONObject()
                .put("action", "set")
                .put("sets", settings),
        )
    }

    override suspend fun assertRamCache() {
        val response = settingsRequest(JSONObject().put("action", "get"))
        val useDisk = try {
            JSONObject(response.body).getBoolean("UseDisk")
        } catch (error: Exception) {
            throw IOException("TorrServer settings did not include valid UseDisk", error)
        }
        if (useDisk) {
            throw IOException("TorrServer disk cache is enabled")
        }
    }

    override suspend fun shutdown() {
        try {
            transport.execute(
                Request.Builder()
                    .url(url("shutdown"))
                    .get()
                    .build(),
            )
        } catch (_: IOException) {
            // The process may close the socket while shutting itself down.
        }
    }

    private suspend fun awaitTorrentFiles(
        hash: String,
        timeoutMs: Long,
        onStartupStats: (TorrentStartupStats) -> Unit,
    ): TorrServerTorrentInfo {
        var found: TorrServerTorrentInfo? = null
        withTimeoutOrNull(timeoutMs) {
            while (found == null) {
                val torrent = torrentRequest(
                    JSONObject()
                        .put("action", "get")
                        .put("hash", hash),
                )
                onStartupStats(torrent.startupStats)
                if (torrent.files.isNotEmpty()) {
                    found = torrent
                } else {
                    delay(pollIntervalMs)
                }
            }
        }
        return found ?: throw IOException("TorrServer metadata timeout")
    }

    private suspend fun preloadWithStatus(
        hash: String,
        request: Request,
        onStartupStats: (TorrentStartupStats) -> Unit,
    ): TorrServerHttpResponse = coroutineScope {
        val preload = async {
            transport.execute(request)
        }
        val statusPollIntervalMs = maxOf(1L, pollIntervalMs * STARTUP_STATUS_POLL_FACTOR)

        while (preload.isActive) {
            delay(statusPollIntervalMs)
            if (!preload.isActive) break

            try {
                val torrent = torrentRequest(
                    JSONObject()
                        .put("action", "get")
                        .put("hash", hash),
                )
                onStartupStats(torrent.startupStats)
            } catch (_: IOException) {
                // Telemetry is best effort; the preload response remains authoritative.
            }
        }

        preload.await()
    }

    private suspend fun torrentRequest(body: JSONObject): TorrServerTorrentInfo {
        val response = transport.execute(
            Request.Builder()
                .url(url("torrents"))
                .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build(),
        )
        if (!response.isSuccessful) {
            throw IOException("TorrServer torrent request failed with HTTP ${response.code}")
        }

        return try {
            val json = JSONObject(response.body)
            val filesJson = json.optJSONArray("file_stats")
            val files = buildList {
                if (filesJson != null) {
                    for (index in 0 until filesJson.length()) {
                        val file = filesJson.getJSONObject(index)
                        add(
                            TorrServerFileInfo(
                                id = file.getInt("id"),
                                path = file.getString("path"),
                                length = file.getLong("length"),
                            ),
                        )
                    }
                }
            }
            TorrServerTorrentInfo(
                hash = json.optString("hash"),
                files = files,
                startupStats = TorrentStartupStats(
                    activePeers = json.optInt("active_peers"),
                    totalPeers = json.optInt("total_peers"),
                    connectedSeeders = json.optInt("connected_seeders"),
                    downloadSpeedBytesPerSecond = json.optDouble("download_speed", 0.0),
                    preloadedBytes = json.optLong("preloaded_bytes"),
                    preloadSizeBytes = json.optLong("preload_size"),
                ),
            )
        } catch (error: Exception) {
            throw IOException("Invalid TorrServer torrent response", error)
        }
    }

    private suspend fun settingsRequest(body: JSONObject): TorrServerHttpResponse {
        val response = transport.execute(
            Request.Builder()
                .url(url("settings"))
                .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build(),
        )
        if (!response.isSuccessful) {
            throw IOException("TorrServer settings request failed with HTTP ${response.code}")
        }
        return response
    }

    private fun streamRequestUrl(
        link: String,
        fileIndex: Int,
        fileName: String,
        action: String,
    ): HttpUrl = baseUrl.newBuilder()
        .addPathSegment("stream")
        .addPathSegment(fileName)
        .addQueryParameter("link", link)
        .addQueryParameter("index", fileIndex.toString())
        .addQueryParameter(action, null)
        .build()

    private fun url(pathSegment: String): HttpUrl = baseUrl.newBuilder()
        .addPathSegment(pathSegment)
        .build()

    private data class TorrServerTorrentInfo(
        val hash: String,
        val files: List<TorrServerFileInfo>,
        val startupStats: TorrentStartupStats,
    )

    private data class TorrServerFileInfo(
        val id: Int,
        val path: String,
        val length: Long,
    )

    private companion object {
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        const val METADATA_TIMEOUT_MS = 90_000L
        const val TORRENT_DISCONNECT_TIMEOUT_SECONDS = 120
        const val STARTUP_STATUS_POLL_FACTOR = 5L
        val VIDEO_EXTENSIONS = setOf("mkv", "mp4", "m4v", "webm", "ts")
    }
}
