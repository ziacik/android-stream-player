package sk.ziacik.androidstreamplayer.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test
import sk.ziacik.androidstreamplayer.search.TorrentSearchResult
import sk.ziacik.androidstreamplayer.search.TorrentSearchUiState
import sk.ziacik.androidstreamplayer.torrent.TorrentStartupStats
import sk.ziacik.androidstreamplayer.ui.theme.AndroidStreamPlayerTheme

class TorrentStartupStatsUiTest {
	@get:Rule
	val composeRule = createComposeRule()

	@Test
	fun startingTorrentShowsLivePeerSpeedAndBufferStats() {
		val result = TorrentSearchResult(
			id = "movie",
			title = "Movie.2026.1080p",
			magnetUri = "magnet:?xt=urn:btih:movie",
			seeders = 26,
		)
		composeRule.setContent {
			AndroidStreamPlayerTheme {
				TorrentResults(
					state = TorrentSearchUiState(results = listOf(result)),
					startingResultId = result.id,
					startupErrorMessage = null,
					startupStats = TorrentStartupStats(
						activePeers = 3,
						totalPeers = 17,
						connectedSeeders = 2,
						downloadSpeedBytesPerSecond = 1.8 * 1024 * 1024,
						preloadedBytes = 24L * 1024 * 1024,
						preloadSizeBytes = 50L * 1024 * 1024,
					),
					onPlay = {},
					onRetry = {},
				)
			}
		}

		composeRule.onNodeWithText(
			"Peers 3/17 · Seeds 2 · 1.8 MB/s · Buffered 24/50 MB",
		).assertIsDisplayed()
	}
}
