package sk.ziacik.androidstreamplayer.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.Rule
import org.junit.Test
import sk.ziacik.androidstreamplayer.search.FakeTorrentSearchProvider
import sk.ziacik.androidstreamplayer.search.SearchController

class SearchScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun searchResultCanBeSelected() {
        val controller = SearchController(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
            provider = FakeTorrentSearchProvider(),
        )
        composeRule.setContent {
            SearchScreen(controller = controller)
        }

        composeRule.onNodeWithTag("search-field").performTextInput("Alien")
        composeRule.onNodeWithTag("search-button").performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("2160p").fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithTag("result-Alien-2160p").performClick()
        composeRule.onNodeWithText("Streaming not implemented yet").assertIsDisplayed()
    }
}
