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
import org.junit.Rule
import org.junit.Test
import sk.ziacik.androidstreamplayer.catalog.Movie
import sk.ziacik.androidstreamplayer.catalog.MovieCatalog
import sk.ziacik.androidstreamplayer.catalog.MovieExternalIds
import sk.ziacik.androidstreamplayer.catalog.MovieSearchController
import sk.ziacik.androidstreamplayer.playback.PlaybackController
import sk.ziacik.androidstreamplayer.search.MovieTorrentSearchRequest
import sk.ziacik.androidstreamplayer.search.TorrentSearchController
import sk.ziacik.androidstreamplayer.search.TorrentSearchProvider
import sk.ziacik.androidstreamplayer.search.TorrentSearchResult

class KinoAppScreenTest {
	@get:Rule
	val composeRule = createComposeRule()

	private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

	@After
	fun tearDown() {
		scope.cancel()
	}

	@Test
	fun catalogIsEntryPointAndSelectingMovieShowsTorrentVersions() {
		val catalog = FakeCatalog(listOf(matrix()))
		val movieSearchController = MovieSearchController(
			scope = scope,
			catalog = catalog,
			debounceMs = 0,
		)
		val torrentSearchController = TorrentSearchController(
			scope = scope,
			catalog = catalog,
			provider = TorrentSearchProvider { request ->
				listOf(matrixTorrent(request))
			},
		)
		val playbackController = PlaybackController(
			scope = scope,
			streamer = null,
		)

		composeRule.setContent {
			KinoAppScreen(
				movieSearchController = movieSearchController,
				torrentSearchController = torrentSearchController,
				playbackController = playbackController,
			)
		}

		composeRule.onNodeWithTag("movie-search-input").assertIsDisplayed()
		composeRule.onNodeWithTag("movie-search-input").performTextInput("Matrix")
		composeRule.waitUntil(timeoutMillis = 5_000) {
			composeRule.onAllNodes(hasTestTag("movie-603")).fetchSemanticsNodes().isNotEmpty()
		}
		composeRule.onNodeWithTag("movie-603").performClick()

		composeRule.waitUntil(timeoutMillis = 5_000) {
			composeRule.onAllNodes(hasTestTag("torrent-result-matrix-1080p")).fetchSemanticsNodes().isNotEmpty()
		}
		composeRule.onNodeWithText("The Matrix").assertIsDisplayed()
		composeRule.onNodeWithText("Available versions").assertIsDisplayed()
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

	private fun matrixTorrent(request: MovieTorrentSearchRequest) = TorrentSearchResult(
		id = "matrix-1080p",
		title = "${request.title}.${request.year}.1080p.BluRay",
		magnetUri = "magnet:?xt=urn:btih:matrix",
		quality = "1080p",
		sizeBytes = 8_000_000_000,
		seeders = 42,
		source = "Knaben",
	)

	private class FakeCatalog(
		private val results: List<Movie>,
	) : MovieCatalog {
		override suspend fun search(query: String): List<Movie> = results

		override suspend fun externalIds(tmdbId: Int): MovieExternalIds =
			MovieExternalIds("tt0133093")
	}
}
