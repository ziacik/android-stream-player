package sk.ziacik.androidstreamplayer.playback

import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import sk.ziacik.androidstreamplayer.search.TorrentSearchResult
import sk.ziacik.androidstreamplayer.torrent.TorrentSource
import sk.ziacik.androidstreamplayer.torrent.TorrentStreamer

class PlaybackControllerTest {
	@Test
	fun `play prepares torrent and reports playing`() = runTest {
		val selected = result("matrix")
		val source = TorrentSource("torrent://stream/matrix.mkv")
		var prepared: TorrentSearchResult? = null
		var played: TorrentSource? = null
		val controller = PlaybackController(
			scope = this,
			streamer = TorrentStreamer { result ->
				prepared = result
				source
			},
			onStreamReady = { played = it },
		)

		controller.play(selected)

		assertEquals(selected, controller.state.value.selectedResult)
		assertEquals("Preparing stream…", controller.state.value.status)

		advanceUntilIdle()

		assertEquals(selected, prepared)
		assertEquals(source, played)
		assertEquals("Playing", controller.state.value.status)
	}

	@Test
	fun `streamer failure becomes stream failed`() = runTest {
		val controller = PlaybackController(
			scope = this,
			streamer = TorrentStreamer { throw IllegalStateException("boom") },
		)

		controller.play(result("matrix"))
		advanceUntilIdle()

		assertEquals("Stream failed", controller.state.value.status)
	}

	@Test
	fun `missing streamer becomes streaming unavailable`() = runTest {
		val controller = PlaybackController(scope = this, streamer = null)

		controller.play(result("matrix"))
		advanceUntilIdle()

		assertEquals("Streaming unavailable", controller.state.value.status)
	}

	@Test
	fun `playback callback failure becomes playback failed`() = runTest {
		val controller = PlaybackController(
			scope = this,
			streamer = TorrentStreamer { TorrentSource("torrent://stream/matrix.mkv") },
			onStreamReady = { throw IllegalStateException("player failed") },
		)

		controller.play(result("matrix"))
		advanceUntilIdle()

		assertEquals("Playback failed", controller.state.value.status)
	}

	@Test
	fun `exit clears playback state`() = runTest {
		val controller = PlaybackController(
			scope = this,
			streamer = TorrentStreamer { TorrentSource("torrent://stream/matrix.mkv") },
		)

		controller.play(result("matrix"))
		advanceUntilIdle()
		controller.exit()

		assertNull(controller.state.value.selectedResult)
		assertNull(controller.state.value.status)
	}

	@Test
	fun `direct magnet creates a dedicated magnet result`() = runTest {
		val magnet = "magnet:?xt=urn:btih:ABC123"
		var prepared: TorrentSearchResult? = null
		val controller = PlaybackController(
			scope = this,
			streamer = TorrentStreamer { result ->
				prepared = result
				TorrentSource("torrent://stream/direct.mkv")
			},
		)

		controller.playMagnet(magnet)
		advanceUntilIdle()

		assertEquals("direct-magnet", prepared?.id)
		assertEquals("Magnet", prepared?.source)
		assertEquals(magnet, prepared?.magnetUri)
	}

	private fun result(id: String) = TorrentSearchResult(
		id = id,
		title = id,
		magnetUri = "magnet:?xt=urn:btih:$id",
		seeders = 10,
	)
}
