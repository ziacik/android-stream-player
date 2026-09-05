package sk.ziacik.androidstreamplayer.playback

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import sk.ziacik.androidstreamplayer.catalog.Movie
import sk.ziacik.androidstreamplayer.search.TorrentSearchResult
import sk.ziacik.androidstreamplayer.subtitle.SubtitleOption
import sk.ziacik.androidstreamplayer.subtitle.SubtitleTrack
import sk.ziacik.androidstreamplayer.torrent.TorrentSource
import sk.ziacik.androidstreamplayer.torrent.TorrentStreamer

class PlaybackController(
	private val scope: CoroutineScope,
	private val streamer: TorrentStreamer?,
	private val subtitleSearch: (suspend (Movie, TorrentSearchResult, TorrentSource) -> List<SubtitleOption>)? = null,
	private val subtitleDownload: (suspend (Movie, TorrentSearchResult, SubtitleOption) -> SubtitleTrack?)? = null,
	private val onStreamReady: (TorrentSource) -> Unit = {},
	private val onSubtitleSelected: (SubtitleTrack?) -> Unit = {},
) {
	private val mutableState = MutableStateFlow(PlaybackUiState())
	val state: StateFlow<PlaybackUiState> = mutableState.asStateFlow()

	private var playbackJob: Job? = null
	private var subtitleSearchJob: Job? = null
	private var subtitleDownloadJob: Job? = null
	private var generation = 0L
	private var activeMovie: Movie? = null
	private var activeResult: TorrentSearchResult? = null

	fun play(movie: Movie, result: TorrentSearchResult) {
		playInternal(movie, result)
	}

	fun play(result: TorrentSearchResult) {
		playInternal(null, result)
	}

	private fun playInternal(movie: Movie?, result: TorrentSearchResult) {
		cancelJobs()
		generation += 1
		val currentGeneration = generation
		activeMovie = movie
		activeResult = result

		mutableState.value = PlaybackUiState(
			selectedResult = result,
			status = "Preparing stream…",
			subtitles = SubtitleUiState(
				isSearching = movie != null && subtitleSearch != null,
			),
		)

		playbackJob = scope.launch {
			val activeStreamer = streamer
			if (activeStreamer == null) {
				publishSubtitleSearchStopped(currentGeneration)
				publishStatus(result, "Streaming unavailable", currentGeneration)
				return@launch
			}

			val source = try {
				activeStreamer.prepare(result)
			} catch (error: CancellationException) {
				throw error
			} catch (_: Throwable) {
				publishSubtitleSearchStopped(currentGeneration)
				publishStatus(result, "Stream failed", currentGeneration)
				return@launch
			}

			if (generation != currentGeneration) return@launch

			try {
				onStreamReady(source)
			} catch (_: Throwable) {
				publishSubtitleSearchStopped(currentGeneration)
				publishStatus(result, "Playback failed", currentGeneration)
				return@launch
			}

			publishStatus(result, "Playing", currentGeneration)
			if (movie != null && subtitleSearch != null) {
				startSubtitleSearch(movie, result, source, currentGeneration)
			}
		}
	}

	private fun startSubtitleSearch(
		movie: Movie,
		result: TorrentSearchResult,
		source: TorrentSource,
		currentGeneration: Long,
	) {
		val search = subtitleSearch ?: return
		subtitleSearchJob = scope.launch {
			try {
				val options = search.invoke(movie, result, source)
				if (generation != currentGeneration) return@launch
				val current = mutableState.value
				mutableState.value = current.copy(
					subtitles = current.subtitles.copy(
						isSearching = false,
						options = options,
						message = if (options.isEmpty()) "No subtitles found" else null,
					),
				)

				options.firstOrNull { it.exactMatch }?.let(::selectSubtitle)
			} catch (error: CancellationException) {
				throw error
			} catch (error: Throwable) {
				Log.e(
					TAG,
					"Subtitle search failed for movie=${movie.title} (${movie.releaseYear}), " +
						"torrent=${result.title}, error=${error::class.java.simpleName}: ${error.message}",
					error,
				)
				if (generation != currentGeneration) return@launch
				val current = mutableState.value
				mutableState.value = current.copy(
					subtitles = current.subtitles.copy(
						isSearching = false,
						message = "Could not find subtitles",
					),
				)
			}
		}
	}

	fun selectSubtitle(option: SubtitleOption?) {
		subtitleDownloadJob?.cancel()
		subtitleDownloadJob = null
		val currentGeneration = generation

		if (option == null) {
			try {
				onSubtitleSelected(null)
				val current = mutableState.value
				mutableState.value = current.copy(
					subtitles = current.subtitles.copy(
						selectedId = null,
						loadingId = null,
						message = null,
					),
				)
			} catch (_: Throwable) {
				publishSubtitleMessage("Could not disable subtitles", currentGeneration)
			}
			return
		}

		val movie = activeMovie ?: return
		val result = activeResult ?: return
		val download = subtitleDownload ?: return
		val current = mutableState.value
		if (current.subtitles.options.none { it.id == option.id }) return
		mutableState.value = current.copy(
			subtitles = current.subtitles.copy(
				loadingId = option.id,
				message = null,
			),
		)

		subtitleDownloadJob = scope.launch {
			try {
				val track = download.invoke(movie, result, option)
				if (generation != currentGeneration) return@launch
				if (track == null) {
					publishSubtitleMessage("Could not load subtitles", currentGeneration)
					return@launch
				}
				onSubtitleSelected(track)
				val latest = mutableState.value
				mutableState.value = latest.copy(
					subtitles = latest.subtitles.copy(
						selectedId = option.id,
						loadingId = null,
						message = null,
					),
				)
			} catch (error: CancellationException) {
				throw error
			} catch (error: Throwable) {
				Log.e(
					TAG,
					"Subtitle download failed for id=${option.id}, error=${error::class.java.simpleName}: ${error.message}",
					error,
				)
				publishSubtitleMessage("Could not load subtitles", currentGeneration)
			}
		}
	}

	fun playMagnet(magnet: String) {
		val normalizedMagnet = magnet.trim()
		play(
			TorrentSearchResult(
				id = "direct-magnet",
				title = "Magnet torrent",
				magnetUri = normalizedMagnet,
				source = "Magnet",
			),
		)
	}

	fun exit() {
		cancelJobs()
		generation += 1
		activeMovie = null
		activeResult = null
		mutableState.value = PlaybackUiState()
	}

	private fun cancelJobs() {
		playbackJob?.cancel()
		playbackJob = null
		subtitleSearchJob?.cancel()
		subtitleSearchJob = null
		subtitleDownloadJob?.cancel()
		subtitleDownloadJob = null
	}

	private fun publishStatus(
		result: TorrentSearchResult,
		status: String,
		currentGeneration: Long,
	) {
		if (generation != currentGeneration) return
		mutableState.value = mutableState.value.copy(
			selectedResult = result,
			status = status,
		)
	}

	private fun publishSubtitleSearchStopped(currentGeneration: Long) {
		if (generation != currentGeneration) return
		val current = mutableState.value
		mutableState.value = current.copy(
			subtitles = current.subtitles.copy(isSearching = false),
		)
	}

	private fun publishSubtitleMessage(message: String, currentGeneration: Long) {
		if (generation != currentGeneration) return
		val current = mutableState.value
		mutableState.value = current.copy(
			subtitles = current.subtitles.copy(
				loadingId = null,
				message = message,
			),
		)
	}

	private companion object {
		const val TAG = "KinoSubtitles"
	}
}
