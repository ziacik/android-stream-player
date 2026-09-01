package sk.ziacik.androidstreamplayer.ui

import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
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
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import sk.ziacik.androidstreamplayer.catalog.Movie
import sk.ziacik.androidstreamplayer.catalog.MovieCatalog
import sk.ziacik.androidstreamplayer.catalog.MovieExternalIds
import sk.ziacik.androidstreamplayer.playback.PlaybackUiState
import sk.ziacik.androidstreamplayer.search.TorrentSearchController
import sk.ziacik.androidstreamplayer.search.TorrentSearchProvider
import sk.ziacik.androidstreamplayer.search.TorrentSearchResult
import sk.ziacik.androidstreamplayer.ui.theme.AndroidStreamPlayerTheme

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
	fun primaryHeadingsContrastWithDarkBackground() {
		setDetail(provider = TorrentSearchProvider { emptyList() })

		composeRule.onNodeWithText("The Matrix").assertContainsBrightPixels()
		composeRule.onNodeWithText("Available versions").assertContainsBrightPixels()
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
	fun parsedReleaseMetadataRendersAsBadgesAndKeepsRawTorrentDetails() {
		val title = "Dune.Part.Two.2024.2160p.UHD.BluRay.REMUX.DV.HDR10.HEVC.TrueHD.Atmos.7.1.ENG-GROUP"
		val hit = TorrentSearchResult(
			id = "badges",
			title = title,
			magnetUri = "magnet:?xt=urn:btih:badges",
			sizeBytes = 8_000_000_000,
			seeders = 42,
			source = "Knaben",
		)
		setDetail(provider = TorrentSearchProvider { listOf(hit) })

		composeRule.waitUntil(timeoutMillis = 5_000) {
			composeRule.onAllNodes(hasTestTag("torrent-badges")).fetchSemanticsNodes().isNotEmpty()
		}
		listOf(
			"4K",
			"REMUX",
			"DV",
			"HDR10",
			"HEVC",
			"TrueHD",
			"Atmos",
			"7.1",
			"ENG",
			"2024",
		).forEach { label ->
			composeRule.onNodeWithText(label).assertIsDisplayed()
		}
		composeRule.onNodeWithText(title).assertIsDisplayed()
		composeRule.onNodeWithText("42 seeds").assertIsDisplayed()
		composeRule.onNodeWithText("Knaben").assertIsDisplayed()
	}

	@Test
	fun unknownReleaseMetadataDoesNotRenderAutoPlaceholder() {
		val hit = TorrentSearchResult(
			id = "unknown",
			title = "Some.Movie.Release",
			magnetUri = "magnet:?xt=urn:btih:unknown",
		)
		setDetail(provider = TorrentSearchProvider { listOf(hit) })

		composeRule.waitUntil(timeoutMillis = 5_000) {
			composeRule.onAllNodes(hasTestTag("torrent-unknown")).fetchSemanticsNodes().isNotEmpty()
		}
		composeRule.onAllNodesWithText("AUTO").assertCountEquals(0)
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

	@Test
	fun startingTorrentShowsInlineFeedbackAndKeepsOtherVersionsVisible() {
		val first = torrent("hit-1")
		val second = torrent("hit-2")
		setDetail(
			provider = TorrentSearchProvider { listOf(first, second) },
			playbackState = PlaybackUiState(
				selectedResult = first,
				status = "Preparing stream…",
			),
		)

		composeRule.waitUntil(timeoutMillis = 5_000) {
			composeRule.onAllNodes(hasTestTag("torrent-starting-hit-1")).fetchSemanticsNodes().isNotEmpty()
		}
		composeRule.onNodeWithTag("torrent-starting-hit-1").assertIsDisplayed()
		composeRule.onNodeWithTag("torrent-hit-2").assertIsDisplayed()
	}

	@Test
	fun failedTorrentStartupShowsRetryGuidance() {
		val hit = torrent()
		setDetail(
			provider = TorrentSearchProvider { listOf(hit) },
			playbackState = PlaybackUiState(
				selectedResult = hit,
				status = "Stream failed",
			),
		)

		composeRule.waitUntil(timeoutMillis = 5_000) {
			composeRule.onAllNodes(hasTestTag("torrent-hit-1")).fetchSemanticsNodes().isNotEmpty()
		}
		composeRule.onNodeWithText("Could not start stream. Try another version.").assertIsDisplayed()
	}

	private fun setDetail(
		provider: TorrentSearchProvider,
		onPlay: (TorrentSearchResult) -> Unit = {},
		playbackState: PlaybackUiState = PlaybackUiState(),
	) {
		val controller = TorrentSearchController(
			scope = scope,
			catalog = FakeCatalog,
			provider = provider,
		)
		composeRule.setContent {
			AndroidStreamPlayerTheme {
				MovieDetailScreen(
					movie = matrix(),
					torrentController = controller,
					playbackState = playbackState,
					onPlay = onPlay,
					onBack = {},
				)
			}
		}
	}

	private fun SemanticsNodeInteraction.assertContainsBrightPixels() {
		val pixels = captureToImage().toPixelMap()
		var brightestChannel = 0f
		for (x in 0 until pixels.width) {
			for (y in 0 until pixels.height) {
				val color = pixels[x, y]
				brightestChannel = maxOf(
					brightestChannel,
					color.red,
					color.green,
					color.blue,
				)
			}
		}
		assertTrue("Expected bright text pixels on the dark detail background", brightestChannel > 0.5f)
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

	private fun torrent(id: String = "hit-1") = TorrentSearchResult(
		id = id,
		title = "The.Matrix.1999.$id.1080p.BluRay",
		magnetUri = "magnet:?xt=urn:btih:$id",
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
