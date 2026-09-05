package sk.ziacik.androidstreamplayer.playback

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import sk.ziacik.androidstreamplayer.catalog.Movie
import sk.ziacik.androidstreamplayer.search.TorrentSearchResult
import sk.ziacik.androidstreamplayer.subtitle.SubtitleOption
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
	fun `movie playback starts without waiting for subtitle search`() = runTest {
		val searchResult = CompletableDeferred<List<SubtitleOption>>()
		val source = TorrentSource("torrent://stream/matrix.mkv")
		var played: TorrentSource? = null
		val controller = PlaybackController(
			scope = this,
			streamer = TorrentStreamer { source },
			subtitleSearch = { _, _ -> searchResult.await() },
			onStreamReady = { played = it },
		)

		controller.play(movie(), result("matrix"))
		runCurrent()

		assertEquals(source, played)
		assertEquals("Playing", controller.state.value.status)
		assertTrue(controller.state.value.subtitles.isSearching)

		val option = option("sk-1", "sk", "Slovak")
		searchResult.complete(listOf(option))
		advanceUntilIdle()

		assertFalse(controller.state.value.subtitles.isSearching)
		assertEquals(listOf(option), controller.state.value.subtitles.options)
	}

	@Test
	fun `subtitle search failure is visible and does not block playback`() = runTest {
		var played = false
		val controller = PlaybackController(
			scope = this,
			streamer = TorrentStreamer { TorrentSource("torrent://stream/matrix.mkv") },
			subtitleSearch = { _, _ -> throw IllegalStateException("provider down") },
			onStreamReady = { played = true },
		)

		controller.play(movie(), result("matrix"))
		advanceUntilIdle()

		assertTrue(played)
		assertEquals("Playing", controller.state.value.status)
		assertFalse(controller.state.value.subtitles.isSearching)
		assertEquals("Could not find subtitles", controller.state.value.subtitles.message)
	}

	@Test
	fun `selecting subtitle downloads it and sends track to player`() = runTest {
		val option = option("sk-1", "sk", "Slovak")
		val track = SubtitleTrack(
			path = "/tmp/matrix-sk.srt",
			language = "sk",
			mimeType = "application/x-subrip",
			label = "Slovak",
		)
		var selectedTrack: SubtitleTrack? = null
		val controller = PlaybackController(
			scope = this,
			streamer = TorrentStreamer { TorrentSource("torrent://stream/matrix.mkv") },
			subtitleSearch = { _, _ -> listOf(option) },
			subtitleDownload = { _, _, selected ->
				assertEquals(option, selected)
				track
			},
			onSubtitleSelected = { selectedTrack = it },
		)

		controller.play(movie(), result("matrix"))
		advanceUntilIdle()
		controller.selectSubtitle(option)
		advanceUntilIdle()

		assertEquals(track, selectedTrack)
		assertEquals(option.id, controller.state.value.subtitles.selectedId)
		assertNull(controller.state.value.subtitles.loadingId)
	}

	@Test
	fun `selecting off disables subtitles`() = runTest {
		val selected = mutableListOf<SubtitleTrack?>()
		val controller = PlaybackController(
			scope = this,
			streamer = TorrentStreamer { TorrentSource("torrent://stream/matrix.mkv") },
			onSubtitleSelected = { selected += it },
		)

		controller.play(movie(), result("matrix"))
		advanceUntilIdle()
		controller.selectSubtitle(null)

		assertEquals(listOf<SubtitleTrack?>(null), selected)
		assertNull(controller.state.value.subtitles.selectedId)
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
		assertTrue(controller.state.value.subtitles.options.isEmpty())
	}

	@Test
	fun `direct magnet skips subtitle search`() = runTest {
		val magnet = "magnet:?xt=urn:btih:ABC123"
		var prepared: TorrentSearchResult? = null
		var subtitleSearchCalled = false
		val controller = PlaybackController(
			scope = this,
			streamer = TorrentStreamer { result ->
				prepared = result
				TorrentSource("torrent://stream/direct.mkv")
			},
			subtitleSearch = { _, _ ->
				subtitleSearchCalled = true
				emptyList()
			},
		)

		controller.playMagnet(magnet)
		advanceUntilIdle()

		assertEquals("direct-magnet", prepared?.id)
		assertEquals("Magnet", prepared?.source)
		assertEquals(magnet, prepared?.magnetUri)
		assertFalse(subtitleSearchCalled)
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

	private fun option(id: String, language: String, label: String) = SubtitleOption(
		id = id,
		language = language,
		label = label,
		release = "The.Matrix.1999.1080p.BluRay.x264-GROUP",
		downloads = 100,
	)
}
