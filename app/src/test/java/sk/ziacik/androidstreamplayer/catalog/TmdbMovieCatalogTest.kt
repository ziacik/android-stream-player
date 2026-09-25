package sk.ziacik.androidstreamplayer.catalog

import java.util.ArrayDeque
import kotlinx.coroutines.test.runTest
import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TmdbMovieCatalogTest {
    @Test
    fun `search requests mixed catalog and maps movies and series`() = runTest {
        val transport = RecordingTmdbTransport(
            response = TmdbHttpResponse(
                code = 200,
                body = """
                    {
                      "results": [
                        {
                          "media_type": "movie",
                          "id": 603,
                          "title": "The Matrix",
                          "original_title": "The Matrix",
                          "release_date": "1999-03-30",
                          "overview": "A hacker discovers reality is a simulation.",
                          "vote_average": 8.2,
                          "poster_path": "/poster.jpg",
                          "backdrop_path": "/backdrop.jpg"
                        },
                        {
                          "media_type": "tv",
                          "id": 123,
                          "name": "Za sklom",
                          "original_name": "Za sklom",
                          "first_air_date": "2016-09-28",
                          "overview": "Crime series.",
                          "vote_average": 7.3
                        }
                      ]
                    }
                """.trimIndent(),
            ),
        )
        val catalog = TmdbMovieCatalog(apiKey = "test-key", transport = transport)

        val result = catalog.search("Matrix")

        assertEquals(listOf(MediaType.MOVIE, MediaType.SERIES), result.map { it.mediaType })
        assertEquals(1999, result.first().releaseYear)
        assertEquals(2016, result.last().releaseYear)
        val request = checkNotNull(transport.request)
        assertEquals("/3/search/multi", request.url.encodedPath)
        assertEquals("Matrix", request.url.queryParameter("query"))
        assertEquals("false", request.url.queryParameter("include_adult"))
        assertEquals("en-US", request.url.queryParameter("language"))
        assertEquals("test-key", request.url.queryParameter("api_key"))
    }

    @Test
    fun `trending requests weekly mixed titles`() = runTest {
        val transport = RecordingTmdbTransport(
            TmdbHttpResponse(
                200,
                """{"results":[{"media_type":"movie","id":603,"title":"The Matrix","original_title":"The Matrix","release_date":"1999-03-30"}]}""",
            ),
        )
        val catalog = TmdbMovieCatalog(apiKey = "test-key", transport = transport)

        assertEquals(603, catalog.trending().single().tmdbId)
        assertEquals("/3/trending/all/week", checkNotNull(transport.request).url.encodedPath)
    }

    @Test
    fun `external ids uses TV endpoint for series and episodes`() = runTest {
        val transport = RecordingTmdbTransport(TmdbHttpResponse(200, """{"imdb_id":"tt123"}"""))
        val catalog = TmdbMovieCatalog(apiKey = "test-key", transport = transport)
        val series = Movie(
            tmdbId = 123,
            title = "Za sklom",
            originalTitle = "Za sklom",
            releaseYear = 2016,
            overview = null,
            voteAverage = null,
            posterPath = null,
            backdropPath = null,
            mediaType = MediaType.SERIES,
        )

        assertEquals("tt123", catalog.externalIds(series).imdbId)
        assertEquals("/3/tv/123/external_ids", checkNotNull(transport.request).url.encodedPath)
    }

    @Test
    fun `series seasons and episodes are mapped`() = runTest {
        val transport = QueueTmdbTransport(
            TmdbHttpResponse(
                200,
                """{"seasons":[{"season_number":0,"name":"Specials","episode_count":1},{"season_number":1,"name":"Season 1","episode_count":8,"air_date":"2016-09-28"}]}""",
            ),
            TmdbHttpResponse(
                200,
                """{"episodes":[{"episode_number":1,"name":"Episode One","overview":"First","air_date":"2016-09-28","vote_average":7.1,"still_path":"/still.jpg"}]}""",
            ),
        )
        val catalog = TmdbMovieCatalog(apiKey = "test-key", transport = transport)

        val season = catalog.seasons(123).single()
        assertEquals(1, season.number)
        assertEquals(8, season.episodeCount)

        val episode = catalog.episodes(123, 1).single()
        assertEquals(1, episode.number)
        assertEquals("Episode One", episode.name)
        assertEquals("/3/tv/123/season/1", transport.requests.last().url.encodedPath)
    }

    @Test
    fun `missing optional movie metadata stays valid`() = runTest {
        val transport = RecordingTmdbTransport(
            TmdbHttpResponse(
                200,
                """{"results":[{"media_type":"movie","id":1,"title":"X","original_title":"X"}]}""",
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

    private class QueueTmdbTransport(
        vararg responses: TmdbHttpResponse,
    ) : TmdbHttpTransport {
        private val responses = ArrayDeque(responses.toList())
        val requests = mutableListOf<Request>()

        override suspend fun execute(request: Request): TmdbHttpResponse {
            requests += request
            return responses.removeFirst()
        }
    }
}
