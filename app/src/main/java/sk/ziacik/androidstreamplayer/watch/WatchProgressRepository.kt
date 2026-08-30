package sk.ziacik.androidstreamplayer.watch

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import sk.ziacik.androidstreamplayer.catalog.Movie
import sk.ziacik.androidstreamplayer.search.TorrentSearchResult

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
	private val mutableEntries = MutableStateFlow(storage.load())
	val entries: StateFlow<List<WatchProgressEntry>> = mutableEntries.asStateFlow()

	fun record(
		movie: Movie,
		result: TorrentSearchResult,
		positionMs: Long,
		durationMs: Long,
	) {
		val updated = mutableEntries.value + WatchProgressEntry(
			movie = movie,
			result = result,
			positionMs = positionMs,
			durationMs = durationMs,
			updatedAtEpochMs = nowEpochMs(),
		)
		storage.save(updated)
		mutableEntries.value = updated
	}
}
