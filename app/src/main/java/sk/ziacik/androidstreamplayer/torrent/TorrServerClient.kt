package sk.ziacik.androidstreamplayer.torrent

import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
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
    suspend fun assertRamCache()
    suspend fun shutdown()
}

internal class OkHttpTorrServerTransport(
    private val client: OkHttpClient = OkHttpClient(),
) : TorrServerHttpTransport {
    override suspend fun execute(request: Request): TorrServerHttpResponse =
        withContext(Dispatchers.IO) {
            client.newCall(request).execute().use { response ->
                TorrServerHttpResponse(
                    code = response.code,
                    body = response.body.string(),
                )
            }
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
    ): String = baseUrl.newBuilder()
        .addPathSegment("stream")
        .addPathSegment(fileName)
        .addQueryParameter("link", link)
        .addQueryParameter("index", fileIndex.toString())
        .addQueryParameter("preload", null)
        .addQueryParameter("play", null)
        .build()
        .toString()

    suspend fun prepareStreamUrl(
        magnet: String,
        timeoutMs: Long = 60_000L,
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

        val ready: TorrServerTorrentInfo = if (added.files.isNotEmpty()) {
            added
        } else {
            awaitTorrentFiles(added.hash, timeoutMs)
        }

        val file = ready.files
            .asSequence()
            .filter { it.path.substringAfterLast('.', "").lowercase() in VIDEO_EXTENSIONS }
            .maxByOrNull { it.length }
            ?: throw IOException("Torrent contains no playable video file")

        return streamUrl(
            link = ready.hash,
            fileIndex = file.id,
            fileName = File(file.path).name,
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

    override suspend fun assertRamCache() {
        val requestBody = SETTINGS_GET_JSON.toRequestBody(JSON_MEDIA_TYPE)
        val response = transport.execute(
            Request.Builder()
                .url(url("settings"))
                .post(requestBody)
                .build(),
        )

        if (!response.isSuccessful) {
            throw IOException("TorrServer settings request failed with HTTP ${response.code}")
        }

        val useDiskMatch = USE_DISK_REGEX.find(response.body)
            ?: throw IOException("TorrServer settings did not include UseDisk")
        val useDisk = useDiskMatch.groupValues[1].toBooleanStrictOrNull()
            ?: throw IOException("Invalid TorrServer UseDisk setting")
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
    ): TorrServerTorrentInfo {
        var found: TorrServerTorrentInfo? = null
        withTimeoutOrNull(timeoutMs) {
            while (found == null) {
                val torrent = torrentRequest(
                    JSONObject()
                        .put("action", "get")
                        .put("hash", hash),
                )
                if (torrent.files.isNotEmpty()) {
                    found = torrent
                } else {
                    delay(pollIntervalMs)
                }
            }
        }
        return found ?: throw IOException("TorrServer metadata timeout")
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
            )
        } catch (error: Exception) {
            throw IOException("Invalid TorrServer torrent response", error)
        }
    }

    private fun url(pathSegment: String): HttpUrl = baseUrl.newBuilder()
        .addPathSegment(pathSegment)
        .build()

    private data class TorrServerTorrentInfo(
        val hash: String,
        val files: List<TorrServerFileInfo>,
    )

    private data class TorrServerFileInfo(
        val id: Int,
        val path: String,
        val length: Long,
    )

    private companion object {
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        const val SETTINGS_GET_JSON = "{\"action\":\"get\"}"
        val USE_DISK_REGEX = Regex("\\\"UseDisk\\\"\\s*:\\s*(true|false)", RegexOption.IGNORE_CASE)
        val VIDEO_EXTENSIONS = setOf("mkv", "mp4", "m4v", "webm", "ts")
    }
}
