package sk.ziacik.androidstreamplayer.torrent

import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import sk.ziacik.androidstreamplayer.search.TorrentSearchResult

class LibtorrentTorrentStreamerTest {
    @Test
    fun prepareSelectsLargestVideoAndBuildsSeekableSource() = runTest {
        val session = FakeTorrentSessionBackend(
            metadata = TorrentMetadata(
                id = "torrent-1",
                pieceLengthBytes = 1024,
                pieceCount = 32,
                files = listOf(
                    TorrentFileEntry(0, "sample.txt", 20, 0),
                    TorrentFileEntry(1, "small.mp4", 3_000, 20),
                    TorrentFileEntry(2, "Movie Name.mkv", 20_000, 3_020),
                ),
            ),
        )
        val streamer = LibtorrentTorrentStreamer(session)

        val source = streamer.prepare(result("magnet:?xt=urn:btih:test"))

        assertEquals("magnet:?xt=urn:btih:test", session.requestedMagnet)
        assertEquals(2, session.selectedFile?.index)
        assertEquals("Movie Name.mkv", source.pieceAccess?.fileName)
        assertEquals(20_000L, source.pieceAccess?.fileLength)
        assertTrue(source.uri.endsWith("Movie%20Name.mkv"))
        assertNotNull(source.pieceAccess)
    }

    @Test
    fun prepareFailsWhenTorrentHasNoSupportedVideo() = runTest {
        val session = FakeTorrentSessionBackend(
            metadata = TorrentMetadata(
                id = "torrent-1",
                pieceLengthBytes = 1024,
                pieceCount = 2,
                files = listOf(TorrentFileEntry(0, "readme.txt", 100, 0)),
            ),
        )
        val streamer = LibtorrentTorrentStreamer(session)

        assertThrows(IOException::class.java) {
            kotlinx.coroutines.runBlocking {
                streamer.prepare(result("magnet:?xt=urn:btih:test"))
            }
        }
    }

    private fun result(magnet: String) = TorrentSearchResult(
        id = "result",
        title = "Movie",
        magnetUri = magnet,
    )

    private class FakeTorrentSessionBackend(
        private val metadata: TorrentMetadata,
    ) : TorrentSessionBackend {
        var requestedMagnet: String? = null
        var selectedFile: TorrentFileEntry? = null

        override suspend fun fetchMetadata(magnetUri: String): TorrentMetadata {
            requestedMagnet = magnetUri
            return metadata
        }

        override suspend fun startDownload(
            metadata: TorrentMetadata,
            selectedFile: TorrentFileEntry,
        ): TorrentPieceBackend {
            this.selectedFile = selectedFile
            return object : TorrentPieceBackend {
                override fun setPiecePriority(piece: Int, priority: TorrentPiecePriority) = Unit
                override fun clearPieceDeadlines() = Unit
                override fun setPieceDeadline(piece: Int, deadlineMs: Int) = Unit
                override fun awaitVerifiedPiece(piece: Int, isCancelled: () -> Boolean) = Unit
                override fun readSelectedFile(
                    positionBytes: Long,
                    buffer: ByteArray,
                    offset: Int,
                    length: Int,
                ): Int = -1
            }
        }
    }
}
