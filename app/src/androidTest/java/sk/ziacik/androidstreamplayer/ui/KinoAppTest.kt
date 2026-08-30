package sk.ziacik.androidstreamplayer.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
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
import sk.ziacik.androidstreamplayer.playback.PlaybackController
import sk.ziacik.androidstreamplayer.search.MovieTorrentSearchRequest
import sk.ziacik.androidstreamplayer.search.TorrentSearchController
import sk.ziacik.androidstreamplayer.search.TorrentSearchProvider
import sk.ziacik.androidstreamplayer.search.TorrentSearchResult
import sk.ziacik.androidstreamplayer.torrent.TorrentSource
import sk.ziacik.androidstreamplayer.torrent.TorrentStreamer
import sk.ziacik.androidstreamplayer.watch.WatchProgressEntry
import sk.ziacik.androidstreamplayer.watch.WatchProgressRepository
import sk.ziacik.androidstreamplayer.watch.WatchProgressStorage

class KinoAppTest {
	@get:Rule
	val composeRule = createComposeRule()

	private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

	@After
	fun tearDown() {
		scope.cancel()
	}

	@Test
	fun movieCatalogIsEntryPointAndFlowReturnsFromPlayerToDetail() {
		val catalog = FakeCatalog
		val movieSearchController = MovieSearchController(
			scope = scope,
			catalog = catalog,
			debounceMs = 0,
		)
		val torrentSearchController = TorrentSearchController(
			scope = scope,
			catalog = catalog,
			provider = TorrentSearchProvider { request -> listOf(torrent(request)) },
		)
		val playbackController = PlaybackController(
			scope = scope,
			streamer = TorrentStreamer { TorrentSource("http://127.0.0.1/movie.mkv") },
			onStreamReady = {},
		)

		composeRule.setContent {
			KinoApp(
				movieSearchController = movieSearchController,
				torrentSearchController = torrentSearchController,
				playbackController = playbackController,
				watchProgressRepository = watchProgressRepository(),
				playerContent = { _, result, _, onExit ->
					Box(
						modifier = Modifier
							.testTag("kino-player")
							.clickable(onClick = onExit),
					) {
						Text(result?.title ?: "Player")
					}
				},
			)
		}

		composeRule.onNodeWithTag("movie-search-input").assertIsDisplayed()
		composeRule.onNodeWithTag("movie-search-input").performTextInput("Matrix")
		composeRule.waitUntil(timeoutMillis = 5_000) {
			composeRule.onAllNodes(hasTestTag("movie-603")).fetchSemanticsNodes().isNotEmpty()
		}
		composeRule.onNodeWithTag("movie-603").performClick()

		composeRule.waitUntil(timeoutMillis = 5_000) {
			composeRule.onAllNodes(hasTestTag("torrent-hit-1")).fetchSemanticsNodes().isNotEmpty()
		}
		composeRule.onNodeWithTag("movie-detail").assertIsDisplayed()
		composeRule.onNodeWithTag("torrent-hit-1").performClick()

		composeRule.waitUntil(timeoutMillis = 5_000) {
			composeRule.onAllNodes(hasTestTag("kino-player")).fetchSemanticsNodes().isNotEmpty()
		}
		composeRule.onNodeWithTag("kino-player").performClick()

		composeRule.waitUntil(timeoutMillis = 5_000) {
			composeRule.onAllNodes(hasTestTag("movie-detail")).fetchSemanticsNodes().isNotEmpty()
		}
	}

	@Test
	fun resumeWatchingStartsStoredTorrentAtStoredPositionWithoutSearchingAgain() {
		val entry = WatchProgressEntry(
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
		var torrentSearchCalls = 0
		var playerMovie: Movie? = null
		var playerResult: TorrentSearchResult? = null
		var playerResumePositionMs: Long? = null
		val movieSearchController = MovieSearchController(
			scope = scope,
			catalog = FakeCatalog,
			debounceMs = 0,
		)
		val torrentSearchController = TorrentSearchController(
			scope = scope,
			catalog = FakeCatalog,
			provider = TorrentSearchProvider {
				torrentSearchCalls += 1
				emptyList()
			},
		)
		val playbackController = PlaybackController(
			scope = scope,
			streamer = TorrentStreamer { TorrentSource("http://127.0.0.1/movie.mkv") },
			onStreamReady = {},
		)

		composeRule.setContent {
			KinoApp(
				movieSearchController = movieSearchController,
				torrentSearchController = torrentSearchController,
				playbackController = playbackController,
				watchProgressRepository = watchProgressRepository(listOf(entry)),
				playerContent = { movie, result, resumePositionMs, onExit ->
					playerMovie = movie
					playerResult = result
					playerResumePositionMs = resumePositionMs
					Box(
						modifier = Modifier
							.testTag("kino-player")
							.clickable(onClick = onExit),
					)
				},
			)
		}

		composeRule.onNodeWithTag("resume-watching-603").assertIsDisplayed().performClick()
		composeRule.waitUntil(timeoutMillis = 5_000) {
			composeRule.onAllNodes(hasTestTag("kino-player")).fetchSemanticsNodes().isNotEmpty()
		}

		composeRule.runOnIdle {
			assertEquals(603, playerMovie?.tmdbId)
			assertEquals(entry.result, playerResult)
			assertEquals(300_000L, playerResumePositionMs)
			assertEquals(0, torrentSearchCalls)
		}
	}

	private fun torrent(request: MovieTorrentSearchRequest) = TorrentSearchResult(
		id = "hit-1",
		title = "${request.title}.${request.year}.1080p.BluRay",
		magnetUri = "magnet:?xt=urn:btih:matrix",
		quality = "1080p",
		sizeBytes = 8_000_000_000,
		seeders = 42,
		source = "Knaben",
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

	private fun watchProgressRepository(
		entries: List<WatchProgressEntry> = emptyList(),
	): WatchProgressRepository = WatchProgressRepository(
		storage = object : WatchProgressStorage {
			private var stored = entries

			override fun load(): List<WatchProgressEntry> = stored

			override fun save(entries: List<WatchProgressEntry>) {
				stored = entries
			}
		},
	)

	private object FakeCatalog : MovieCatalog {
		override suspend fun search(query: String): List<Movie> = listOf(matrix())

		override suspend fun externalIds(tmdbId: Int): MovieExternalIds =
			MovieExternalIds("tt0133093")
	}
}
