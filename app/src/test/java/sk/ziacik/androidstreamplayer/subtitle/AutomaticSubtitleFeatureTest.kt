package sk.ziacik.androidstreamplayer.subtitle

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import sk.ziacik.androidstreamplayer.catalog.Movie
import sk.ziacik.androidstreamplayer.playback.PlaybackController
import sk.ziacik.androidstreamplayer.search.TorrentSearchResult

class AutomaticSubtitleFeatureTest {
	@Test
	fun `playback accepts movie metadata for subtitle lookup`() {
		val method = PlaybackController::class.java.methods.singleOrNull { method ->
			method.name == "play" &&
				method.parameterTypes.contentEquals(
					arrayOf(Movie::class.java, TorrentSearchResult::class.java),
				)
		}

		assertNotNull(method)
	}

	@Test
	fun `open subtitles provider is available`() {
		val providerExists = runCatching {
			Class.forName("sk.ziacik.androidstreamplayer.subtitle.OpenSubtitlesSubtitleProvider")
		}.isSuccess

		assertTrue(providerExists)
	}
}
