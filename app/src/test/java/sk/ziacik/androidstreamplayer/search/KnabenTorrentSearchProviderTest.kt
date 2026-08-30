package sk.ziacik.androidstreamplayer.search

import kotlinx.coroutines.test.runTest
import okhttp3.Request
import okio.Buffer
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KnabenTorrentSearchProviderTest {
    @Test
    fun `search sends a safe movie and tv query sorted by seeders`() = runTest {
        val transport = RecordingTorrentSearchTransport(
            response = TorrentSearchHttpResponse(
                code = 200,
                body = """{"hits":[]}""",
            ),
        )
        val provider = KnabenTorrentSearchProvider(transport = transport)

        provider.search("Dune")

        val request = assertNotNull(transport.request).let { transport.request!! }
        assertEquals("POST", request.method)
        assertEquals("https://api.knaben.org/v1", request.url.toString())

        val body = JSONObject(request.bodyUtf8())
        assertEquals("Dune", body.getString("query"))
        assertEquals("seeders", body.getString("order_by"))
        assertEquals("desc", body.getString("order_direction"))
        assertTrue(body.getBoolean("hide_unsafe"))
        assertTrue(body.getBoolean("hide_xxx"))
        assertEquals(50, body.getInt("size"))

        val categories = body.getJSONArray("categories")
        assertEquals(listOf(3_000_000, 2_000_000), List(categories.length()) { categories.getInt(it) })
    }

    @Test
    fun `search maps Knaben hits to torrent results and infers quality`() = runTest {
        val transport = RecordingTorrentSearchTransport(
            response = TorrentSearchHttpResponse(
                code = 200,
                body = """
                    {
                      "hits": [
                        {
                          "id": "hit-1",
                          "title": "Dune.Part.Two.2024.2160p.WEB-DL.DDP5.1.H.265",
                          "magnetUrl": "magnet:?xt=urn:btih:ABC123&dn=Dune.Part.Two",
                          "bytes": 18790000000,
                          "seeders": 184,
                          "cachedOrigin": "The Pirate Bay"
                        }
                      ]
                    }
                """.trimIndent(),
            ),
        )
        val provider = KnabenTorrentSearchProvider(transport = transport)

        val results = provider.search("Dune Part Two")

        assertEquals(1, results.size)
        assertEquals(
            TorrentSearchResult(
                id = "hit-1",
                title = "Dune.Part.Two.2024.2160p.WEB-DL.DDP5.1.H.265",
                magnetUri = "magnet:?xt=urn:btih:ABC123&dn=Dune.Part.Two",
                quality = "2160p",
                sizeBytes = 18_790_000_000,
                seeders = 184,
                source = "The Pirate Bay",
            ),
            results.single(),
        )
    }

    private class RecordingTorrentSearchTransport(
        private val response: TorrentSearchHttpResponse,
    ) : TorrentSearchHttpTransport {
        var request: Request? = null

        override suspend fun execute(request: Request): TorrentSearchHttpResponse {
            this.request = request
            return response
        }
    }

    private fun Request.bodyUtf8(): String {
        val buffer = Buffer()
        requireNotNull(body).writeTo(buffer)
        return buffer.readUtf8()
    }
}
