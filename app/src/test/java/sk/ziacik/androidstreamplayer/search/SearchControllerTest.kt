package sk.ziacik.androidstreamplayer.search

import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import sk.ziacik.androidstreamplayer.torrent.TorrentSource
import sk.ziacik.androidstreamplayer.torrent.TorrentStreamer

class SearchControllerTest {
    @Test
    fun `blank query does not call provider`() = runTest {
        val provider = RecordingProvider()
        val controller = SearchController(this, provider)

        controller.setQuery("   ")
        controller.search()
        advanceUntilIdle()

        assertEquals(0, provider.calls)
        assertFalse(controller.state.value.isSearching)
    }

    @Test
    fun `magnet query becomes direct result without calling provider`() = runTest {
        val provider = RecordingProvider()
        val controller = SearchController(this, provider)
        val magnet = "magnet:?xt=urn:btih:0123456789abcdef"

        controller.setQuery(magnet)
        controller.search()
        advanceUntilIdle()

        assertEquals(0, provider.calls)
        assertEquals(1, controller.state.value.results.size)
        assertEquals(magnet, controller.state.value.results.single().magnetUri)
        assertEquals("Magnet", controller.state.value.results.single().source)
    }

    @Test
    fun `successful search publishes results`() = runTest {
        val expected = result("Alien.1080p", "1080p")
        val controller = SearchController(this, RecordingProvider(results = listOf(expected)))

        controller.setQuery("Alien")
        controller.search()
        advanceUntilIdle()

        assertEquals(listOf(expected), controller.state.value.results)
        assertNull(controller.state.value.errorMessage)
    }

    @Test
    fun `empty response publishes empty results without error`() = runTest {
        val controller = SearchController(this, RecordingProvider())

        controller.setQuery("Unknown")
        controller.search()
        advanceUntilIdle()

        assertTrue(controller.state.value.results.isEmpty())
        assertNull(controller.state.value.errorMessage)
    }

    @Test
    fun `provider failure becomes ui error`() = runTest {
        val controller = SearchController(this, RecordingProvider(error = IllegalStateException("boom")))

        controller.setQuery("Alien")
        controller.search()
        advanceUntilIdle()

        assertEquals("Search failed", controller.state.value.errorMessage)
        assertFalse(controller.state.value.isSearching)
    }

    @Test
    fun `retry repeats last non empty query`() = runTest {
        val provider = RecordingProvider(results = listOf(result("Alien.2160p", "2160p")))
        val controller = SearchController(this, provider)

        controller.setQuery("Alien")
        controller.search()
        advanceUntilIdle()
        controller.setQuery("Changed but not searched")
        controller.retry()
        advanceUntilIdle()

        assertEquals(listOf("Alien", "Alien"), provider.queries)
    }

    @Test
    fun `selecting result prepares torrent and starts playback`() = runTest {
        val selected = result("Alien.2160p", "2160p")
        val source = TorrentSource("torrent://stream/Alien.mkv")
        var preparedResult: TorrentSearchResult? = null
        var playedSource: TorrentSource? = null
        val streamer = TorrentStreamer { result ->
            preparedResult = result
            source
        }
        val controller = SearchController(
            scope = this,
            provider = RecordingProvider(),
            streamer = streamer,
            onStreamReady = { playedSource = it },
        )

        controller.select(selected)
        assertEquals(selected, controller.state.value.selectedResult)
        assertEquals("Preparing stream…", controller.state.value.streamStatus)

        advanceUntilIdle()

        assertEquals(selected, preparedResult)
        assertEquals(source, playedSource)
        assertEquals("Playing", controller.state.value.streamStatus)
    }

    @Test
    fun `exiting playback returns to existing search results`() = runTest {
        val selected = result("Alien.2160p", "2160p")
        val controller = SearchController(
            scope = this,
            provider = RecordingProvider(results = listOf(selected)),
            streamer = TorrentStreamer { TorrentSource("torrent://stream/Alien.mkv") },
        )

        controller.setQuery("Alien")
        controller.search()
        advanceUntilIdle()
        controller.select(selected)
        advanceUntilIdle()

        controller.exitPlayback()

        assertEquals("Alien", controller.state.value.query)
        assertEquals(listOf(selected), controller.state.value.results)
        assertNull(controller.state.value.selectedResult)
        assertNull(controller.state.value.streamStatus)
    }

    @Test
    fun `stream preparation failure becomes stream error`() = runTest {
        val selected = result("Alien.2160p", "2160p")
        val controller = SearchController(
            scope = this,
            provider = RecordingProvider(),
            streamer = TorrentStreamer { throw IllegalStateException("boom") },
        )

        controller.select(selected)
        advanceUntilIdle()

        assertEquals(selected, controller.state.value.selectedResult)
        assertEquals("Stream failed", controller.state.value.streamStatus)
    }

    private fun result(title: String, quality: String) = TorrentSearchResult(
        id = title,
        title = title,
        magnetUri = "magnet:?xt=urn:btih:$title",
        quality = quality,
        sizeBytes = 1_000_000_000,
        seeders = 42,
        source = "Test",
    )

    private class RecordingProvider(
        private val results: List<TorrentSearchResult> = emptyList(),
        private val error: Throwable? = null,
    ) : TorrentSearchProvider {
        var calls = 0
            private set
        val queries = mutableListOf<String>()

        override suspend fun search(query: String): List<TorrentSearchResult> {
            calls += 1
            queries += query
            error?.let { throw it }
            return results
        }
    }
}
