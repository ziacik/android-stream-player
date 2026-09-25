package sk.ziacik.androidstreamplayer.search

import sk.ziacik.androidstreamplayer.catalog.MediaType

data class MovieTorrentSearchRequest(
    val tmdbId: Int,
    val imdbId: String?,
    val title: String,
    val originalTitle: String,
    val year: Int?,
    val mediaType: MediaType = MediaType.MOVIE,
    val seriesTitle: String? = null,
    val originalSeriesTitle: String? = null,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
) {
    val episodeCode: String?
        get() = if (mediaType == MediaType.EPISODE && seasonNumber != null && episodeNumber != null) {
            "S%02dE%02d".format(seasonNumber, episodeNumber)
        } else {
            null
        }
}
