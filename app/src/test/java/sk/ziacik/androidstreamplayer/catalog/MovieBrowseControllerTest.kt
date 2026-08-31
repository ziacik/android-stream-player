package sk.ziacik.androidstreamplayer.catalog

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MovieBrowseControllerTest {
	@Test
	fun loadsTrendingMoviesForDashboard() {
		val dispatcher = StandardTestDispatcher()
		val scope = TestScope(dispatcher)
		val movie = Movie(
			tmdbId = 603,
			title = "The Matrix",
			originalTitle = "The Matrix",
			releaseYear = 1999,
			overview = null,
			voteAverage = 8.2,
			posterPath = null,
			backdropPath = null,
		)
		val controller = MovieBrowseController(
			scope = scope,
			loadTrending = { listOf(movie) },
		)

		scope.advanceUntilIdle()

		assertFalse(controller.state.value.isLoading)
		assertEquals(listOf(movie), controller.state.value.trending)
	}
}
