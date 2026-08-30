package sk.ziacik.androidstreamplayer.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
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
import sk.ziacik.androidstreamplayer.search.TorrentSearchResult
import sk.ziacik.androidstreamplayer.watch.WatchProgressEntry

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
			runCatching {
				composeRule.onNodeWithTag("movie-603").fetchSemanticsNode()
			}.isSuccess
		}
		composeRule.onNodeWithTag("movie-603").performClick()

		composeRule.runOnIdle {
			assertEquals(603, selected?.tmdbId)
		}
	}

	@Test
	fun landingShowsResumeWatchingAndClickReturnsStoredEntry() {
		var resumed: WatchProgressEntry? = null
		val entry = WatchProgressEntry(
			movie = matrix(),
			result = TorrentSearchResult(
				id = "matrix-1080p",
				title = "The Matrix 1999 1080p",
				magnetUri = "magnet:?xt=urn:btih:matrix",
				quality = "1080p",
			),
			positionMs = 300_000L,
			durationMs = 600_000L,
			updatedAtEpochMs = 123L,
		)
		val controller = MovieSearchController(
			scope = scope,
			catalog = FakeCatalog(),
			debounceMs = 0,
		)

		composeRule.setContent {
			MovieSearchScreen(
				controller = controller,
				onMovieSelected = {},
				resumeWatching = listOf(entry),
				onResumeWatching = { resumed = it },
				onRemoveResumeWatching = {},
			)
		}

		composeRule.onNodeWithText("Resume Watching").assertIsDisplayed()
		composeRule.onNodeWithTag("resume-watching-603").assertIsDisplayed().performClick()

		composeRule.runOnIdle {
			assertEquals(entry, resumed)
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
			runCatching {
				composeRule.onNodeWithTag("movie-search-empty").fetchSemanticsNode()
			}.isSuccess
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
			runCatching {
				composeRule.onNodeWithTag("movie-search-error").fetchSemanticsNode()
			}.isSuccess
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
