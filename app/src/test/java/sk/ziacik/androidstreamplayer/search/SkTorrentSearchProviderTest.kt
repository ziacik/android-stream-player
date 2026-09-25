package sk.ziacik.androidstreamplayer.search

import kotlinx.coroutines.test.runTest
import okhttp3.HttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import sk.ziacik.androidstreamplayer.catalog.MediaType

class SkTorrentSearchProviderTest {
    @Test
    fun `login session searches movies and maps infohash to playable magnet`() = runTest {
        val hash = "99e9e4198403b74f43a5f934df1409ba447bedec"
        val session = FakeSession(
            getResponses = ArrayDeque(
                listOf(
                    authenticatedPage(),
                    searchPage(
                        hash = hash,
                        title = "Drama / Lóve 2 (2024)(SK)[1080p] = CSFD 55%",
                        size = "3.8 GB",
                        seeders = 12,
                    ),
                ),
            ),
        )
        val credentials = SkTorrentCredentials("tester", "p%a&ss^word")
        val provider = SkTorrentSearchProvider(
            credentialsStore = FixedCredentialsStore(credentials),
            session = session,
        )

        val results = provider.search(movieRequest("Lóve 2"))

        assertEquals(1, session.postedForms.size)
        assertEquals(credentials.username, session.postedForms.single().second["uid"])
        assertEquals(credentials.password, session.postedForms.single().second["pwd"])

        val searchUrl = session.getUrls.last()
        assertEquals("Lóve 2", searchUrl.queryParameter("search"))
        assertEquals("0", searchUrl.queryParameter("active"))
        assertTrue(searchUrl.queryParameter("category")!!.contains("1"))

        val result = results.single()
        assertEquals(hash, result.id)
        assertEquals("Lóve 2 (2024)(SK)[1080p]", result.title)
        assertEquals(12, result.seeders)
        assertEquals((3.8 * 1024 * 1024 * 1024).toLong(), result.sizeBytes)
        assertEquals("SkTorrent", result.source)
        assertTrue(result.magnetUri.startsWith("magnet:?xt=urn:btih:$hash"))
        assertTrue(result.magnetUri.contains("announce.sktorrent.eu"))
    }

    @Test
    fun `authenticated session is reused and fallback results deduplicate by infohash`() = runTest {
        val hash = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
        val session = FakeSession(
            getResponses = ArrayDeque(
                listOf(
                    authenticatedPage(),
                    searchPage(hash, "Film (2020)(CZ)[1080p]", "1.0 GB", 5),
                    searchPage(hash, "Film (2020)(CZ)[1080p]", "1.0 GB", 20),
                    searchPage(hash, "Film (2020)(CZ)[1080p]", "1.0 GB", 20),
                    searchPage(hash, "Film (2020)(CZ)[1080p]", "1.0 GB", 20),
                ),
            ),
        )
        val provider = SkTorrentSearchProvider(
            credentialsStore = FixedCredentialsStore(SkTorrentCredentials("u", "p")),
            session = session,
        )
        val request = MovieTorrentSearchRequest(
            tmdbId = 1,
            imdbId = null,
            title = "Film",
            originalTitle = "Original Film",
            year = null,
        )

        val first = provider.search(request)
        val second = provider.search(request)

        assertEquals(1, session.postedForms.size)
        assertEquals(1, first.size)
        assertEquals(20, first.single().seeders)
        assertEquals(1, second.size)
    }

    @Test
    fun `episode search uses TV categories and SxxExx query`() = runTest {
        val hash = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
        val session = FakeSession(
            getResponses = ArrayDeque(
                listOf(
                    authenticatedPage(),
                    searchPage(hash, "Za sklom S02E08 (SK)[720p]", "900 MB", 8),
                    emptySearchPage(),
                    emptySearchPage(),
                    emptySearchPage(),
                    emptySearchPage(),
                    emptySearchPage(),
                ),
            ),
        )
        val provider = SkTorrentSearchProvider(
            credentialsStore = FixedCredentialsStore(SkTorrentCredentials("u", "p")),
            session = session,
        )

        provider.search(
            MovieTorrentSearchRequest(
                tmdbId = 123,
                imdbId = null,
                title = "Episode 8",
                originalTitle = "Episode 8",
                year = 2018,
                mediaType = MediaType.EPISODE,
                seriesTitle = "Za sklom",
                originalSeriesTitle = "Za sklom",
                seasonNumber = 2,
                episodeNumber = 8,
            ),
        )

        val firstSearch = session.getUrls.first { it.encodedPath.endsWith("/torrents.php") }
        assertEquals("Za sklom S02E08", firstSearch.queryParameter("search"))
        val categories = firstSearch.queryParameter("category").orEmpty()
        assertTrue(categories.contains("16"))
        assertTrue(!categories.contains("1;"))
    }

    private fun movieRequest(title: String) = MovieTorrentSearchRequest(
        tmdbId = 0,
        imdbId = null,
        title = title,
        originalTitle = title,
        year = null,
    )

    private fun authenticatedPage() =
        """<html><body><a href="usercp.php">Profile</a></body></html>"""

    private fun emptySearchPage() =
        """<html><body><a href="usercp.php">Profile</a><table></table></body></html>"""

    private fun searchPage(
        hash: String,
        title: String,
        size: String,
        seeders: Int,
    ) = """
        <html><body>
        <a href="usercp.php">Profile</a>
        <table><tr>
          <td><a href="torrents.php?category=16">category</a></td>
          <td>
            <a href="details.php?id=$hash" title="$title">title</a>
            <a href="download.php?id=$hash">download</a>
          </td>
          <td>Velkost $size |</td>
          <td>x</td>
          <td>$seeders</td>
          <td>0</td>
        </tr></table>
        </body></html>
    """.trimIndent()

    private class FixedCredentialsStore(
        private val credentials: SkTorrentCredentials?,
    ) : SkTorrentCredentialsStore {
        override fun load(): SkTorrentCredentials? = credentials
        override fun save(credentials: SkTorrentCredentials) = Unit
        override fun clear() = Unit
    }

    private class FakeSession(
        private val getResponses: ArrayDeque<String>,
    ) : SkTorrentHttpSession {
        val getUrls = mutableListOf<HttpUrl>()
        val postedForms = mutableListOf<Pair<HttpUrl, Map<String, String>>>()
        var clearCount = 0

        override suspend fun get(url: HttpUrl): SkTorrentHttpResponse {
            getUrls += url
            return SkTorrentHttpResponse(200, getResponses.removeFirst())
        }

        override suspend fun postForm(
            url: HttpUrl,
            fields: Map<String, String>,
        ): SkTorrentHttpResponse {
            postedForms += url to fields
            return SkTorrentHttpResponse(200, """<html><body><a href="usercp.php">Profile</a></body></html>""")
        }

        override fun clearCookies() {
            clearCount += 1
        }
    }
}
