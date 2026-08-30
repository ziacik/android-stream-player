package sk.ziacik.androidstreamplayer.catalog

import java.io.IOException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MovieSearchControllerTest {
    @Test
    fun `two characters search after debounce`() = runTest {
        val catalog = RecordingMovieCatalog()
        val controller = MovieSearchController(this, catalog, debounceMs = 400)

        controller.setQuery("Al")
        advanceTimeBy(399)
        assertTrue(catalog.queries.isEmpty())

        advanceTimeBy(1)
        advanceUntilIdle()

        assertEquals(listOf("Al"), catalog.queries)
    }

    @Test
    fun `one character does not search and clears previous results`() = runTest {
        val alien = movie(1, "Alien")
        val catalog = RecordingMovieCatalog(results = mapOf("Alien" to listOf(alien)))
        val controller = MovieSearchController(this, catalog, debounceMs = 0)

        controller.setQuery("Alien")
        advanceUntilIdle()
        assertEquals(listOf(alien), controller.state.value.results)

        controller.setQuery("A")
        advanceUntilIdle()

        assertEquals(listOf("Alien"), catalog.queries)
        assertTrue(controller.state.value.results.isEmpty())
        assertFalse(controller.state.value.isSearching)
    }

    @Test
    fun `newer query wins over slower older request`() = runTest {
        val alien = movie(2, "Alien")
        val aliens = movie(3, "Aliens")
        val catalog = RecordingMovieCatalog(
            results = mapOf("Alien" to listOf(alien), "Aliens" to listOf(aliens)),
            delays = mapOf("Alien" to 1_000L),
        )
        val controller = MovieSearchController(this, catalog, debounceMs = 0)

        controller.setQuery("Alien")
        runCurrent()
        controller.setQuery("Aliens")
        advanceUntilIdle()

        assertEquals("Aliens", controller.state.value.query)
        assertEquals(listOf(aliens), controller.state.value.results)
        assertNull(controller.state.value.errorMessage)
    }

    @Test
    fun `clearing query resets landing state`() = runTest {
        val catalog = RecordingMovieCatalog(results = mapOf("Alien" to listOf(movie(1, "Alien"))))
        val controller = MovieSearchController(this, catalog, debounceMs = 0)

        controller.setQuery("Alien")
        advanceUntilIdle()
        controller.setQuery("")

        val state = controller.state.value
        assertEquals("", state.query)
        assertFalse(state.isSearching)
        assertTrue(state.results.isEmpty())
        assertNull(state.errorMessage)
    }

    @Test
    fun `search failure exposes error and retry repeats latest query`() = runTest {
        val catalog = RecordingMovieCatalog(failuresBeforeSuccess = 1)
        val controller = MovieSearchController(this, catalog, debounceMs = 0)

        controller.setQuery("Alien")
        advanceUntilIdle()

        assertEquals("Search failed", controller.state.value.errorMessage)
        assertFalse(controller.state.value.isSearching)

        controller.retry()
        advanceUntilIdle()

        assertEquals(listOf("Alien", "Alien"), catalog.queries)
        assertNull(controller.state.value.errorMessage)
    }

    @Test
    fun `search now bypasses pending debounce`() = runTest {
        val catalog = RecordingMovieCatalog()
        val controller = MovieSearchController(this, catalog, debounceMs = 10_000)

        controller.setQuery("Alien")
        controller.searchNow()
        advanceUntilIdle()

        assertEquals(listOf("Alien"), catalog.queries)
    }

    @Test
    fun `focused movie id is retained independently from search results`() = runTest {
        val controller = MovieSearchController(this, RecordingMovieCatalog())

        controller.setFocusedMovie(603)

        assertEquals(603, controller.state.value.focusedMovieId)
    }

    private fun movie(id: Int, title: String) = Movie(
        tmdbId = id,
        title = title,
        originalTitle = title,
        releaseYear = 1979,
        overview = null,
        voteAverage = null,
        posterPath = null,
        backdropPath = null,
    )

    private class RecordingMovieCatalog(
        private val results: Map<String, List<Movie>> = emptyMap(),
        private val delays: Map<String, Long> = emptyMap(),
        private var failuresBeforeSuccess: Int = 0,
    ) : MovieCatalog {
        val queries = mutableListOf<String>()

        override suspend fun search(query: String): List<Movie> {
            queries += query
            delays[query]?.let { delay(it) }
            if (failuresBeforeSuccess > 0) {
                failuresBeforeSuccess -= 1
                throw IOException("boom")
            }
            return results[query].orEmpty()
        }

        override suspend fun externalIds(tmdbId: Int): MovieExternalIds = MovieExternalIds(null)
    }
}
