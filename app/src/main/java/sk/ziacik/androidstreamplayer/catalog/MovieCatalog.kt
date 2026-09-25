package sk.ziacik.androidstreamplayer.catalog

data class MovieExternalIds(
    val imdbId: String?,
)

data class SeriesSeason(
    val number: Int,
    val name: String,
    val episodeCount: Int,
    val airYear: Int?,
    val posterPath: String?,
)

data class SeriesEpisode(
    val number: Int,
    val seasonNumber: Int,
    val name: String,
    val overview: String?,
    val airYear: Int?,
    val voteAverage: Double?,
    val stillPath: String?,
)

interface MovieCatalog {
    suspend fun search(query: String): List<Movie>

    suspend fun externalIds(tmdbId: Int): MovieExternalIds

    suspend fun externalIds(movie: Movie): MovieExternalIds = externalIds(movie.tmdbId)

    suspend fun seasons(tmdbId: Int): List<SeriesSeason> = emptyList()

    suspend fun episodes(tmdbId: Int, seasonNumber: Int): List<SeriesEpisode> = emptyList()
}
