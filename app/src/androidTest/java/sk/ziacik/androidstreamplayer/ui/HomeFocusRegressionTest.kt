package sk.ziacik.androidstreamplayer.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Rule
import org.junit.Test
import sk.ziacik.androidstreamplayer.catalog.Movie
import sk.ziacik.androidstreamplayer.catalog.MovieBrowseController
import sk.ziacik.androidstreamplayer.search.TorrentSearchResult
import sk.ziacik.androidstreamplayer.watch.WatchProgressEntry

class HomeFocusRegressionTest {
	@get:Rule
	val composeRule = createComposeRule()

	private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

	@After
	fun tearDown() {
		scope.cancel()
	}

	@Test
	fun rightAtEndOfResumeRowStaysInResumeRow() {
		composeRule.setContent {
			HomeScreen(
				controller = movieBrowseController(),
				resumeWatching = listOf(resumeEntry()),
				onMovieSelected = {},
				onResumeWatching = {},
				onCancelResumeWatching = {},
				onRemoveResumeWatching = {},
				startingResumeMovieId = null,
				onSearch = {},
			)
		}

		waitUntilFocused("resume-watching-603")
		composeRule.onNodeWithTag("resume-watching-603")
			.performKeyInput { pressKey(Key.DirectionRight) }
		composeRule.onNodeWithTag("resume-watching-603").assertIsFocused()
	}

	@Test
	fun removingLastFocusedResumeItemMovesFocusToTrending() {
		var resumeWatching by mutableStateOf(listOf(resumeEntry()))

		composeRule.setContent {
			HomeScreen(
				controller = movieBrowseController(),
				resumeWatching = resumeWatching,
				onMovieSelected = {},
				onResumeWatching = {},
				onCancelResumeWatching = {},
				onRemoveResumeWatching = {},
				startingResumeMovieId = null,
				onSearch = {},
			)
		}

		composeRule.waitUntil(timeoutMillis = 5_000) {
			composeRule.onAllNodes(hasTestTag("home-trending-1054867")).fetchSemanticsNodes().isNotEmpty()
		}
		waitUntilFocused("resume-watching-603")

		composeRule.runOnIdle {
			resumeWatching = emptyList()
		}
		composeRule.waitUntil(timeoutMillis = 5_000) {
			composeRule.onAllNodes(hasTestTag("resume-watching-603")).fetchSemanticsNodes().isEmpty()
		}
		waitUntilFocused("home-trending-1054867")
	}

	private fun waitUntilFocused(tag: String) {
		composeRule.waitUntil(timeoutMillis = 5_000) {
			runCatching {
				composeRule.onNodeWithTag(tag).assertIsFocused()
			}.isSuccess
		}
	}

	private fun movieBrowseController() = MovieBrowseController(
		scope = scope,
		loadTrending = { listOf(odyssey()) },
	)

	private fun resumeEntry() = WatchProgressEntry(
		movie = matrix(),
		result = TorrentSearchResult(
			id = "stored-hit",
			title = "The.Matrix.1999.1080p.Stored",
			magnetUri = "magnet:?xt=urn:btih:stored-matrix",
			quality = "1080p",
		),
		positionMs = 300_000L,
		durationMs = 600_000L,
		updatedAtEpochMs = 123L,
	)

	private fun matrix() = Movie(
		tmdbId = 603,
		title = "The Matrix",
		originalTitle = "The Matrix",
		releaseYear = 1999,
		overview = "A hacker discovers the truth about his world.",
		voteAverage = 8.2,
		posterPath = null,
		backdropPath = null,
	)

	private fun odyssey() = Movie(
		tmdbId = 1_054_867,
		title = "The Odyssey",
		originalTitle = "The Odyssey",
		releaseYear = 2026,
		overview = "Odysseus journeys home.",
		voteAverage = 7.5,
		posterPath = null,
		backdropPath = null,
	)
}