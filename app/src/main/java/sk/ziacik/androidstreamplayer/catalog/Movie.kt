package sk.ziacik.androidstreamplayer.catalog

enum class MediaType {
    MOVIE,
    SERIES,
    EPISODE,
}

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
    val mediaType: MediaType = MediaType.MOVIE,
    val seriesTitle: String? = null,
    val originalSeriesTitle: String? = null,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
) {
    val contentKey: String
        get() = when (mediaType) {
            MediaType.EPISODE -> "tv:$tmdbId:s${seasonNumber ?: 0}:e${episodeNumber ?: 0}"
            MediaType.SERIES -> "tv:$tmdbId"
            MediaType.MOVIE -> "movie:$tmdbId"
        }

    val resumeKey: Int
        get() = if (mediaType == MediaType.EPISODE) contentKey.hashCode() else tmdbId

    val browseKey: Int
        get() = when (mediaType) {
            MediaType.MOVIE -> tmdbId
            MediaType.SERIES -> -tmdbId
            MediaType.EPISODE -> resumeKey
        }

    val displayTitle: String
        get() = if (mediaType == MediaType.EPISODE) {
            val code = seasonNumber?.let { season ->
                episodeNumber?.let { episode -> "S%02dE%02d".format(season, episode) }
            }
            listOfNotNull(seriesTitle, code, title.takeIf { it != seriesTitle })
                .joinToString(" · ")
        } else {
            title
        }
}
