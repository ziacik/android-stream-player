package sk.ziacik.androidstreamplayer.playback

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import sk.ziacik.androidstreamplayer.catalog.Movie
import sk.ziacik.androidstreamplayer.search.TorrentSearchResult
import sk.ziacik.androidstreamplayer.subtitle.SubtitleTrack
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
	fun `movie playback resolves subtitle and passes it to player`() = runTest {
		val selectedMovie = movie()
		val selectedResult = result("matrix")
		val subtitle = SubtitleTrack(
			path = "/tmp/matrix-sk.srt",
			language = "sk",
			mimeType = "application/x-subrip",
			label = "Slovak",
		)
		var lookupMovie: Movie? = null
		var lookupResult: TorrentSearchResult? = null
		var playedSubtitle: SubtitleTrack? = null
		val controller = PlaybackController(
			scope = this,
			streamer = TorrentStreamer { TorrentSource("torrent://stream/matrix.mkv") },
			subtitleLookup = { foundMovie, foundResult ->
				lookupMovie = foundMovie
				lookupResult = foundResult
				subtitle
			},
			onStreamReady = { _, foundSubtitle -> playedSubtitle = foundSubtitle },
		)

		controller.play(selectedMovie, selectedResult)
		advanceUntilIdle()

		assertEquals(selectedMovie, lookupMovie)
		assertEquals(selectedResult, lookupResult)
		assertEquals(subtitle, playedSubtitle)
		assertEquals("Playing", controller.state.value.status)
	}

	@Test
	fun `subtitle lookup failure does not block movie playback`() = runTest {
		var lookupCalled = false
		var playedSubtitle: SubtitleTrack? = SubtitleTrack("unexpected", "sk", "text/plain", "Unexpected")
		val controller = PlaybackController(
			scope = this,
			streamer = TorrentStreamer { TorrentSource("torrent://stream/matrix.mkv") },
			subtitleLookup = { _, _ ->
				lookupCalled = true
				throw IllegalStateException("provider down")
			},
			onStreamReady = { _, foundSubtitle -> playedSubtitle = foundSubtitle },
		)

		controller.play(movie(), result("matrix"))
		advanceUntilIdle()

		assertTrue(lookupCalled)
		assertNull(playedSubtitle)
		assertEquals("Playing", controller.state.value.status)
	}

	@Test
	fun `subtitle lookup timeout does not block movie playback`() = runTest {
		var lookupStarted = false
		var playedSubtitle: SubtitleTrack? = SubtitleTrack("unexpected", "sk", "text/plain", "Unexpected")
		val controller = PlaybackController(
			scope = this,
			streamer = TorrentStreamer { TorrentSource("torrent://stream/matrix.mkv") },
			subtitleLookup = { _, _ ->
				lookupStarted = true
				awaitCancellation()
			},
			subtitleTimeoutMs = 500L,
			onStreamReady = { _, foundSubtitle -> playedSubtitle = foundSubtitle },
		)

		controller.play(movie(), result("matrix"))
		advanceUntilIdle()

		assertTrue(lookupStarted)
		assertNull(playedSubtitle)
		assertEquals("Playing", controller.state.value.status)
	}

	@Test
	fun `starting another torrent replaces the active startup`() = runTest {
		val gate = CompletableDeferred<Unit>()
		val started = mutableListOf<String>()
		val controller = PlaybackController(
			scope = this,
			streamer = TorrentStreamer { selected ->
				started += selected.id
				gate.await()
				TorrentSource("torrent://stream/${selected.id}.mkv")
			},
		)

		controller.play(result("first"))
		runCurrent()
		assertEquals(listOf("first"), started)
		assertEquals("first", controller.state.value.startingResultId)

		controller.play(result("second"))
		runCurrent()

		assertEquals(listOf("first", "second"), started)
		assertEquals("second", controller.state.value.selectedResult?.id)
		assertEquals("second", controller.state.value.startingResultId)
		controller.exit()
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

	private fun movie() = Movie(
		tmdbId = 603,
		imdbId = "tt0133093",
		title = "The Matrix",
		originalTitle = "The Matrix",
		releaseYear = 1999,
		overview = null,
		voteAverage = null,
		posterPath = null,
		backdropPath = null,
	)

	private fun result(id: String) = TorrentSearchResult(
		id = id,
		title = id,
		magnetUri = "magnet:?xt=urn:btih:$id",
		seeders = 10,
	)
}
