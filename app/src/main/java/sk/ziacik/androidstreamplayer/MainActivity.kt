package sk.ziacik.androidstreamplayer

import android.os.Bundle
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import sk.ziacik.androidstreamplayer.search.FakeTorrentSearchProvider
import sk.ziacik.androidstreamplayer.search.SearchController
import sk.ziacik.androidstreamplayer.ui.SearchScreen
import sk.ziacik.androidstreamplayer.ui.theme.AndroidStreamPlayerTheme

class MainActivity : ComponentActivity() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var searchController: SearchController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY

        searchController = SearchController(
            scope = appScope,
            provider = FakeTorrentSearchProvider(),
        )

        setContent {
            AndroidStreamPlayerTheme {
                SearchScreen(controller = searchController)
            }
        }
    }

    override fun onDestroy() {
        appScope.cancel()
        super.onDestroy()
    }
}
