package sk.ziacik.androidstreamplayer.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import sk.ziacik.androidstreamplayer.catalog.Movie
import sk.ziacik.androidstreamplayer.catalog.MovieBrowseController
import sk.ziacik.androidstreamplayer.catalog.MovieSearchController
import sk.ziacik.androidstreamplayer.playback.PlaybackController
import sk.ziacik.androidstreamplayer.search.TorrentSearchController
import sk.ziacik.androidstreamplayer.search.TorrentSearchResult
import sk.ziacik.androidstreamplayer.watch.WatchProgressRepository

@Composable
fun KinoApp(
	movieBrowseController: MovieBrowseController,
	movieSearchController: MovieSearchController,
	torrentSearchController: TorrentSearchController,
	playbackController: PlaybackController,
	watchProgressRepository: WatchProgressRepository,
	playerContent: @Composable (Movie?, TorrentSearchResult?, Long?, () -> Unit) -> Unit,
) {
	var rootScreen by remember { mutableStateOf(KinoRootScreen.Home) }
	var selectedMovie by remember { mutableStateOf<Movie?>(null) }
	var playbackMovie by remember { mutableStateOf<Movie?>(null) }
	var resumePositionMs by remember { mutableStateOf<Long?>(null) }
	val playbackState by playbackController.state.collectAsState()
	val resumeWatching by watchProgressRepository.entries.collectAsState()
	val startingResumeMovieId = startingResumeMovieId(
		playbackStatus = playbackState.status,
		playbackMovieId = playbackMovie?.tmdbId,
	)

	BackHandler(
		enabled = rootScreen == KinoRootScreen.Search &&
			selectedMovie == null &&
			playbackState.status != "Playing",
	) {
		rootScreen = KinoRootScreen.Home
	}

	when {
		playbackState.status == "Playing" -> {
			playerContent(
				playbackMovie,
				playbackState.selectedResult,
				resumePositionMs,
			) {
				playbackController.exit()
				playbackMovie = null
				resumePositionMs = null
			}
		}

		selectedMovie != null -> {
			val movie = selectedMovie!!
			MovieDetailScreen(
				movie = movie,
				torrentController = torrentSearchController,
				playbackState = playbackState,
				onPlay = { result ->
					playbackMovie = movie
					resumePositionMs = null
					playbackController.play(result)
				},
				onBack = {
					playbackController.exit()
					torrentSearchController.clear()
					selectedMovie = null
				},
			)
		}

		rootScreen == KinoRootScreen.Home -> {
			HomeScreen(
				controller = movieBrowseController,
				resumeWatching = resumeWatching,
				onMovieSelected = { movie ->
					selectedMovie = movie
				},
				onResumeWatching = { entry ->
					playbackMovie = entry.movie
					resumePositionMs = entry.positionMs
					playbackController.play(entry.result)
				},
				onCancelResumeWatching = {
					playbackController.exit()
					playbackMovie = null
					resumePositionMs = null
				},
				onRemoveResumeWatching = watchProgressRepository::remove,
				startingResumeMovieId = startingResumeMovieId,
				onSearch = { rootScreen = KinoRootScreen.Search },
			)
		}

		else -> {
			MovieSearchScreen(
				controller = movieSearchController,
				onMovieSelected = { movie ->
					selectedMovie = movie
				},
			)
		}
	}
}

private enum class KinoRootScreen {
	Home,
	Search,
}
