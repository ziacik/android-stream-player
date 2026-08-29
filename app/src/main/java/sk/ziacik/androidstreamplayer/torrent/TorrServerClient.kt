package sk.ziacik.androidstreamplayer.torrent

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
        magnet: String,
        fileIndex: Int = 1,
    ): String = baseUrl.newBuilder()
        .addPathSegment("stream")
        .addPathSegment("video")
        .addQueryParameter("link", magnet)
        .addQueryParameter("index", fileIndex.toString())
        .addQueryParameter("preload", null)
        .addQueryParameter("play", null)
        .build()
        .toString()

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

    private fun url(pathSegment: String): HttpUrl = baseUrl.newBuilder()
        .addPathSegment(pathSegment)
        .build()

    private companion object {
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        const val SETTINGS_GET_JSON = "{\"action\":\"get\"}"
        val USE_DISK_REGEX = Regex("\\\"UseDisk\\\"\\s*:\\s*(true|false)", RegexOption.IGNORE_CASE)
    }
}
