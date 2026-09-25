package sk.ziacik.androidstreamplayer.catalog

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SeriesUiState(
    val show: Movie? = null,
    val seasons: List<SeriesSeason> = emptyList(),
    val selectedSeason: Int? = null,
    val episodes: List<SeriesEpisode> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
)

class SeriesController(
    private val scope: CoroutineScope,
    private val catalog: MovieCatalog,
) {
    private val mutableState = MutableStateFlow(SeriesUiState())
    val state: StateFlow<SeriesUiState> = mutableState.asStateFlow()

    private var job: Job? = null
    private var generation = 0L

    fun open(show: Movie) {
        require(show.mediaType == MediaType.SERIES)
        job?.cancel()
        generation += 1
        val currentGeneration = generation
        mutableState.value = SeriesUiState(show = show, isLoading = true)

        job = scope.launch {
            try {
                val seasons = catalog.seasons(show.tmdbId)
                if (generation != currentGeneration) return@launch
                val firstSeason = seasons.firstOrNull()?.number
                mutableState.value = SeriesUiState(
                    show = show,
                    seasons = seasons,
                    selectedSeason = firstSeason,
                    isLoading = firstSeason != null,
                )
                if (firstSeason != null) {
                    loadEpisodes(show, seasons, firstSeason, currentGeneration)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                if (generation == currentGeneration) {
                    mutableState.value = SeriesUiState(
                        show = show,
                        errorMessage = "Could not load series",
                    )
                }
            }
        }
    }

    fun selectSeason(number: Int) {
        val current = mutableState.value
        val show = current.show ?: return
        if (current.selectedSeason == number && current.episodes.isNotEmpty()) return

        job?.cancel()
        generation += 1
        val currentGeneration = generation
        mutableState.value = current.copy(
            selectedSeason = number,
            episodes = emptyList(),
            isLoading = true,
            errorMessage = null,
        )
        job = scope.launch {
            loadEpisodes(show, current.seasons, number, currentGeneration)
        }
    }

    fun episodeMovie(episode: SeriesEpisode): Movie {
        val show = requireNotNull(mutableState.value.show)
        return Movie(
            tmdbId = show.tmdbId,
            imdbId = show.imdbId,
            title = episode.name,
            originalTitle = episode.name,
            releaseYear = episode.airYear,
            overview = episode.overview,
            voteAverage = episode.voteAverage,
            posterPath = show.posterPath,
            backdropPath = episode.stillPath ?: show.backdropPath,
            mediaType = MediaType.EPISODE,
            seriesTitle = show.title,
            originalSeriesTitle = show.originalTitle,
            seasonNumber = episode.seasonNumber,
            episodeNumber = episode.number,
        )
    }

    fun retry() {
        val show = mutableState.value.show ?: return
        val season = mutableState.value.selectedSeason
        if (season == null) open(show) else selectSeason(season)
    }

    fun clear() {
        job?.cancel()
        job = null
        generation += 1
        mutableState.value = SeriesUiState()
    }

    private suspend fun loadEpisodes(
        show: Movie,
        seasons: List<SeriesSeason>,
        seasonNumber: Int,
        currentGeneration: Long,
    ) {
        try {
            val episodes = catalog.episodes(show.tmdbId, seasonNumber)
            if (generation != currentGeneration) return
            mutableState.value = SeriesUiState(
                show = show,
                seasons = seasons,
                selectedSeason = seasonNumber,
                episodes = episodes,
            )
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            if (generation == currentGeneration) {
                mutableState.value = SeriesUiState(
                    show = show,
                    seasons = seasons,
                    selectedSeason = seasonNumber,
                    errorMessage = "Could not load episodes",
                )
            }
        }
    }
}
