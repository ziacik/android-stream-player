package sk.ziacik.androidstreamplayer.watch

import org.junit.Assert.assertEquals
import org.junit.Test
import sk.ziacik.androidstreamplayer.catalog.Movie
import sk.ziacik.androidstreamplayer.search.TorrentSearchResult

class WatchProgressJsonTest {
	@Test
	fun `round trip preserves resumable movie torrent and progress`() {
		val entry = WatchProgressEntry(
			movie = Movie(
				tmdbId = 603,
				imdbId = "tt0133093",
				title = "The Matrix",
				originalTitle = "The Matrix",
				releaseYear = 1999,
				overview = "A hacker discovers reality is a simulation.",
				voteAverage = 8.2,
				posterPath = "/poster.jpg",
				backdropPath = "/backdrop.jpg",
			),
			result = TorrentSearchResult(
				id = "matrix-1080p",
				title = "The Matrix 1999 1080p",
				magnetUri = "magnet:?xt=urn:btih:matrix",
				quality = "1080p",
				sizeBytes = 4_200_000_000L,
				seeders = 87,
				source = "Knaben",
			),
			positionMs = 2_400_000L,
			durationMs = 8_160_000L,
			updatedAtEpochMs = 123_456L,
		)

		val encoded = WatchProgressJson.encode(listOf(entry))
		val decoded = WatchProgressJson.decode(encoded)

		assertEquals(listOf(entry), decoded)
	}

	@Test
	fun `malformed persisted value is treated as empty`() {
		assertEquals(emptyList<WatchProgressEntry>(), WatchProgressJson.decode("not json"))
	}
}
