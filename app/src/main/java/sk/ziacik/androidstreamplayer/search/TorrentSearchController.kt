package sk.ziacik.androidstreamplayer.search

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import sk.ziacik.androidstreamplayer.catalog.Movie
import sk.ziacik.androidstreamplayer.catalog.MovieCatalog

class TorrentSearchController(
	private val scope: CoroutineScope,
	private val catalog: MovieCatalog,
	private val provider: TorrentSearchProvider,
	private val releaseParser: TorrentReleaseParser = DefaultTorrentReleaseParser,
) {
	private val mutableState = MutableStateFlow(TorrentSearchUiState())
	val state: StateFlow<TorrentSearchUiState> = mutableState.asStateFlow()

	private var searchJob: Job? = null
	private var generation = 0L

	fun open(movie: Movie) {
		searchJob?.cancel()
		generation += 1
		val currentGeneration = generation

		mutableState.value = TorrentSearchUiState(
			movie = movie,
			isSearching = true,
		)

		searchJob = scope.launch {
			val imdbId = try {
				catalog.externalIds(movie.tmdbId).imdbId ?: movie.imdbId
			} catch (error: CancellationException) {
				throw error
			} catch (_: Throwable) {
				movie.imdbId
			}

			val request = MovieTorrentSearchRequest(
				tmdbId = movie.tmdbId,
				imdbId = imdbId,
				title = movie.title,
				originalTitle = movie.originalTitle,
				year = movie.releaseYear,
			)

			try {
				val results = provider.search(request).map(::enrichReleaseInfo)
				if (generation != currentGeneration) return@launch

				mutableState.value = TorrentSearchUiState(
					movie = movie,
					results = results,
				)
			} catch (error: CancellationException) {
				throw error
			} catch (_: Throwable) {
				if (generation != currentGeneration) return@launch

				mutableState.value = TorrentSearchUiState(
					movie = movie,
					errorMessage = "Search failed",
				)
			}
		}
	}

	fun retry() {
		state.value.movie?.let(::open)
	}

	fun clear() {
		searchJob?.cancel()
		searchJob = null
		generation += 1
		mutableState.value = TorrentSearchUiState()
	}

	private fun enrichReleaseInfo(result: TorrentSearchResult): TorrentSearchResult {
		val info = runCatching { releaseParser.parse(result.title) }
			.getOrElse { return result }
		return result.copy(
			quality = info.resolution?.releaseLabel ?: result.quality,
			releaseInfo = info,
		)
	}
}
