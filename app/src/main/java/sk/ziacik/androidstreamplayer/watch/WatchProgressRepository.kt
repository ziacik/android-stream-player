package sk.ziacik.androidstreamplayer.watch

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import sk.ziacik.androidstreamplayer.catalog.Movie
import sk.ziacik.androidstreamplayer.search.TorrentSearchResult

private const val COMPLETION_THRESHOLD = 0.95

data class WatchProgressEntry(
	val movie: Movie,
	val result: TorrentSearchResult,
	val positionMs: Long,
	val durationMs: Long,
	val updatedAtEpochMs: Long,
)

interface WatchProgressStorage {
	fun load(): List<WatchProgressEntry>
	fun save(entries: List<WatchProgressEntry>)
}

class WatchProgressRepository(
	private val storage: WatchProgressStorage,
	private val nowEpochMs: () -> Long = System::currentTimeMillis,
) {
	private val mutableEntries = MutableStateFlow(
		storage.load().sortedByDescending { it.updatedAtEpochMs },
	)
	val entries: StateFlow<List<WatchProgressEntry>> = mutableEntries.asStateFlow()

	fun record(
		movie: Movie,
		result: TorrentSearchResult,
		positionMs: Long,
		durationMs: Long,
	) {
		if (durationMs <= 0L) return

		val normalizedPositionMs = positionMs.coerceAtLeast(0L)
		if (normalizedPositionMs.toDouble() / durationMs.toDouble() >= COMPLETION_THRESHOLD) {
			remove(movie.tmdbId)
			return
		}

		val entry = WatchProgressEntry(
			movie = movie,
			result = result,
			positionMs = normalizedPositionMs,
			durationMs = durationMs,
			updatedAtEpochMs = nowEpochMs(),
		)
		val updated = (mutableEntries.value.filterNot { it.movie.tmdbId == movie.tmdbId } + entry)
			.sortedByDescending { it.updatedAtEpochMs }
		persist(updated)
	}

	fun remove(tmdbId: Int) {
		val updated = mutableEntries.value.filterNot { it.movie.tmdbId == tmdbId }
		if (updated == mutableEntries.value) return
		persist(updated)
	}

	private fun persist(entries: List<WatchProgressEntry>) {
		storage.save(entries)
		mutableEntries.value = entries
	}
}
