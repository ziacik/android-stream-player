package sk.ziacik.androidstreamplayer.catalog

import kotlinx.coroutines.test.runTest
import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TmdbMovieCatalogTest {
    @Test
    fun `search requests movie catalog without adult content and maps results`() = runTest {
        val transport = RecordingTmdbTransport(
            response = TmdbHttpResponse(
                code = 200,
                body = """
                    {
                      "results": [
                        {
                          "id": 603,
                          "title": "The Matrix",
                          "original_title": "The Matrix",
                          "release_date": "1999-03-30",
                          "overview": "A hacker discovers reality is a simulation.",
                          "vote_average": 8.2,
                          "poster_path": "/poster.jpg",
                          "backdrop_path": "/backdrop.jpg"
                        }
                      ]
                    }
                """.trimIndent(),
            ),
        )
        val catalog = TmdbMovieCatalog(apiKey = "test-key", transport = transport)

        val result = catalog.search("Matrix")

        assertEquals(603, result.single().tmdbId)
        assertEquals(1999, result.single().releaseYear)
        val request = checkNotNull(transport.request)
        assertEquals("GET", request.method)
        assertEquals("Matrix", request.url.queryParameter("query"))
        assertEquals("false", request.url.queryParameter("include_adult"))
        assertEquals("en-US", request.url.queryParameter("language"))
        assertEquals("test-key", request.url.queryParameter("api_key"))
    }

    @Test
    fun `external ids returns imdb id`() = runTest {
        val transport = RecordingTmdbTransport(
            response = TmdbHttpResponse(
                code = 200,
                body = """{"imdb_id":"tt0133093"}""",
            ),
        )
        val catalog = TmdbMovieCatalog(apiKey = "test-key", transport = transport)

        assertEquals("tt0133093", catalog.externalIds(603).imdbId)
        assertEquals("/3/movie/603/external_ids", checkNotNull(transport.request).url.encodedPath)
    }

    @Test
    fun `missing optional movie metadata stays valid`() = runTest {
        val transport = RecordingTmdbTransport(
            response = TmdbHttpResponse(
                code = 200,
                body = """{"results":[{"id":1,"title":"X","original_title":"X"}]}""",
            ),
        )

        val movie = TmdbMovieCatalog(apiKey = "test-key", transport = transport)
            .search("X")
            .single()

        assertNull(movie.releaseYear)
        assertNull(movie.overview)
        assertNull(movie.voteAverage)
        assertNull(movie.posterPath)
        assertNull(movie.backdropPath)
    }

    private class RecordingTmdbTransport(
        private val response: TmdbHttpResponse,
    ) : TmdbHttpTransport {
        var request: Request? = null

        override suspend fun execute(request: Request): TmdbHttpResponse {
            this.request = request
            return response
        }
    }
}
