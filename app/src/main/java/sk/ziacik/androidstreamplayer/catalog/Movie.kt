package sk.ziacik.androidstreamplayer.catalog

data class Movie(
    val tmdbId: Int,
    val imdbId: String? = null,
    val title: String,
    val originalTitle: String,
    val releaseYear: Int?,
    val overview: String?,
    val voteAverage: Double?,
    val posterPath: String?,
    val backdropPath: String?,
)
