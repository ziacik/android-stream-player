package sk.ziacik.androidstreamplayer.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import sk.ziacik.androidstreamplayer.catalog.Movie
import sk.ziacik.androidstreamplayer.catalog.MovieSearchController
import sk.ziacik.androidstreamplayer.playback.PlaybackController
import sk.ziacik.androidstreamplayer.search.TorrentSearchController
import sk.ziacik.androidstreamplayer.search.TorrentSearchResult

@Composable
fun KinoApp(
	movieSearchController: MovieSearchController,
	torrentSearchController: TorrentSearchController,
	playbackController: PlaybackController,
	playerContent: @Composable (TorrentSearchResult?, () -> Unit) -> Unit,
) {
	var selectedMovie by remember { mutableStateOf<Movie?>(null) }
	val playbackState by playbackController.state.collectAsState()

	when {
		playbackState.status == "Playing" -> {
			playerContent(playbackState.selectedResult) {
				playbackController.exit()
			}
		}

		selectedMovie != null -> {
			MovieDetailScreen(
				movie = selectedMovie!!,
				torrentController = torrentSearchController,
				onPlay = playbackController::play,
				onBack = {
					playbackController.exit()
					torrentSearchController.clear()
					selectedMovie = null
				},
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
