package sk.ziacik.androidstreamplayer.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodes
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import sk.ziacik.androidstreamplayer.catalog.Movie
import sk.ziacik.androidstreamplayer.catalog.MovieCatalog
import sk.ziacik.androidstreamplayer.catalog.MovieExternalIds
import sk.ziacik.androidstreamplayer.catalog.MovieSearchController

class MovieSearchScreenTest {
	@get:Rule
	val composeRule = createComposeRule()

	private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

	@After
	fun tearDown() {
		scope.cancel()
	}

	@Test
	fun searchShowsPosterAndSelectionReturnsMovie() {
		var selected: Movie? = null
		val controller = MovieSearchController(
			scope = scope,
			catalog = FakeCatalog(results = listOf(matrix())),
			debounceMs = 0,
		)

		composeRule.setContent {
			MovieSearchScreen(
				controller = controller,
				onMovieSelected = { selected = it },
			)
		}

		composeRule.onNodeWithTag("movie-search-input").performTextInput("Matrix")
		composeRule.waitUntil(timeoutMillis = 5_000) {
			composeRule.onAllNodes(hasTestTag("movie-603")).fetchSemanticsNodes().isNotEmpty()
		}
		composeRule.onNodeWithTag("movie-603").performClick()

		composeRule.runOnIdle {
			assertEquals(603, selected?.tmdbId)
		}
	}

	@Test
	fun emptySearchShowsNoMoviesFound() {
		val controller = MovieSearchController(
			scope = scope,
			catalog = FakeCatalog(),
			debounceMs = 0,
		)

		composeRule.setContent {
			MovieSearchScreen(controller = controller, onMovieSelected = {})
		}

		composeRule.onNodeWithTag("movie-search-input").performTextInput("Unknown")

		composeRule.waitUntil(timeoutMillis = 5_000) {
			composeRule.onAllNodes(hasTestTag("movie-search-empty")).fetchSemanticsNodes().isNotEmpty()
		}
		composeRule.onNodeWithText("No movies found").assertIsDisplayed()
	}

	@Test
	fun failedSearchShowsInlineErrorAndRetry() {
		val controller = MovieSearchController(
			scope = scope,
			catalog = FakeCatalog(error = IllegalStateException("boom")),
			debounceMs = 0,
		)

		composeRule.setContent {
			MovieSearchScreen(controller = controller, onMovieSelected = {})
		}

		composeRule.onNodeWithTag("movie-search-input").performTextInput("Matrix")

		composeRule.waitUntil(timeoutMillis = 5_000) {
			composeRule.onAllNodes(hasTestTag("movie-search-error")).fetchSemanticsNodes().isNotEmpty()
		}
		composeRule.onNodeWithText("Couldn’t search movies").assertIsDisplayed()
		composeRule.onNodeWithText("Retry").assertIsDisplayed()
	}

	private fun matrix() = Movie(
		tmdbId = 603,
		title = "The Matrix",
		originalTitle = "The Matrix",
		releaseYear = 1999,
		overview = "A hacker discovers the truth about his world.",
		voteAverage = 8.2,
		posterPath = "/matrix.jpg",
		backdropPath = "/matrix-backdrop.jpg",
	)

	private class FakeCatalog(
		private val results: List<Movie> = emptyList(),
		private val error: Throwable? = null,
	) : MovieCatalog {
		override suspend fun search(query: String): List<Movie> {
			error?.let { throw it }
			return results
		}

		override suspend fun externalIds(tmdbId: Int): MovieExternalIds = MovieExternalIds(null)
	}
}
