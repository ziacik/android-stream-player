package sk.ziacik.androidstreamplayer.ui

import org.junit.Assert.assertTrue
import org.junit.Test

class HomeScreenStateTest {
	@Test
	fun homeAndSearchAreBothHeaderFocusTargets() {
		assertTrue(isHeaderTarget(HomeFocusTarget.Home))
		assertTrue(isHeaderTarget(HomeFocusTarget.Search))
	}

	private fun isHeaderTarget(target: HomeFocusTarget): Boolean = when (target) {
		HomeFocusTarget.Home,
		HomeFocusTarget.Search,
		-> true

		is HomeFocusTarget.Resume,
		is HomeFocusTarget.Trending,
		-> false
	}
}
