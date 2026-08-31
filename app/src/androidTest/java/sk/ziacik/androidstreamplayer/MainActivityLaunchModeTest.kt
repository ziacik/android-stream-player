package sk.ziacik.androidstreamplayer

import android.content.ComponentName
import android.content.pm.ActivityInfo
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test

class MainActivityLaunchModeTest {
	@Test
	fun launcherActivityDoesNotStackDuplicateAppInstances() {
		val context = InstrumentationRegistry.getInstrumentation().targetContext
		val activityInfo = context.packageManager.getActivityInfo(
			ComponentName(context, MainActivity::class.java),
			0,
		)

		assertEquals(ActivityInfo.LAUNCH_SINGLE_TASK, activityInfo.launchMode)
	}
}
