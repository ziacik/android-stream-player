package sk.ziacik.androidstreamplayer.search

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class CompositeTorrentSearchProviderTest {
	@Test
	fun `one failing provider does not hide results from another provider`() = runTest {
		val good = TorrentSearchResult(
			id = "good",
			title = "Film",
			magnetUri = "magnet:?xt=urn:btih:ABC123",
			seeders = 10,
			source = "good",
		)
		val provider = CompositeTorrentSearchProvider(
			listOf(
				TorrentSearchProvider { throw IllegalStateException("down") },
				TorrentSearchProvider { listOf(good) },
			),
		)

		assertEquals(listOf(good), provider.search(movieRequest()))
	}

	@Test
	fun `duplicate hashes keep the result with more seeders`() = runTest {
		val weak = TorrentSearchResult(
			id = "weak",
			title = "Film weak",
			magnetUri = "magnet:?xt=urn:btih:ABC123&dn=Film",
			seeders = 2,
			source = "one",
		)
		val strong = TorrentSearchResult(
			id = "strong",
			title = "Film strong",
			magnetUri = "magnet:?dn=Film&xt=urn:btih:abc123",
			seeders = 20,
			source = "two",
		)
		val provider = CompositeTorrentSearchProvider(
			listOf(
				TorrentSearchProvider { listOf(weak) },
				TorrentSearchProvider { listOf(strong) },
			),
		)

		assertEquals(listOf(strong), provider.search(movieRequest()))
	}

	private fun movieRequest() = MovieTorrentSearchRequest(
		tmdbId = 1,
		imdbId = null,
		title = "Film",
		originalTitle = "Film",
		year = 2020,
	)
}
