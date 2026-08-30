package sk.ziacik.androidstreamplayer.catalog

data class MovieSearchUiState(
    val query: String = "",
    val isSearching: Boolean = false,
    val results: List<Movie> = emptyList(),
    val errorMessage: String? = null,
    val focusedMovieId: Int? = null,
)
