package sk.ziacik.androidstreamplayer.search

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield

class SearchController(
    private val scope: CoroutineScope,
    private val provider: TorrentSearchProvider,
) {
    private val _state = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = _state.asStateFlow()

    private var lastSearchQuery: String? = null

    fun setQuery(query: String) {
        _state.value = _state.value.copy(query = query)
    }

    fun search() {
        val query = _state.value.query.trim()
        if (query.isEmpty()) {
            _state.value = _state.value.copy(isSearching = false)
            return
        }

        lastSearchQuery = query
        performSearch(query)
    }

    fun retry() {
        lastSearchQuery?.let(::performSearch)
    }

    fun select(result: TorrentSearchResult) {
        _state.value = _state.value.copy(
            selectedResult = result,
            streamStatus = "Preparing stream…",
        )
        scope.launch {
            yield()
            _state.value = _state.value.copy(streamStatus = "Streaming not implemented yet")
        }
    }

    private fun performSearch(query: String) {
        _state.value = _state.value.copy(
            isSearching = true,
            errorMessage = null,
            selectedResult = null,
            streamStatus = null,
        )

        scope.launch {
            runCatching { provider.search(query) }
                .onSuccess { results ->
                    _state.value = _state.value.copy(
                        isSearching = false,
                        results = results,
                        errorMessage = null,
                    )
                }
                .onFailure {
                    _state.value = _state.value.copy(
                        isSearching = false,
                        results = emptyList(),
                        errorMessage = "Search failed",
                    )
                }
        }
    }
}
