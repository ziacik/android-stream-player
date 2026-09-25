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
    fun authenticatedTorrentFileIsPreferredOverMagnet() = runTest {
        val runtime = FakeRuntime()
        val torrentBytes = byteArrayOf(1, 2, 3)
        val streamer = TorrServerTorrentStreamer(
            runtime = runtime,
            torrentFileFetcher = { url ->
                assertEquals("https://sktorrent.eu/torrent/download.php?id=abc", url)
                torrentBytes
            },
        )
        val result = TorrentSearchResult(
            id = "abc",
            title = "Private",
            magnetUri = "magnet:?xt=urn:btih:abc",
            torrentFileUrl = "https://sktorrent.eu/torrent/download.php?id=abc",
            preferredFilePattern = "S02E08",
        )

        val source = streamer.prepare(result)

        assertEquals(1, runtime.ensureReadyCount)
        assertEquals(torrentBytes.toList(), runtime.lastTorrentFile?.toList())
        assertEquals("abc.torrent", runtime.lastTorrentFileName)
        assertEquals("S02E08", runtime.lastPreferredFilePattern)
        assertEquals(null, runtime.lastMagnet)
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
        var lastTorrentFile: ByteArray? = null
        var lastTorrentFileName: String? = null
        var lastPreferredFilePattern: String? = null

        override suspend fun ensureReady() {
            ensureReadyCount++
        }

        override suspend fun prepareStreamUrl(magnet: String): String {
            prepareStreamUrlCalled = true
            prepareStreamUrlCount++
            lastMagnet = magnet
            return "http://127.0.0.1:18090/stream/movie.mkv?link=hash&index=7&preload&play"
        }

        override suspend fun prepareStreamUrl(
            torrentFile: ByteArray,
            torrentFileName: String,
            preferredFilePattern: String?,
            onStartupStats: (TorrentStartupStats) -> Unit,
        ): String {
            prepareStreamUrlCalled = true
            prepareStreamUrlCount++
            lastTorrentFile = torrentFile
            lastTorrentFileName = torrentFileName
            lastPreferredFilePattern = preferredFilePattern
            return "http://127.0.0.1:18090/stream/movie.mkv?link=hash&index=7&preload&play"
        }

        override suspend fun stop() = Unit
    }
}
