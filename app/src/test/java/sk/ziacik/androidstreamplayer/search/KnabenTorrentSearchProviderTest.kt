package sk.ziacik.androidstreamplayer.search

import java.util.ArrayDeque
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
    fun `legacy text search sends a safe movie and tv query sorted by seeders`() = runTest {
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
    fun `text search maps Knaben hits to torrent results and infers quality`() = runTest {
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

    @Test
    fun `movie search uses ordered unique fallbacks and movie category only`() = runTest {
        val transport = QueueTorrentSearchTransport(
            List(4) { TorrentSearchHttpResponse(200, """{"hits":[]}""") },
        )
        val provider = KnabenTorrentSearchProvider(transport = transport)

        provider.search(
            MovieTorrentSearchRequest(
                tmdbId = 129,
                imdbId = "tt0245429",
                title = "Spirited Away",
                originalTitle = "Sen to Chihiro no kamikakushi",
                year = 2001,
            ),
        )

        assertEquals(
            listOf(
                "Sen to Chihiro no kamikakushi 2001",
                "Spirited Away 2001",
                "Sen to Chihiro no kamikakushi",
                "Spirited Away",
            ),
            transport.queries(),
        )
        transport.requests.forEach { request ->
            val categories = JSONObject(request.bodyUtf8()).getJSONArray("categories")
            assertEquals(listOf(3_000_000), List(categories.length()) { categories.getInt(it) })
        }
    }

    @Test
    fun `movie search skips duplicate title fallbacks`() = runTest {
        val transport = QueueTorrentSearchTransport(
            List(2) { TorrentSearchHttpResponse(200, """{"hits":[]}""") },
        )
        val provider = KnabenTorrentSearchProvider(transport = transport)

        provider.search(
            MovieTorrentSearchRequest(
                tmdbId = 603,
                imdbId = "tt0133093",
                title = "The Matrix",
                originalTitle = "The Matrix",
                year = 1999,
            ),
        )

        assertEquals(listOf("The Matrix 1999", "The Matrix"), transport.queries())
    }

    @Test
    fun `movie fallback results deduplicate by info hash and sort by seeders`() = runTest {
        val transport = QueueTorrentSearchTransport(
            listOf(
                TorrentSearchHttpResponse(
                    200,
                    """
                        {"hits":[
                          {
                            "id":"abc-old",
                            "title":"The.Matrix.1999.1080p",
                            "magnetUrl":"magnet:?xt=urn:btih:ABC123&dn=Matrix",
                            "bytes":8000000000,
                            "seeders":20
                          }
                        ]}
                    """.trimIndent(),
                ),
                TorrentSearchHttpResponse(
                    200,
                    """
                        {"hits":[
                          {
                            "id":"abc-better",
                            "title":"The.Matrix.1999.1080p.BluRay",
                            "magnetUrl":"magnet:?dn=Matrix&xt=urn:btih:abc123",
                            "bytes":9000000000,
                            "seeders":40
                          },
                          {
                            "id":"def",
                            "title":"The.Matrix.1999.2160p",
                            "magnetUrl":"magnet:?xt=urn:btih:DEF456&dn=Matrix",
                            "bytes":18000000000,
                            "seeders":100
                          }
                        ]}
                    """.trimIndent(),
                ),
            ),
        )
        val provider = KnabenTorrentSearchProvider(transport = transport)

        val results = provider.search(
            MovieTorrentSearchRequest(
                tmdbId = 603,
                imdbId = "tt0133093",
                title = "The Matrix",
                originalTitle = "The Matrix",
                year = 1999,
            ),
        )

        assertEquals(listOf("def", "abc-better"), results.map { it.id })
        assertEquals(listOf(100, 40), results.map { it.seeders })
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

    private class QueueTorrentSearchTransport(
        responses: List<TorrentSearchHttpResponse>,
    ) : TorrentSearchHttpTransport {
        private val responses = ArrayDeque(responses)
        val requests = mutableListOf<Request>()

        override suspend fun execute(request: Request): TorrentSearchHttpResponse {
            requests += request
            return responses.removeFirst()
        }

        fun queries(): List<String> = requests.map { request ->
            JSONObject(request.bodyUtf8()).getString("query")
        }
    }

    private fun Request.bodyUtf8(): String {
        val buffer = Buffer()
        requireNotNull(body).writeTo(buffer)
        return buffer.readUtf8()
    }
}
