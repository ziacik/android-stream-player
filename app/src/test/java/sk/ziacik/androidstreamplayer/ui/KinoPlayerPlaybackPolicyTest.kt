package sk.ziacik.androidstreamplayer.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KinoPlayerPlaybackPolicyTest {
	@Test
	fun bufferingWhilePlaybackIsRequestedDoesNotRevealOverlay() {
		assertFalse(
			shouldRevealOverlayForPlaybackState(
				isPlaying = false,
				playWhenReady = true,
			),
		)
	}

	@Test
	fun pausedPlaybackRevealsOverlay() {
		assertTrue(
			shouldRevealOverlayForPlaybackState(
				isPlaying = false,
				playWhenReady = false,
			),
		)
	}
}
