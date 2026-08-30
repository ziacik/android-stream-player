package sk.ziacik.androidstreamplayer.ui

import org.junit.Assert.assertEquals
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
}
