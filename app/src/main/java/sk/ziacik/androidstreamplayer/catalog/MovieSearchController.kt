package sk.ziacik.androidstreamplayer.catalog

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MovieSearchController(
    private val scope: CoroutineScope,
    private val catalog: MovieCatalog,
    private val debounceMs: Long = 400L,
) {
    private val _state = MutableStateFlow(MovieSearchUiState())
    val state: StateFlow<MovieSearchUiState> = _state.asStateFlow()

    private var searchJob: Job? = null
    private var lastSearchQuery: String? = null
    private var requestGeneration: Long = 0L

    fun setQuery(query: String) {
        searchJob?.cancel()
        requestGeneration += 1

        val normalized = query.trim()
        _state.value = _state.value.copy(
            query = query,
            errorMessage = null,
        )

        if (normalized.length < MIN_QUERY_LENGTH) {
            lastSearchQuery = null
            _state.value = _state.value.copy(
                isSearching = false,
                results = emptyList(),
                errorMessage = null,
            )
            return
        }

        lastSearchQuery = normalized
        val generation = requestGeneration
        searchJob = scope.launch {
            if (debounceMs > 0) delay(debounceMs)
            performSearch(normalized, generation)
        }
    }

    fun searchNow() {
        val normalized = _state.value.query.trim()
        if (normalized.length < MIN_QUERY_LENGTH) return

        searchJob?.cancel()
        requestGeneration += 1
        lastSearchQuery = normalized
        val generation = requestGeneration
        searchJob = scope.launch {
            performSearch(normalized, generation)
        }
    }

    fun retry() {
        val query = lastSearchQuery ?: return

        searchJob?.cancel()
        requestGeneration += 1
        val generation = requestGeneration
        searchJob = scope.launch {
            performSearch(query, generation)
        }
    }

    fun setFocusedMovie(tmdbId: Int?) {
        _state.value = _state.value.copy(focusedMovieId = tmdbId)
    }

    private suspend fun performSearch(query: String, generation: Long) {
        if (generation != requestGeneration) return

        _state.value = _state.value.copy(
            isSearching = true,
            errorMessage = null,
        )

        try {
            val results = catalog.search(query)
            if (generation == requestGeneration) {
                _state.value = _state.value.copy(
                    isSearching = false,
                    results = results,
                    errorMessage = null,
                )
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            if (generation == requestGeneration) {
                _state.value = _state.value.copy(
                    isSearching = false,
                    results = emptyList(),
                    errorMessage = "Search failed",
                )
            }
        }
    }

    private companion object {
        const val MIN_QUERY_LENGTH = 2
    }
}
