package sk.ziacik.androidstreamplayer.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
    fun `resume seek ignores empty positions and clamps to movie duration`() {
        assertNull(resumeSeekTargetMs(resumePositionMs = null, durationMs = 600_000L))
        assertNull(resumeSeekTargetMs(resumePositionMs = 0L, durationMs = 600_000L))
        assertNull(resumeSeekTargetMs(resumePositionMs = -1L, durationMs = 600_000L))
        assertEquals(300_000L, resumeSeekTargetMs(resumePositionMs = 300_000L, durationMs = null))
        assertEquals(600_000L, resumeSeekTargetMs(resumePositionMs = 700_000L, durationMs = 600_000L))
    }

    @Test
    fun `playback time uses compact movie clock`() {
        assertEquals("0:07", formatPlaybackTime(7_000L))
        assertEquals("12:34", formatPlaybackTime(754_000L))
        assertEquals("1:02:03", formatPlaybackTime(3_723_000L))
    }

    @Test
    fun `watch progress is persisted at five second intervals`() {
        assertFalse(shouldPersistWatchProgress(lastPersistedMs = null, positionMs = 4_999L, durationMs = 120_000L))
        assertTrue(shouldPersistWatchProgress(lastPersistedMs = null, positionMs = 5_000L, durationMs = 120_000L))
        assertFalse(shouldPersistWatchProgress(lastPersistedMs = 5_000L, positionMs = 9_999L, durationMs = 120_000L))
        assertTrue(shouldPersistWatchProgress(lastPersistedMs = 5_000L, positionMs = 10_000L, durationMs = 120_000L))
    }

    @Test
    fun `watch progress ignores invalid playback duration`() {
        assertFalse(shouldPersistWatchProgress(lastPersistedMs = null, positionMs = 5_000L, durationMs = null))
        assertFalse(shouldPersistWatchProgress(lastPersistedMs = null, positionMs = 5_000L, durationMs = 0L))
    }
}
