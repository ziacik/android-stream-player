package sk.ziacik.androidstreamplayer.search

data class SearchUiState(
    val query: String = "",
    val isSearching: Boolean = false,
    val results: List<TorrentSearchResult> = emptyList(),
    val errorMessage: String? = null,
    val selectedResult: TorrentSearchResult? = null,
    val streamStatus: String? = null,
)
