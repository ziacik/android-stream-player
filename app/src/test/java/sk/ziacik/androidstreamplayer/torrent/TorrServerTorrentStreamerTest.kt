package sk.ziacik.androidstreamplayer.torrent

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import sk.ziacik.androidstreamplayer.search.TorrentSearchResult

class TorrServerTorrentStreamerTest {
    @Test
    fun prepareStartsRuntimeAndReturnsLocalStreamUrl() = runTest {
        val runtime = FakeRuntime()
        val streamer = TorrServerTorrentStreamer(runtime)
        val result = result("magnet:?xt=urn:btih:abcdef&tr=udp://tracker/announce")

        val source = streamer.prepare(result)

        assertEquals(1, runtime.ensureReadyCount)
        assertEquals(result.magnetUri, runtime.lastMagnet)
        assertEquals(1, runtime.lastFileIndex)
        assertEquals("http://127.0.0.1:18090/stream/video", source.uri)
    }

    @Test
    fun invalidMagnetFailsBeforeStartingRuntime() = runTest {
        val runtime = FakeRuntime()
        val streamer = TorrServerTorrentStreamer(runtime)

        val failure = runCatching {
            streamer.prepare(result("https://example.com/file.torrent"))
        }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
        assertEquals(0, runtime.ensureReadyCount)
        assertFalse(runtime.streamUrlCalled)
    }

    private fun result(magnet: String) = TorrentSearchResult(
        id = "proof",
        title = "Proof",
        magnetUri = magnet,
    )

    private class FakeRuntime : TorrServerRuntime {
        var ensureReadyCount = 0
        var streamUrlCalled = false
        var lastMagnet: String? = null
        var lastFileIndex: Int? = null

        override suspend fun ensureReady() {
            ensureReadyCount++
        }

        override fun streamUrl(magnet: String, fileIndex: Int): String {
            streamUrlCalled = true
            lastMagnet = magnet
            lastFileIndex = fileIndex
            return "http://127.0.0.1:18090/stream/video"
        }

        override suspend fun stop() = Unit
    }
}
