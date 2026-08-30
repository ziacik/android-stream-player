package sk.ziacik.androidstreamplayer.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class KinoPlayerNavigationTest {
    @Test
    fun hiddenOverlayHorizontalKeySeeksWithoutOpeningOverlay() {
        val action = kinoHorizontalAction(
            overlayVisible = false,
            focus = KinoPlayerFocus.PLAY_PAUSE,
            direction = -1,
            repeatCount = 0,
        )

        assertEquals(KinoPlayerAction.SeekBy(-10_000L, showOverlay = false), action)
    }

    @Test
    fun visibleOverlayHorizontalKeyMovesBetweenButtons() {
        assertEquals(
            KinoPlayerAction.MoveFocus(KinoPlayerFocus.SEEK_BACK),
            kinoHorizontalAction(
                overlayVisible = true,
                focus = KinoPlayerFocus.PLAY_PAUSE,
                direction = -1,
                repeatCount = 0,
            ),
        )
        assertEquals(
            KinoPlayerAction.MoveFocus(KinoPlayerFocus.PLAY_PAUSE),
            kinoHorizontalAction(
                overlayVisible = true,
                focus = KinoPlayerFocus.SEEK_BACK,
                direction = 1,
                repeatCount = 0,
            ),
        )
    }

    @Test
    fun progressFocusUsesAcceleratingScrubInsteadOfButtonNavigation() {
        assertEquals(
            KinoPlayerAction.ScrubBy(10_000L),
            kinoHorizontalAction(
                overlayVisible = true,
                focus = KinoPlayerFocus.PROGRESS,
                direction = 1,
                repeatCount = 0,
            ),
        )
        assertEquals(
            KinoPlayerAction.ScrubBy(30_000L),
            kinoHorizontalAction(
                overlayVisible = true,
                focus = KinoPlayerFocus.PROGRESS,
                direction = 1,
                repeatCount = 10,
            ),
        )
        assertEquals(
            KinoPlayerAction.ScrubBy(60_000L),
            kinoHorizontalAction(
                overlayVisible = true,
                focus = KinoPlayerFocus.PROGRESS,
                direction = 1,
                repeatCount = 30,
            ),
        )
        assertEquals(
            KinoPlayerAction.ScrubBy(300_000L),
            kinoHorizontalAction(
                overlayVisible = true,
                focus = KinoPlayerFocus.PROGRESS,
                direction = 1,
                repeatCount = 60,
            ),
        )
    }

    @Test
    fun upAndDownMoveBetweenControlsAndProgress() {
        assertEquals(
            KinoPlayerFocus.PROGRESS,
            kinoVerticalFocus(KinoPlayerFocus.SEEK_FORWARD, direction = -1),
        )
        assertEquals(
            KinoPlayerFocus.PLAY_PAUSE,
            kinoVerticalFocus(KinoPlayerFocus.PROGRESS, direction = 1),
        )
    }
}
