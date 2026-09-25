package sk.ziacik.androidstreamplayer.search

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope

internal class CompositeTorrentSearchProvider(
	private val providers: List<TorrentSearchProvider>,
) : TorrentSearchProvider {
	override suspend fun search(movie: MovieTorrentSearchRequest): List<TorrentSearchResult> =
		supervisorScope {
			val results = providers
				.map { provider ->
					async {
						try {
							provider.search(movie)
						} catch (error: CancellationException) {
							throw error
						} catch (_: Exception) {
							emptyList()
						}
					}
				}
				.awaitAll()
				.flatten()

			val merged = linkedMapOf<String, TorrentSearchResult>()
			results.forEach { result ->
				val key = result.deduplicationKey()
				val existing = merged[key]
				if (existing == null || result.seederCount() > existing.seederCount()) {
					merged[key] = result
				}
			}

			merged.values.sortedWith(
				compareByDescending<TorrentSearchResult> { it.seederCount() }
					.thenBy { it.title.lowercase() },
			)
		}

	private fun TorrentSearchResult.deduplicationKey(): String {
		val infoHash = INFO_HASH_REGEX.find(magnetUri)
			?.groupValues
			?.getOrNull(1)
			?.lowercase()
		return infoHash?.let { "hash:$it" } ?: "id:${id.lowercase()}"
	}

	private fun TorrentSearchResult.seederCount(): Int = seeders ?: -1

	private companion object {
		val INFO_HASH_REGEX = Regex("""(?i)[?&]xt=urn:btih:([A-Za-z0-9]+)""")
	}
}
