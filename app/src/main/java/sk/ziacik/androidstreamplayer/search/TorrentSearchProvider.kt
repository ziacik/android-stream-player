package sk.ziacik.androidstreamplayer.search

fun interface TorrentSearchProvider {
	suspend fun search(movie: MovieTorrentSearchRequest): List<TorrentSearchResult>

	@Deprecated("Legacy free-text search; remove with old SearchController")
	suspend fun search(query: String): List<TorrentSearchResult> = search(
		MovieTorrentSearchRequest(
			tmdbId = 0,
			imdbId = null,
			title = query,
			originalTitle = query,
			year = null,
		),
	)
}
