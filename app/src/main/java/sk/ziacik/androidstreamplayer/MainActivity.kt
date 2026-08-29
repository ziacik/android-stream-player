package sk.ziacik.androidstreamplayer

import android.os.Bundle
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.media3.common.util.UnstableApi
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.libtorrent4j.SessionManager
import sk.ziacik.androidstreamplayer.player.Media3PlayerPort
import sk.ziacik.androidstreamplayer.search.FakeTorrentSearchProvider
import sk.ziacik.androidstreamplayer.search.SearchController
import sk.ziacik.androidstreamplayer.torrent.LibtorrentSessionBackend
import sk.ziacik.androidstreamplayer.torrent.LibtorrentTorrentStreamer
import sk.ziacik.androidstreamplayer.ui.SearchScreen
import sk.ziacik.androidstreamplayer.ui.theme.AndroidStreamPlayerTheme

@UnstableApi
class MainActivity : ComponentActivity() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val cleanupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private lateinit var searchController: SearchController
    private lateinit var playerPort: Media3PlayerPort
    private lateinit var torrentBackend: LibtorrentSessionBackend

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY

        playerPort = Media3PlayerPort(this)
        torrentBackend = LibtorrentSessionBackend(
            sessionManager = SessionManager(),
            rootDir = File(cacheDir, "torrent-streams"),
        )
        val torrentStreamer = LibtorrentTorrentStreamer(torrentBackend)

        searchController = SearchController(
            scope = appScope,
            provider = FakeTorrentSearchProvider(),
            streamer = torrentStreamer,
            onStreamReady = { source ->
                playerPort.prepare(source)
                playerPort.play()
            },
        )

        setContent {
            AndroidStreamPlayerTheme {
                SearchScreen(
                    controller = searchController,
                    player = playerPort.player,
                )
            }
        }

        intent.getStringExtra(EXTRA_MAGNET)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.let(::startMagnet)
    }

    override fun onStop() {
        if (::playerPort.isInitialized) {
            playerPort.pause()
        }
        super.onStop()
    }

    override fun onDestroy() {
        if (::playerPort.isInitialized) {
            playerPort.release()
        }
        appScope.cancel()

        if (::torrentBackend.isInitialized) {
            cleanupScope.launch {
                try {
                    torrentBackend.stop()
                } finally {
                    cleanupScope.cancel()
                }
            }
        } else {
            cleanupScope.cancel()
        }

        super.onDestroy()
    }

    private fun startMagnet(magnet: String) {
        searchController.setQuery(magnet)
        searchController.search()
        searchController.state.value.results.singleOrNull()?.let(searchController::select)
    }

    private companion object {
        const val EXTRA_MAGNET = "magnet"
    }
}
