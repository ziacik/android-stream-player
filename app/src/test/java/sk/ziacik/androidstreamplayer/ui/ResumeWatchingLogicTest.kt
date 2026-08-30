package sk.ziacik.androidstreamplayer.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ResumeWatchingLogicTest {
	@Test
	fun longPressOpensActionsInsteadOfRemoving() {
		assertEquals(
			ResumeWatchingKeyAction.OpenActions,
			resumeWatchingKeyAction(
				isConfirm = true,
				isDown = true,
				isUp = false,
				repeatCount = 1,
				longPressTriggered = false,
			),
		)
	}

	@Test
	fun preparingResumeShowsStartingUntilPlaybackFailsOrStarts() {
		assertEquals(603, startingResumeMovieId("Preparing stream…", 603))
		assertNull(startingResumeMovieId("Stream failed", 603))
		assertNull(startingResumeMovieId("Playback failed", 603))
		assertNull(startingResumeMovieId("Playing", 603))
		assertNull(startingResumeMovieId("Preparing stream…", null))
	}
}
