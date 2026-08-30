package sk.ziacik.androidstreamplayer.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
	fun resumeActionsConsumeOriginalLongPressUntilItsKeyUp() {
		val repeatedDown = resumeActionsKeyDecision(
			isConfirm = true,
			isUp = false,
			waitingForRelease = true,
		)
		assertTrue(repeatedDown.consume)
		assertTrue(repeatedDown.waitingForRelease)

		val release = resumeActionsKeyDecision(
			isConfirm = true,
			isUp = true,
			waitingForRelease = repeatedDown.waitingForRelease,
		)
		assertTrue(release.consume)
		assertFalse(release.waitingForRelease)

		val nextPress = resumeActionsKeyDecision(
			isConfirm = true,
			isUp = false,
			waitingForRelease = release.waitingForRelease,
		)
		assertFalse(nextPress.consume)
		assertFalse(nextPress.waitingForRelease)
	}

	@Test
	fun activatingResumeCardCancelsWhileItIsStarting() {
		assertEquals(ResumeWatchingActivation.Resume, resumeWatchingActivation(isStarting = false))
		assertEquals(ResumeWatchingActivation.Cancel, resumeWatchingActivation(isStarting = true))
	}

	@Test
	fun longPressOptionsAreDisabledWhileResumeIsStarting() {
		assertTrue(resumeWatchingOptionsEnabled(isStarting = false))
		assertFalse(resumeWatchingOptionsEnabled(isStarting = true))
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
