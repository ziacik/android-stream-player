package sk.ziacik.androidstreamplayer.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.media3.common.C
import androidx.media3.common.Player
import kotlinx.coroutines.delay
import sk.ziacik.androidstreamplayer.catalog.Movie
import sk.ziacik.androidstreamplayer.search.TorrentSearchResult
import sk.ziacik.androidstreamplayer.watch.WatchProgressRepository

private const val WATCH_PROGRESS_POLL_INTERVAL_MS = 500L

@Composable
internal fun WatchProgressEffect(
	player: Player,
	movie: Movie?,
	result: TorrentSearchResult?,
	resumePositionMs: Long?,
	repository: WatchProgressRepository,
) {
	var lastPersistedPositionMs by remember(movie?.tmdbId, result?.id) {
		mutableStateOf<Long?>(null)
	}
	var latestPositionMs by remember(movie?.tmdbId, result?.id) {
		mutableLongStateOf(player.currentPosition.coerceAtLeast(0L))
	}
	var latestDurationMs by remember(movie?.tmdbId, result?.id) {
		mutableStateOf(player.watchDurationMs())
	}

	LaunchedEffect(player, result?.id, resumePositionMs) {
		val targetMs = resumeSeekTargetMs(
			resumePositionMs = resumePositionMs,
			durationMs = player.watchDurationMs(),
		) ?: return@LaunchedEffect

		player.seekTo(targetMs)
		latestPositionMs = targetMs
	}

	LaunchedEffect(player, movie?.tmdbId, result?.id) {
		while (true) {
			val positionMs = player.currentPosition.coerceAtLeast(0L)
			val durationMs = player.watchDurationMs()
			latestPositionMs = positionMs
			latestDurationMs = durationMs

			if (
				movie != null &&
				result != null &&
				shouldPersistWatchProgress(
					lastPersistedMs = lastPersistedPositionMs,
					positionMs = positionMs,
					durationMs = durationMs,
				)
			) {
				repository.record(
					movie = movie,
					result = result,
					positionMs = positionMs,
					durationMs = durationMs!!,
				)
				lastPersistedPositionMs = positionMs
			}

			delay(WATCH_PROGRESS_POLL_INTERVAL_MS)
		}
	}

	DisposableEffect(movie?.tmdbId, result?.id) {
		onDispose {
			val trackedMovie = movie
			val trackedResult = result
			val durationMs = latestDurationMs
			if (
				trackedMovie != null &&
				trackedResult != null &&
				durationMs != null &&
				durationMs > 0L &&
				latestPositionMs > 0L
			) {
				repository.record(
					movie = trackedMovie,
					result = trackedResult,
					positionMs = latestPositionMs,
					durationMs = durationMs,
				)
			}
		}
	}
}

private fun Player.watchDurationMs(): Long? =
	duration.takeIf { it != C.TIME_UNSET && it > 0L }
