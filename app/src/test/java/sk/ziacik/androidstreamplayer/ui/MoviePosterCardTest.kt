package sk.ziacik.androidstreamplayer.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

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
