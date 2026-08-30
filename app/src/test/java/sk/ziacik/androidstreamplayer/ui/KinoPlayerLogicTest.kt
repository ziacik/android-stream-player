package sk.ziacik.androidstreamplayer.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class KinoPlayerLogicTest {
    @Test
    fun `seek backward clamps at start of movie`() {
        assertEquals(0L, seekTargetMs(currentMs = 3_000L, deltaMs = -10_000L, durationMs = 120_000L))
    }

    @Test
    fun `seek forward clamps at end of movie`() {
        assertEquals(120_000L, seekTargetMs(currentMs = 118_000L, deltaMs = 10_000L, durationMs = 120_000L))
    }

    @Test
    fun `seek with unknown duration still clamps at zero`() {
        assertEquals(0L, seekTargetMs(currentMs = 2_000L, deltaMs = -10_000L, durationMs = null))
        assertEquals(12_000L, seekTargetMs(currentMs = 2_000L, deltaMs = 10_000L, durationMs = null))
    }

    @Test
    fun `playback time uses compact movie clock`() {
        assertEquals("0:07", formatPlaybackTime(7_000L))
        assertEquals("12:34", formatPlaybackTime(754_000L))
        assertEquals("1:02:03", formatPlaybackTime(3_723_000L))
    }
}
