package sk.ziacik.androidstreamplayer.search

import sk.ziacik.androidstreamplayer.catalog.Movie

data class TorrentSearchUiState(
	val movie: Movie? = null,
	val isSearching: Boolean = false,
	val results: List<TorrentSearchResult> = emptyList(),
	val errorMessage: String? = null,
)
