package sk.ziacik.androidstreamplayer

import android.content.Intent
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
import sk.ziacik.androidstreamplayer.catalog.MovieBrowseController
import sk.ziacik.androidstreamplayer.catalog.MovieSearchController
import sk.ziacik.androidstreamplayer.catalog.TmdbMovieCatalog
import sk.ziacik.androidstreamplayer.playback.PlaybackController
import sk.ziacik.androidstreamplayer.player.Media3PlayerPort
import sk.ziacik.androidstreamplayer.search.KnabenTorrentSearchProvider
import sk.ziacik.androidstreamplayer.search.TorrentSearchController
import sk.ziacik.androidstreamplayer.subtitle.PodnapisiSubtitleProvider
import sk.ziacik.androidstreamplayer.torrent.LocalTorrServerRuntime
import sk.ziacik.androidstreamplayer.torrent.TorrServerClient
import sk.ziacik.androidstreamplayer.torrent.TorrServerProcess
import sk.ziacik.androidstreamplayer.torrent.TorrServerRuntime
import sk.ziacik.androidstreamplayer.torrent.TorrServerTorrentStreamer
import sk.ziacik.androidstreamplayer.ui.KinoApp
import sk.ziacik.androidstreamplayer.ui.KinoPlayerScreen
import sk.ziacik.androidstreamplayer.ui.WatchProgressEffect
import sk.ziacik.androidstreamplayer.ui.theme.AndroidStreamPlayerTheme
import sk.ziacik.androidstreamplayer.watch.SharedPreferencesWatchProgressStorage
import sk.ziacik.androidstreamplayer.watch.WatchProgressRepository

@UnstableApi
class MainActivity : ComponentActivity() {
	private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
	private val cleanupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

	private lateinit var movieBrowseController: MovieBrowseController
	private lateinit var movieSearchController: MovieSearchController
	private lateinit var torrentSearchController: TorrentSearchController
	private lateinit var playbackController: PlaybackController
	private lateinit var playerPort: Media3PlayerPort
	private lateinit var torrentRuntime: TorrServerRuntime
	private lateinit var watchProgressRepository: WatchProgressRepository

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)

		@Suppress("DEPRECATION")
		window.decorView.systemUiVisibility =
			View.SYSTEM_UI_FLAG_FULLSCREEN or
				View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
				View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY

		playerPort = Media3PlayerPort(this)
		watchProgressRepository = WatchProgressRepository(
			storage = SharedPreferencesWatchProgressStorage(applicationContext),
		)

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
		val movieCatalog = TmdbMovieCatalog(BuildConfig.TMDB_API_KEY)
		val subtitleProvider = PodnapisiSubtitleProvider(
			cacheDir = File(cacheDir, SUBTITLE_CACHE_DIR),
		)

		movieBrowseController = MovieBrowseController(
			scope = appScope,
			loadTrending = movieCatalog::trending,
		)
		movieSearchController = MovieSearchController(
			scope = appScope,
			catalog = movieCatalog,
		)
		torrentSearchController = TorrentSearchController(
			scope = appScope,
			catalog = movieCatalog,
			provider = KnabenTorrentSearchProvider(),
		)
		playbackController = PlaybackController(
			scope = appScope,
			streamer = torrentStreamer,
			subtitleLookup = subtitleProvider::find,
			onStreamReady = { source, subtitle ->
				playerPort.prepare(source, subtitle)
				playerPort.play()
			},
		)

		setContent {
			AndroidStreamPlayerTheme {
				KinoApp(
					movieBrowseController = movieBrowseController,
					movieSearchController = movieSearchController,
					torrentSearchController = torrentSearchController,
					playbackController = playbackController,
					watchProgressRepository = watchProgressRepository,
					playerContent = { movie, result, resumePositionMs, onExit ->
						WatchProgressEffect(
							player = playerPort.player,
							movie = movie,
							result = result,
							resumePositionMs = resumePositionMs,
							repository = watchProgressRepository,
						)
						KinoPlayerScreen(
							player = playerPort.player,
							movieTitle = movie?.title ?: "Now playing",
							result = result,
							onExit = onExit,
						)
					},
				)
			}
		}

		handleIntent(intent)
	}

	override fun onNewIntent(intent: Intent) {
		super.onNewIntent(intent)
		setIntent(intent)
		handleIntent(intent)
	}

	override fun onStop() {
		if (::playerPort.isInitialized) {
			playerPort.pause()
		}
		super.onStop()
	}

	override fun onDestroy() {
		if (::playbackController.isInitialized) {
			playbackController.exit()
		}
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

	private fun handleIntent(intent: Intent) {
		intent.getStringExtra(EXTRA_MAGNET)
			?.trim()
			?.takeIf { it.isNotEmpty() }
			?.let(::startMagnet)
	}

	private fun startMagnet(magnet: String) {
		playbackController.playMagnet(magnet)
	}

	private companion object {
		const val EXTRA_MAGNET = "magnet"
		const val TORRSERVER_BINARY = "libtorrserver.so"
		const val TORRSERVER_DATA_DIR = "torrserver"
		const val SUBTITLE_CACHE_DIR = "subtitles"
	}
}
