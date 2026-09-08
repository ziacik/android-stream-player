package sk.ziacik.androidstreamplayer

import org.junit.Assert.assertTrue
import org.junit.Test

class OpenSubtitlesBuildConfigTest {
	@Test
	fun `OpenSubtitles consumer key is bundled for runtime use`() {
		assertTrue(BuildConfig.OPENSUBTITLES_API_KEY.isNotBlank())
	}
}
