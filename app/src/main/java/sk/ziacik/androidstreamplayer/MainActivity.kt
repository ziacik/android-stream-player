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
import sk.ziacik.androidstreamplayer.player.Media3PlayerPort
import sk.ziacik.androidstreamplayer.search.KnabenTorrentSearchProvider
import sk.ziacik.androidstreamplayer.search.SearchController
import sk.ziacik.androidstreamplayer.torrent.LocalTorrServerRuntime
import sk.ziacik.androidstreamplayer.torrent.TorrServerClient
import sk.ziacik.androidstreamplayer.torrent.TorrServerProcess
import sk.ziacik.androidstreamplayer.torrent.TorrServerRuntime
import sk.ziacik.androidstreamplayer.torrent.TorrServerTorrentStreamer
import sk.ziacik.androidstreamplayer.ui.SearchScreen
import sk.ziacik.androidstreamplayer.ui.theme.AndroidStreamPlayerTheme

@UnstableApi
class MainActivity : ComponentActivity() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val cleanupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private lateinit var searchController: SearchController
    private lateinit var playerPort: Media3PlayerPort
    private lateinit var torrentRuntime: TorrServerRuntime

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY

        playerPort = Media3PlayerPort(this)

        val torrServerClient = TorrServerClient()
        val torrServerProcess = TorrServerProcess(
            binaryPath = File(applicationInfo.nativeLibraryDir, TORRSERVER_BINARY).absolutePath,
            dataPath = File(noBackupFilesDir, TORRSERVER_DATA_DIR).absolutePath,
            client = torrServerClient,
            scope = cleanupScope,
        )
        torrentRuntime = LocalTorrServerRuntime(
            process = torrServerProcess,
            client = torrServerClient,
        )
        val torrentStreamer = TorrServerTorrentStreamer(torrentRuntime)

        searchController = SearchController(
            scope = appScope,
            provider = KnabenTorrentSearchProvider(),
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

        if (::torrentRuntime.isInitialized) {
            cleanupScope.launch {
                try {
                    torrentRuntime.stop()
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
        const val TORRSERVER_BINARY = "libtorrserver.so"
        const val TORRSERVER_DATA_DIR = "torrserver"
    }
}
