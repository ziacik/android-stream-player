package sk.ziacik.androidstreamplayer.watch

import org.junit.Assert.assertEquals
import org.junit.Test
import sk.ziacik.androidstreamplayer.catalog.Movie
import sk.ziacik.androidstreamplayer.search.TorrentSearchResult

class WatchProgressRepositoryTest {
	@Test
	fun `record exposes resumable movie with playback position`() {
		val storage = FakeWatchProgressStorage()
		val repository = WatchProgressRepository(
			storage = storage,
			nowEpochMs = { 1234L },
		)

		repository.record(
			movie = movie(603, "The Matrix"),
			result = result("matrix-1080p"),
			positionMs = 2_400_000L,
			durationMs = 8_160_000L,
		)

		val entry = repository.entries.value.single()
		assertEquals(603, entry.movie.tmdbId)
		assertEquals("matrix-1080p", entry.result.id)
		assertEquals(2_400_000L, entry.positionMs)
		assertEquals(8_160_000L, entry.durationMs)
		assertEquals(1234L, entry.updatedAtEpochMs)
		assertEquals(repository.entries.value, storage.saved)
	}

	@Test
	fun `record replaces previous progress for same movie and keeps newest first`() {
		val timestamps = ArrayDeque(listOf(100L, 200L, 300L))
		val repository = WatchProgressRepository(
			storage = FakeWatchProgressStorage(),
			nowEpochMs = { timestamps.removeFirst() },
		)

		repository.record(movie(603, "The Matrix"), result("matrix-old"), 1_000L, 10_000L)
		repository.record(movie(438631, "Dune"), result("dune"), 2_000L, 10_000L)
		repository.record(movie(603, "The Matrix"), result("matrix-new"), 3_000L, 10_000L)

		assertEquals(listOf(603, 438631), repository.entries.value.map { it.movie.tmdbId })
		assertEquals("matrix-new", repository.entries.value.first().result.id)
		assertEquals(3_000L, repository.entries.value.first().positionMs)
	}

	private fun movie(id: Int, title: String) = Movie(
		tmdbId = id,
		title = title,
		originalTitle = title,
		releaseYear = 1999,
		overview = null,
		voteAverage = null,
		posterPath = "/poster.jpg",
		backdropPath = null,
	)

	private fun result(id: String) = TorrentSearchResult(
		id = id,
		title = id,
		magnetUri = "magnet:?xt=urn:btih:$id",
	)
}

private class FakeWatchProgressStorage : WatchProgressStorage {
	var saved: List<WatchProgressEntry> = emptyList()

	override fun load(): List<WatchProgressEntry> = saved

	override fun save(entries: List<WatchProgressEntry>) {
		saved = entries
	}
}
