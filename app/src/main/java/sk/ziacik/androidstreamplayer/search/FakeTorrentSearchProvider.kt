package sk.ziacik.androidstreamplayer.search

class FakeTorrentSearchProvider : TorrentSearchProvider {
	override suspend fun search(movie: MovieTorrentSearchRequest): List<TorrentSearchResult> {
		val title = movie.title.trim()
		if (title.isEmpty()) return emptyList()

		return listOf(
			TorrentSearchResult(
				id = "$title-2160p",
				title = "$title.2160p.WEB-DL.DDP5.1.H.265",
				magnetUri = "magnet:?xt=urn:btih:fake-${title.hashCode()}-2160",
				quality = "2160p",
				sizeBytes = 18_790_000_000,
				seeders = 184,
				source = "Fake",
			),
			TorrentSearchResult(
				id = "$title-1080p",
				title = "$title.1080p.BluRay.x264",
				magnetUri = "magnet:?xt=urn:btih:fake-${title.hashCode()}-1080",
				quality = "1080p",
				sizeBytes = 8_320_000_000,
				seeders = 326,
				source = "Fake",
			),
			TorrentSearchResult(
				id = "$title-720p",
				title = "$title.720p.WEBRip.x264",
				magnetUri = "magnet:?xt=urn:btih:fake-${title.hashCode()}-720",
				quality = "720p",
				sizeBytes = 3_140_000_000,
				seeders = 91,
				source = "Fake",
			),
		)
	}
}
