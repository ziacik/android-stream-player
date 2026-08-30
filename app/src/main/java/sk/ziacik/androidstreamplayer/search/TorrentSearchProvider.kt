package sk.ziacik.androidstreamplayer.search

fun interface TorrentSearchProvider {
	suspend fun search(movie: MovieTorrentSearchRequest): List<TorrentSearchResult>
}
