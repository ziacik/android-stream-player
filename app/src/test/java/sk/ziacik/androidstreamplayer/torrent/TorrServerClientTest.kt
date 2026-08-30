package sk.ziacik.androidstreamplayer.torrent

import java.io.IOException
import kotlinx.coroutines.test.runTest
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TorrServerClientTest {
    @Test
    fun streamUrlIsPlayOnly() {
        val magnet = "magnet:?xt=urn:btih:abcdef&dn=Video&tr=udp://tracker.one/announce&tr=https://tracker.two/announce"
        val client = TorrServerClient(
            transport = FakeTransport(),
        )

        val url = client.streamUrl(magnet).toHttpUrl()

        assertEquals("127.0.0.1", url.host)
        assertEquals(18090, url.port)
        assertEquals("/stream/video", url.encodedPath)
        assertEquals(magnet, url.queryParameter("link"))
        assertEquals("1", url.queryParameter("index"))
        assertFalse(url.queryParameterNames.contains("preload"))
        assertTrue(url.queryParameterNames.contains("play"))
    }

    @Test
    fun prepareStreamPreloadsSelectedVideoBeforeReturningPlayUrl() = runTest {
        val magnet = "magnet:?xt=urn:btih:abcdef&dn=Video"
        val transport = FakeTransport(
            responses = mutableListOf(
                TorrServerHttpResponse(
                    code = 200,
                    body = """{"hash":"hash123","file_stats":null}""",
                ),
                TorrServerHttpResponse(
                    code = 200,
                    body = """{"hash":"hash123","file_stats":[{"id":1,"path":"README.txt","length":999999999},{"id":4,"path":"sample.mkv","length":1000},{"id":7,"path":"folder/movie.mkv","length":9000}]}""",
                ),
                TorrServerHttpResponse(code = 200, body = ""),
            ),
        )
        val client = TorrServerClient(
            transport = transport,
            pollIntervalMs = 1,
        )

        val url = client.prepareStreamUrl(magnet, timeoutMs = 1_000).toHttpUrl()

        assertEquals("/stream/movie.mkv", url.encodedPath)
        assertEquals("hash123", url.queryParameter("link"))
        assertEquals("7", url.queryParameter("index"))
        assertFalse(url.queryParameterNames.contains("preload"))
        assertTrue(url.queryParameterNames.contains("play"))

        assertEquals(3, transport.requests.size)
        assertEquals("/torrents", transport.requests[0].url.encodedPath)
        assertEquals("POST", transport.requests[0].method)
        assertEquals("/torrents", transport.requests[1].url.encodedPath)
        assertEquals("POST", transport.requests[1].method)

        val preload = transport.requests[2]
        assertEquals("GET", preload.method)
        assertEquals("/stream/movie.mkv", preload.url.encodedPath)
        assertEquals("hash123", preload.url.queryParameter("link"))
        assertEquals("7", preload.url.queryParameter("index"))
        assertTrue(preload.url.queryParameterNames.contains("preload"))
        assertFalse(preload.url.queryParameterNames.contains("play"))
    }

    @Test
    fun configureStreamingSettingsPreservesExistingSettingsAndDisablesUpload() = runTest {
        val transport = FakeTransport(
            responses = mutableListOf(
                TorrServerHttpResponse(
                    code = 200,
                    body = """{"CacheSize":67108864,"ReaderReadAHead":95,"PreloadCache":50,"UseDisk":false,"DisableUpload":false,"TorrentDisconnectTimeout":30,"ConnectionsLimit":25,"EnableDHT":true}""",
                ),
                TorrServerHttpResponse(code = 200, body = ""),
            ),
        )
        val client = TorrServerClient(transport = transport)

        client.configureStreamingSettings()

        assertEquals(2, transport.requests.size)
        val getBody = JSONObject(transport.requests[0].bodyAsString())
        assertEquals("get", getBody.getString("action"))

        val setBody = JSONObject(transport.requests[1].bodyAsString())
        assertEquals("set", setBody.getString("action"))
        val settings = setBody.getJSONObject("sets")
        assertEquals(120, settings.getInt("TorrentDisconnectTimeout"))
        assertFalse(settings.getBoolean("UseDisk"))
        assertTrue(settings.getBoolean("DisableUpload"))
        assertEquals(67108864, settings.getInt("CacheSize"))
        assertEquals(95, settings.getInt("ReaderReadAHead"))
        assertEquals(25, settings.getInt("ConnectionsLimit"))
        assertTrue(settings.getBoolean("EnableDHT"))
    }

    @Test
    fun ramCacheSettingsAcceptDiskDisabled() = runTest {
        val transport = FakeTransport(
            responses = mutableListOf(
                TorrServerHttpResponse(
                    code = 200,
                    body = "{\"UseDisk\":false,\"CacheSize\":67108864}",
                ),
            ),
        )
        val client = TorrServerClient(transport = transport)

        client.assertRamCache()

        val request = transport.requests.single()
        assertEquals("/settings", request.url.encodedPath)
        assertEquals("POST", request.method)
    }

    @Test
    fun ramCacheSettingsRejectDiskEnabled() = runTest {
        val client = TorrServerClient(
            transport = FakeTransport(
                responses = mutableListOf(
                    TorrServerHttpResponse(
                        code = 200,
                        body = "{\"UseDisk\":true,\"CacheSize\":67108864}",
                    ),
                ),
            ),
        )

        var error: IOException? = null
        try {
            client.assertRamCache()
        } catch (caught: IOException) {
            error = caught
        }

        assertNotNull(error)
        assertTrue(error?.message.orEmpty().contains("disk", ignoreCase = true))
    }

    @Test
    fun awaitReadyRetriesUntilEchoSucceeds() = runTest {
        val transport = FakeTransport(
            responses = mutableListOf(
                TorrServerHttpResponse(503, ""),
                TorrServerHttpResponse(200, ""),
                TorrServerHttpResponse(200, "MatriX.143"),
            ),
        )
        val client = TorrServerClient(
            transport = transport,
            pollIntervalMs = 1,
        )

        client.awaitReady(timeoutMs = 1_000)

        assertEquals(3, transport.requests.size)
        assertTrue(transport.requests.all { it.url.encodedPath == "/echo" })
    }

    @Test
    fun awaitReadyFailsAfterFiniteTimeout() = runTest {
        val client = TorrServerClient(
            transport = FakeTransport(
                fallback = TorrServerHttpResponse(503, ""),
            ),
            pollIntervalMs = 10,
        )

        var error: IOException? = null
        try {
            client.awaitReady(timeoutMs = 25)
        } catch (caught: IOException) {
            error = caught
        }

        assertNotNull(error)
        assertTrue(error?.message.orEmpty().contains("timeout", ignoreCase = true))
    }

    private fun Request.bodyAsString(): String {
        val buffer = okio.Buffer()
        body?.writeTo(buffer)
        return buffer.readUtf8()
    }

    private class FakeTransport(
        private val responses: MutableList<TorrServerHttpResponse> = mutableListOf(),
        private val fallback: TorrServerHttpResponse = TorrServerHttpResponse(500, ""),
    ) : TorrServerHttpTransport {
        val requests = mutableListOf<Request>()

        override suspend fun execute(request: Request): TorrServerHttpResponse {
            requests += request
            return if (responses.isNotEmpty()) responses.removeAt(0) else fallback
        }
    }
}
