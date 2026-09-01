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
    fun overlayShowsMovieTitleReleaseBadgesProgressAndTvControls() {
        composeRule.setContent {
            KinoPlayerOverlay(
                title = "Dune: Part Two",
                badges = listOf("4K", "REMUX", "DV", "HDR10", "HEVC", "TrueHD", "Atmos", "7.1", "ENG", "2024"),
                positionMs = 754_000L,
                durationMs = 3_723_000L,
                bufferedPositionMs = 1_200_000L,
                isPlaying = true,
                focusedFocus = KinoPlayerFocus.PLAY_PAUSE,
                scrubPositionMs = null,
            )
        }

        composeRule.onNodeWithText("Dune: Part Two").assertIsDisplayed()
        listOf("4K", "REMUX", "DV", "HDR10", "HEVC", "TrueHD", "Atmos", "7.1", "ENG", "2024").forEach { badge ->
            composeRule.onNodeWithText(badge).assertIsDisplayed()
        }
        composeRule.onNodeWithText("12:34").assertIsDisplayed()
        composeRule.onNodeWithText("1:02:03").assertIsDisplayed()
        composeRule.onNodeWithTag("player-progress").assertIsDisplayed()
        composeRule.onNodeWithTag("player-seek-back").assertIsDisplayed()
        composeRule.onNodeWithTag("player-play-pause").assertIsDisplayed()
        composeRule.onNodeWithTag("player-seek-forward").assertIsDisplayed()
    }

    @Test
    fun focusedProgressShowsScrubTargetTime() {
        composeRule.setContent {
            KinoPlayerOverlay(
                title = "Alien",
                badges = emptyList(),
                positionMs = 754_000L,
                durationMs = 3_723_000L,
                bufferedPositionMs = 1_200_000L,
                isPlaying = true,
                focusedFocus = KinoPlayerFocus.PROGRESS,
                scrubPositionMs = 1_500_000L,
            )
        }

        composeRule.onNodeWithTag("player-progress").assertIsDisplayed()
        composeRule.onNodeWithTag("player-scrub-time").assertIsDisplayed()
        composeRule.onNodeWithText("25:00").assertIsDisplayed()
    }
}
