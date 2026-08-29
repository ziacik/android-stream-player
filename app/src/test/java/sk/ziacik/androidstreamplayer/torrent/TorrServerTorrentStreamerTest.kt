package sk.ziacik.androidstreamplayer.torrent

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import sk.ziacik.androidstreamplayer.search.TorrentSearchResult

class TorrServerTorrentStreamerTest {
    @Test
    fun prepareStartsRuntimeAndReturnsPreparedLocalStreamUrl() = runTest {
        val runtime = FakeRuntime()
        val streamer = TorrServerTorrentStreamer(runtime)
        val result = result("magnet:?xt=urn:btih:abcdef&tr=udp://tracker/announce")

        val source = streamer.prepare(result)

        assertEquals(1, runtime.ensureReadyCount)
        assertEquals(result.magnetUri, runtime.lastMagnet)
        assertEquals(1, runtime.prepareStreamUrlCount)
        assertEquals("http://127.0.0.1:18090/stream/movie.mkv?link=hash&index=7&preload&play", source.uri)
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
        assertFalse(runtime.prepareStreamUrlCalled)
    }

    private fun result(magnet: String) = TorrentSearchResult(
        id = "proof",
        title = "Proof",
        magnetUri = magnet,
    )

    private class FakeRuntime : TorrServerRuntime {
        var ensureReadyCount = 0
        var prepareStreamUrlCalled = false
        var prepareStreamUrlCount = 0
        var lastMagnet: String? = null

        override suspend fun ensureReady() {
            ensureReadyCount++
        }

        override suspend fun prepareStreamUrl(magnet: String): String {
            prepareStreamUrlCalled = true
            prepareStreamUrlCount++
            lastMagnet = magnet
            return "http://127.0.0.1:18090/stream/movie.mkv?link=hash&index=7&preload&play"
        }

        override suspend fun stop() = Unit
    }
}
