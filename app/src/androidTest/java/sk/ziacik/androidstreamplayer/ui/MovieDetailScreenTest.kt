package sk.ziacik.androidstreamplayer.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import kotlinx.coroutines.CompletableDeferred
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
import sk.ziacik.androidstreamplayer.search.TorrentSearchController
import sk.ziacik.androidstreamplayer.search.TorrentSearchProvider
import sk.ziacik.androidstreamplayer.search.TorrentSearchResult

class MovieDetailScreenTest {
	@get:Rule
	val composeRule = createComposeRule()

	private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

	@After
	fun tearDown() {
		scope.cancel()
	}

	@Test
	fun movieMetadataRemainsVisible() {
		setDetail(provider = TorrentSearchProvider { emptyList() })

		composeRule.onNodeWithText("The Matrix").assertIsDisplayed()
		composeRule.onNodeWithText("1999").assertIsDisplayed()
		composeRule.onNodeWithText("A hacker discovers the truth about his world.").assertIsDisplayed()
	}

	@Test
	fun loadingVersionsKeepsMovieVisible() {
		val gate = CompletableDeferred<Unit>()
		setDetail(
			provider = TorrentSearchProvider {
				gate.await()
				emptyList()
			},
		)

		composeRule.onNodeWithText("The Matrix").assertIsDisplayed()
		composeRule.onNodeWithText("Finding versions…").assertIsDisplayed()
	}

	@Test
	fun emptyVersionsShowsEmptyState() {
		setDetail(provider = TorrentSearchProvider { emptyList() })

		composeRule.waitUntil(timeoutMillis = 5_000) {
			composeRule.onAllNodes(hasTestTag("torrent-results-empty")).fetchSemanticsNodes().isNotEmpty()
		}
		composeRule.onNodeWithText("No versions found").assertIsDisplayed()
	}

	@Test
	fun failedVersionsShowsRetry() {
		setDetail(
			provider = TorrentSearchProvider {
				throw IllegalStateException("boom")
			},
		)

		composeRule.waitUntil(timeoutMillis = 5_000) {
			composeRule.onAllNodes(hasTestTag("torrent-results-error")).fetchSemanticsNodes().isNotEmpty()
		}
		composeRule.onNodeWithText("Couldn’t find versions").assertIsDisplayed()
		composeRule.onNodeWithText("Retry").assertIsDisplayed()
	}

	@Test
	fun selectingTorrentCallsPlay() {
		var selected: TorrentSearchResult? = null
		val hit = torrent()
		setDetail(
			provider = TorrentSearchProvider { listOf(hit) },
			onPlay = { selected = it },
		)

		composeRule.waitUntil(timeoutMillis = 5_000) {
			composeRule.onAllNodes(hasTestTag("torrent-hit-1")).fetchSemanticsNodes().isNotEmpty()
		}
		composeRule.onNodeWithTag("torrent-hit-1").performClick()

		composeRule.runOnIdle {
			assertEquals("hit-1", selected?.id)
		}
	}

	private fun setDetail(
		provider: TorrentSearchProvider,
		onPlay: (TorrentSearchResult) -> Unit = {},
	) {
		val controller = TorrentSearchController(
			scope = scope,
			catalog = FakeCatalog,
			provider = provider,
		)
		composeRule.setContent {
			MovieDetailScreen(
				movie = matrix(),
				torrentController = controller,
				onPlay = onPlay,
				onBack = {},
			)
		}
	}

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

	private fun torrent() = TorrentSearchResult(
		id = "hit-1",
		title = "The.Matrix.1999.1080p.BluRay",
		magnetUri = "magnet:?xt=urn:btih:matrix",
		quality = "1080p",
		sizeBytes = 8_000_000_000,
		seeders = 42,
		source = "Knaben",
	)

	private object FakeCatalog : MovieCatalog {
		override suspend fun search(query: String): List<Movie> = emptyList()

		override suspend fun externalIds(tmdbId: Int): MovieExternalIds =
			MovieExternalIds("tt0133093")
	}
}
