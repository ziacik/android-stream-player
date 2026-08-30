package sk.ziacik.androidstreamplayer.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import sk.ziacik.androidstreamplayer.search.TorrentSearchResult

class PlaybackUiStateTest {
	@Test
	fun `preparing state exposes the starting torrent id`() {
		val state = PlaybackUiState(
			selectedResult = result("first"),
			status = "Preparing stream...",
		)

		assertEquals("first", state.startingResultId)
		assertNull(state.startupErrorMessage)
	}

	@Test
	fun `failed startup exposes a friendly retry message`() {
		listOf("Streaming unavailable", "Stream failed", "Playback failed").forEach { status ->
			val state = PlaybackUiState(
				selectedResult = result("first"),
				status = status,
			)

			assertNull(state.startingResultId)
			assertEquals("Could not start stream. Try another version.", state.startupErrorMessage)
		}
	}

	@Test
	fun `idle and playing states expose no startup feedback`() {
		assertNull(PlaybackUiState().startingResultId)
		assertNull(PlaybackUiState().startupErrorMessage)

		val playing = PlaybackUiState(
			selectedResult = result("first"),
			status = "Playing",
		)
		assertNull(playing.startingResultId)
		assertNull(playing.startupErrorMessage)
	}

	private fun result(id: String) = TorrentSearchResult(
		id = id,
		title = id,
		magnetUri = "magnet:?xt=urn:btih:$id",
	)
}
