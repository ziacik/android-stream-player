package sk.ziacik.androidstreamplayer.catalog

data class MovieExternalIds(
    val imdbId: String?,
)

interface MovieCatalog {
    suspend fun search(query: String): List<Movie>

    suspend fun externalIds(tmdbId: Int): MovieExternalIds
}
