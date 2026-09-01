package sk.ziacik.androidstreamplayer.search

import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import sk.ziacik.androidstreamplayer.catalog.Movie
import sk.ziacik.androidstreamplayer.catalog.MovieCatalog
import sk.ziacik.androidstreamplayer.catalog.MovieExternalIds

class TorrentSearchEnrichmentTest {
	@Test
	fun `controller enriches raw provider results with release metadata`() = runTest {
		val raw = TorrentSearchResult(
			id = "matrix",
			title = "The.Matrix.1999.2160p.BluRay.x265.DTS",
			magnetUri = "magnet:?xt=urn:btih:matrix",
			seeders = 42,
		)
		val controller = TorrentSearchController(
			scope = this,
			catalog = FakeCatalog,
			provider = TorrentSearchProvider { listOf(raw) },
		)

		controller.open(matrix())
		advanceUntilIdle()

		val result = controller.state.value.results.single()
		assertEquals(VideoResolution.P2160, result.releaseInfo.resolution)
		assertEquals(ReleaseSource.BLURAY, result.releaseInfo.releaseSource)
		assertEquals(VideoCodec.HEVC, result.releaseInfo.videoCodec)
		assertEquals(AudioCodec.DTS, result.releaseInfo.audioCodec)
		assertEquals(1999, result.releaseInfo.year)
		assertEquals("2160p", result.quality)
	}

	@Test
	fun `parser failure leaves valid torrent result usable`() = runTest {
		val raw = TorrentSearchResult(
			id = "matrix",
			title = "The.Matrix.1999.1080p",
			magnetUri = "magnet:?xt=urn:btih:matrix",
		)
		val controller = TorrentSearchController(
			scope = this,
			catalog = FakeCatalog,
			provider = TorrentSearchProvider { listOf(raw) },
			releaseParser = TorrentReleaseParser { throw IllegalStateException("bad title") },
		)

		controller.open(matrix())
		advanceUntilIdle()

		assertEquals(listOf(raw), controller.state.value.results)
		assertNull(controller.state.value.errorMessage)
	}

	private fun matrix() = Movie(
		tmdbId = 603,
		title = "The Matrix",
		originalTitle = "The Matrix",
		releaseYear = 1999,
		overview = null,
		voteAverage = null,
		posterPath = null,
		backdropPath = null,
	)

	private object FakeCatalog : MovieCatalog {
		override suspend fun search(query: String): List<Movie> = emptyList()

		override suspend fun externalIds(tmdbId: Int): MovieExternalIds =
			MovieExternalIds("tt0133093")
	}
}
