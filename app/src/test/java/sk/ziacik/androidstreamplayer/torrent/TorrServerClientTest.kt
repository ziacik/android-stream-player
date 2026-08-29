package sk.ziacik.androidstreamplayer.torrent

import java.io.IOException
import kotlinx.coroutines.test.runTest
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TorrServerClientTest {
    @Test
    fun streamUrlKeepsWholeMagnetInsideLinkParameter() {
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
        assertTrue(url.queryParameterNames.contains("preload"))
        assertTrue(url.queryParameterNames.contains("play"))
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
