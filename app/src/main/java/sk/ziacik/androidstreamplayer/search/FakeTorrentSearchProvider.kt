package sk.ziacik.androidstreamplayer.search

class FakeTorrentSearchProvider : TorrentSearchProvider {
    override suspend fun search(query: String): List<TorrentSearchResult> {
        val movie = query.trim()
        if (movie.isEmpty()) return emptyList()

        return listOf(
            TorrentSearchResult(
                id = "$movie-2160p",
                title = "$movie.2160p.WEB-DL.DDP5.1.H.265",
                magnetUri = "magnet:?xt=urn:btih:fake-${movie.hashCode()}-2160",
                quality = "2160p",
                sizeBytes = 18_790_000_000,
                seeders = 184,
                source = "Fake",
            ),
            TorrentSearchResult(
                id = "$movie-1080p",
                title = "$movie.1080p.BluRay.x264",
                magnetUri = "magnet:?xt=urn:btih:fake-${movie.hashCode()}-1080",
                quality = "1080p",
                sizeBytes = 8_320_000_000,
                seeders = 326,
                source = "Fake",
            ),
            TorrentSearchResult(
                id = "$movie-720p",
                title = "$movie.720p.WEBRip.x264",
                magnetUri = "magnet:?xt=urn:btih:fake-${movie.hashCode()}-720",
                quality = "720p",
                sizeBytes = 3_140_000_000,
                seeders = 91,
                source = "Fake",
            ),
        )
    }
}
