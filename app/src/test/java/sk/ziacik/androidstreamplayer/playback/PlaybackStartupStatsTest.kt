package sk.ziacik.androidstreamplayer.playback

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import sk.ziacik.androidstreamplayer.search.TorrentSearchResult
import sk.ziacik.androidstreamplayer.torrent.TorrentSource
import sk.ziacik.androidstreamplayer.torrent.TorrentStartupStats
import sk.ziacik.androidstreamplayer.torrent.TorrentStreamer

class PlaybackStartupStatsTest {
	@Test
	fun `live startup stats are published while selected torrent is preparing`() = runTest {
		val gate = CompletableDeferred<Unit>()
		val stats = TorrentStartupStats(
			activePeers = 3,
			totalPeers = 17,
			connectedSeeders = 2,
			downloadSpeedBytesPerSecond = 1_887_436.8,
			preloadedBytes = 25_165_824,
			preloadSizeBytes = 52_428_800,
		)
		val streamer = object : TorrentStreamer {
			override suspend fun prepare(result: TorrentSearchResult): TorrentSource =
				TorrentSource("torrent://unused")

			override suspend fun prepare(
				result: TorrentSearchResult,
				onStartupStats: (TorrentStartupStats) -> Unit,
			): TorrentSource {
				onStartupStats(stats)
				gate.await()
				return TorrentSource("torrent://stream/movie.mkv")
			}
		}
		val selected = TorrentSearchResult(
			id = "movie",
			title = "Movie",
			magnetUri = "magnet:?xt=urn:btih:movie",
		)
		val controller = PlaybackController(
			scope = this,
			streamer = streamer,
		)

		controller.play(selected)
		runCurrent()

		assertEquals("Preparing stream…", controller.state.value.status)
		assertEquals(stats, controller.state.value.startupStats)

		controller.exit()
	}
}
