package sk.ziacik.androidstreamplayer.search

import kotlinx.coroutines.delay
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import sk.ziacik.androidstreamplayer.catalog.Movie
import sk.ziacik.androidstreamplayer.catalog.MovieCatalog
import sk.ziacik.androidstreamplayer.catalog.MovieExternalIds

class TorrentSearchControllerTest {
	@Test
	fun `open resolves imdb id before torrent search`() = runTest {
		val provider = RecordingMovieProvider(results = listOf(result("matrix")))
		val controller = TorrentSearchController(
			scope = this,
			catalog = FakeCatalog(externalIds = mapOf(603 to "tt0133093")),
			provider = provider,
		)

		controller.open(matrix())
		advanceUntilIdle()

		assertEquals("tt0133093", provider.requests.single().imdbId)
		assertEquals(603, provider.requests.single().tmdbId)
		assertEquals(listOf(result("matrix")), controller.state.value.results)
		assertFalse(controller.state.value.isSearching)
	}

	@Test
	fun `external id failure still searches by title without imdb`() = runTest {
		val provider = RecordingMovieProvider(results = listOf(result("matrix")))
		val controller = TorrentSearchController(
			scope = this,
			catalog = FakeCatalog(externalIdError = IllegalStateException("tmdb down")),
			provider = provider,
		)

		controller.open(matrix())
		advanceUntilIdle()

		assertNull(provider.requests.single().imdbId)
		assertNull(controller.state.value.errorMessage)
		assertEquals(listOf(result("matrix")), controller.state.value.results)
	}

	@Test
	fun `provider failure becomes inline search error`() = runTest {
		val provider = RecordingMovieProvider(error = IllegalStateException("knaben down"))
		val controller = TorrentSearchController(this, FakeCatalog(), provider)

		controller.open(matrix())
		advanceUntilIdle()

		assertEquals("Search failed", controller.state.value.errorMessage)
		assertFalse(controller.state.value.isSearching)
		assertEquals(emptyList<TorrentSearchResult>(), controller.state.value.results)
	}

	@Test
	fun `retry repeats current movie after provider recovers`() = runTest {
		val provider = RecordingMovieProvider(error = IllegalStateException("knaben down"))
		val controller = TorrentSearchController(this, FakeCatalog(), provider)

		controller.open(matrix())
		advanceUntilIdle()
		provider.error = null
		provider.results = listOf(result("recovered"))

		controller.retry()
		advanceUntilIdle()

		assertEquals(2, provider.requests.size)
		assertEquals(603, provider.requests.last().tmdbId)
		assertEquals(listOf(result("recovered")), controller.state.value.results)
		assertNull(controller.state.value.errorMessage)
	}

	@Test
	fun `newer movie supersedes delayed older movie`() = runTest {
		val provider = RecordingMovieProvider(results = listOf(result("release")))
		val controller = TorrentSearchController(
			scope = this,
			catalog = FakeCatalog(externalIdDelays = mapOf(1 to 1_000L)),
			provider = provider,
		)

		controller.open(movie(id = 1, title = "Old"))
		runCurrent()
		controller.open(movie(id = 2, title = "New"))
		advanceUntilIdle()

		assertEquals(2, controller.state.value.movie?.tmdbId)
		assertEquals(listOf(2), provider.requests.map { it.tmdbId })
		assertEquals(listOf(result("release")), controller.state.value.results)
	}

	private fun matrix() = movie(603, "The Matrix", 1999)

	private fun movie(
		id: Int,
		title: String,
		year: Int? = null,
	) = Movie(
		tmdbId = id,
		title = title,
		originalTitle = title,
		releaseYear = year,
		overview = null,
		voteAverage = null,
		posterPath = null,
		backdropPath = null,
	)

	private fun result(id: String) = TorrentSearchResult(
		id = id,
		title = id,
		magnetUri = "magnet:?xt=urn:btih:$id",
		seeders = 10,
	)

	private class FakeCatalog(
		private val externalIds: Map<Int, String?> = emptyMap(),
		private val externalIdError: Throwable? = null,
		private val externalIdDelays: Map<Int, Long> = emptyMap(),
	) : MovieCatalog {
		override suspend fun search(query: String): List<Movie> = emptyList()

		override suspend fun externalIds(tmdbId: Int): MovieExternalIds {
			externalIdDelays[tmdbId]?.let { delay(it) }
			externalIdError?.let { throw it }
			return MovieExternalIds(externalIds[tmdbId])
		}
	}

	private class RecordingMovieProvider(
		var results: List<TorrentSearchResult> = emptyList(),
		var error: Throwable? = null,
	) : TorrentSearchProvider {
		val requests = mutableListOf<MovieTorrentSearchRequest>()

		override suspend fun search(movie: MovieTorrentSearchRequest): List<TorrentSearchResult> {
			requests += movie
			error?.let { throw it }
			return results
		}
	}
}
