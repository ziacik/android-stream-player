package sk.ziacik.androidstreamplayer.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MoviePosterCardTest {
	@Test
	fun tmdbRatingLabelFormatsPositiveRating() {
		assertEquals("★ 8.2", tmdbRatingLabel(8.24))
	}

	@Test
	fun tmdbRatingLabelHidesMissingOrZeroRating() {
		assertNull(tmdbRatingLabel(null))
		assertNull(tmdbRatingLabel(0.0))
	}
}
