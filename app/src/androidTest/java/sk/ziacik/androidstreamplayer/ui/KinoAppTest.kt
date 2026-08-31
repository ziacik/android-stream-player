package sk.ziacik.androidstreamplayer.ui

import android.view.KeyEvent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import sk.ziacik.androidstreamplayer.catalog.Movie
import sk.ziacik.androidstreamplayer.catalog.MovieBrowseController
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
	fun homeDashboardIsEntryPointAndSearchIsOneActionAway() {
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
				movieBrowseController = movieBrowseController(),
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

		composeRule.onNodeWithTag("home-dashboard").assertIsDisplayed()
		composeRule.onNodeWithTag("home-search-nav").assertIsDisplayed().performClick()
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
	fun dpadDownMovesFromResumeWatchingToTrending() {
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

		composeRule.setContent {
			HomeScreen(
				controller = movieBrowseController(listOf(odyssey())),
				resumeWatching = listOf(entry),
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
		composeRule.onNodeWithTag("resume-watching-603")
			.requestFocus()
			.assertIsFocused()
			.performKeyInput { pressKey(Key.DirectionDown) }
		composeRule.onNodeWithTag("home-trending-1054867").assertIsFocused()
	}

	@Test
	fun dpadCanMoveDownAgainAfterReturningFromScrolledTrendingRow() {
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

		composeRule.setContent {
			HomeScreen(
				controller = movieBrowseController(listOf(odyssey(), interstellar())),
				resumeWatching = listOf(entry),
				onMovieSelected = {},
				onResumeWatching = {},
				onCancelResumeWatching = {},
				onRemoveResumeWatching = {},
				startingResumeMovieId = null,
				onSearch = {},
			)
		}

		composeRule.waitUntil(timeoutMillis = 5_000) {
			composeRule.onAllNodes(hasTestTag("home-trending-157336")).fetchSemanticsNodes().isNotEmpty()
		}

		composeRule.onNodeWithTag("resume-watching-603")
			.requestFocus()
			.assertIsFocused()
			.performKeyInput { pressKey(Key.DirectionDown) }
		composeRule.onNodeWithTag("home-trending-1054867")
			.assertIsFocused()
			.performKeyInput { pressKey(Key.DirectionRight) }
		composeRule.onNodeWithTag("home-trending-157336")
			.assertIsFocused()
			.performKeyInput { pressKey(Key.DirectionUp) }
		composeRule.onNodeWithTag("resume-watching-603")
			.assertIsFocused()
			.performKeyInput { pressKey(Key.DirectionDown) }
		composeRule.onNodeWithTag("home-trending-157336").assertIsFocused()
	}

	@Test
	fun backFromTrendingDetailRestoresFocusedCard() {
		val trending = (0..13).map(::dashboardMovie)
		val movieSearchController = MovieSearchController(
			scope = scope,
			catalog = FakeCatalog,
			debounceMs = 0,
		)
		val torrentSearchController = TorrentSearchController(
			scope = scope,
			catalog = FakeCatalog,
			provider = TorrentSearchProvider { request -> listOf(torrent(request)) },
		)
		val playbackController = PlaybackController(
			scope = scope,
			streamer = TorrentStreamer { TorrentSource("http://127.0.0.1/movie.mkv") },
			onStreamReady = {},
		)

		composeRule.setContent {
			KinoApp(
				movieBrowseController = movieBrowseController(trending),
				movieSearchController = movieSearchController,
				torrentSearchController = torrentSearchController,
				playbackController = playbackController,
				watchProgressRepository = watchProgressRepository(),
				playerContent = { _, _, _, _ -> Box(Modifier.testTag("kino-player")) },
			)
		}

		val targetIndex = 12
		composeRule.waitUntil(timeoutMillis = 5_000) {
			composeRule.onAllNodes(hasTestTag("home-trending-${trending.first().tmdbId}"))
				.fetchSemanticsNodes()
				.isNotEmpty()
		}
		var focusedCard = composeRule.onNodeWithTag("home-trending-${trending.first().tmdbId}")
		focusedCard.requestFocus().assertIsFocused()
		for (index in 1..targetIndex) {
			focusedCard.performKeyInput { pressKey(Key.DirectionRight) }
			focusedCard = composeRule.onNodeWithTag("home-trending-${trending[index].tmdbId}")
			focusedCard.assertIsFocused()
		}

		focusedCard.performClick()
		composeRule.waitUntil(timeoutMillis = 5_000) {
			composeRule.onAllNodes(hasTestTag("movie-detail")).fetchSemanticsNodes().isNotEmpty()
		}
		InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
		composeRule.waitUntil(timeoutMillis = 5_000) {
			composeRule.onAllNodes(hasTestTag("home-dashboard")).fetchSemanticsNodes().isNotEmpty()
		}

		composeRule.onNodeWithTag("home-trending-${trending[targetIndex].tmdbId}")
			.assertIsFocused()
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
				movieBrowseController = movieBrowseController(),
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
					) {
					}
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

	private fun movieBrowseController(
		trending: List<Movie> = listOf(matrix()),
	) = MovieBrowseController(
		scope = scope,
		loadTrending = { trending },
	)

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

	private fun interstellar() = Movie(
		tmdbId = 157_336,
		title = "Interstellar",
		originalTitle = "Interstellar",
		releaseYear = 2014,
		overview = "Explorers travel through a wormhole in space.",
		voteAverage = 8.5,
		posterPath = null,
		backdropPath = null,
	)

	private fun dashboardMovie(index: Int) = Movie(
		tmdbId = 10_000 + index,
		title = "Dashboard Movie $index",
		originalTitle = "Dashboard Movie $index",
		releaseYear = 2020 + index % 7,
		overview = "Dashboard movie used for focus restoration testing.",
		voteAverage = 7.0,
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
	}
}