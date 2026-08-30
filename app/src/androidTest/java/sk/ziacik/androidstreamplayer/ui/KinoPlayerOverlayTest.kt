package sk.ziacik.androidstreamplayer.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test

class KinoPlayerOverlayTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun overlayShowsMovieProgressAndTvControls() {
        composeRule.setContent {
            KinoPlayerOverlay(
                title = "Alien",
                quality = "2160p",
                positionMs = 754_000L,
                durationMs = 3_723_000L,
                bufferedPositionMs = 1_200_000L,
                isPlaying = true,
                focusedControl = KinoPlayerControl.PLAY_PAUSE,
            )
        }

        composeRule.onNodeWithText("Alien").assertIsDisplayed()
        composeRule.onNodeWithText("2160p").assertIsDisplayed()
        composeRule.onNodeWithText("12:34").assertIsDisplayed()
        composeRule.onNodeWithText("1:02:03").assertIsDisplayed()
        composeRule.onNodeWithTag("player-seek-back").assertIsDisplayed()
        composeRule.onNodeWithTag("player-play-pause").assertIsDisplayed()
        composeRule.onNodeWithTag("player-seek-forward").assertIsDisplayed()
    }
}
