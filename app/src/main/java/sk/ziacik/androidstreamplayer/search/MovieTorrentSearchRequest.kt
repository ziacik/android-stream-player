package sk.ziacik.androidstreamplayer.search

data class MovieTorrentSearchRequest(
	val tmdbId: Int,
	val imdbId: String?,
	val title: String,
	val originalTitle: String,
	val year: Int?,
)
