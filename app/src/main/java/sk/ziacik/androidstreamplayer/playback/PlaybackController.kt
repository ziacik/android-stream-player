package sk.ziacik.androidstreamplayer.playback

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import sk.ziacik.androidstreamplayer.catalog.Movie
import sk.ziacik.androidstreamplayer.search.TorrentSearchResult
import sk.ziacik.androidstreamplayer.subtitle.SubtitleTrack
import sk.ziacik.androidstreamplayer.torrent.TorrentSource
import sk.ziacik.androidstreamplayer.torrent.TorrentStreamer

class PlaybackController(
	private val scope: CoroutineScope,
	private val streamer: TorrentStreamer?,
	private val subtitleLookup: (suspend (Movie, TorrentSearchResult) -> SubtitleTrack?)? = null,
	private val subtitleTimeoutMs: Long = DEFAULT_SUBTITLE_TIMEOUT_MS,
	private val onStreamReady: (TorrentSource, SubtitleTrack?) -> Unit = { _, _ -> },
) {
	constructor(
		scope: CoroutineScope,
		streamer: TorrentStreamer?,
		onStreamReady: (TorrentSource) -> Unit,
	) : this(
		scope = scope,
		streamer = streamer,
		onStreamReady = { source, _ -> onStreamReady(source) },
	)

	private val mutableState = MutableStateFlow(PlaybackUiState())
	val state: StateFlow<PlaybackUiState> = mutableState.asStateFlow()

	private var playbackJob: Job? = null
	private var generation = 0L

	fun play(movie: Movie, result: TorrentSearchResult) {
		playInternal(movie, result)
	}

	fun play(result: TorrentSearchResult) {
		playInternal(null, result)
	}

	private fun playInternal(movie: Movie?, result: TorrentSearchResult) {
		playbackJob?.cancel()
		generation += 1
		val currentGeneration = generation

		mutableState.value = PlaybackUiState(
			selectedResult = result,
			status = "Preparing stream…",
		)

		playbackJob = scope.launch {
			val subtitleDeferred = if (movie != null && subtitleLookup != null) {
				async {
					try {
						withTimeoutOrNull(subtitleTimeoutMs) {
							subtitleLookup.invoke(movie, result)
						}
					} catch (error: CancellationException) {
						throw error
					} catch (_: Throwable) {
						null
					}
				}
			} else {
				null
			}

			val activeStreamer = streamer
			if (activeStreamer == null) {
				subtitleDeferred?.cancel()
				publishStatus(result, "Streaming unavailable", currentGeneration)
				return@launch
			}

			val source = try {
				activeStreamer.prepare(result)
			} catch (error: CancellationException) {
				throw error
			} catch (_: Throwable) {
				subtitleDeferred?.cancel()
				publishStatus(result, "Stream failed", currentGeneration)
				return@launch
			}

			val subtitle = subtitleDeferred?.await()
			if (generation != currentGeneration) return@launch

			try {
				onStreamReady(source, subtitle)
			} catch (_: Throwable) {
				publishStatus(result, "Playback failed", currentGeneration)
				return@launch
			}

			publishStatus(result, "Playing", currentGeneration)
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
		playbackJob?.cancel()
		playbackJob = null
		generation += 1
		mutableState.value = PlaybackUiState()
	}

	private fun publishStatus(
		result: TorrentSearchResult,
		status: String,
		currentGeneration: Long,
	) {
		if (generation != currentGeneration) return
		mutableState.value = PlaybackUiState(
			selectedResult = result,
			status = status,
		)
	}

	private companion object {
		const val DEFAULT_SUBTITLE_TIMEOUT_MS = 2_500L
	}
}
