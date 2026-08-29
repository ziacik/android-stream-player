package sk.ziacik.androidstreamplayer.torrent

import java.io.IOException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.libtorrent4j.Priority

class LibtorrentTorrentPieceBackendTest {
    @Test
    fun domainPrioritiesMapToLibtorrentPriorities() {
        val calls = mutableListOf<Pair<Int, Priority>>()
        val backend = backend(
            setPriority = { piece, priority -> calls += piece to priority },
        )

        backend.setPiecePriority(1, TorrentPiecePriority.DEFAULT)
        backend.setPiecePriority(2, TorrentPiecePriority.READAHEAD)
        backend.setPiecePriority(3, TorrentPiecePriority.TOP)

        assertEquals(
            listOf(
                1 to Priority.DEFAULT,
                2 to Priority.FIVE,
                3 to Priority.TOP_PRIORITY,
            ),
            calls,
        )
    }

    @Test
    fun deadlinesDelegateToTorrentHandleBoundary() {
        var clears = 0
        val deadlines = mutableListOf<Pair<Int, Int>>()
        val backend = backend(
            clearDeadlines = { clears++ },
            setDeadline = { piece, millis -> deadlines += piece to millis },
        )

        backend.clearPieceDeadlines()
        backend.setPieceDeadline(7, 500)

        assertEquals(1, clears)
        assertEquals(listOf(7 to 500), deadlines)
    }

    @Test
    fun waitPollsUntilPieceIsVerified() {
        var probes = 0
        val sleeps = mutableListOf<Long>()
        val backend = backend(
            havePiece = {
                probes++
                probes >= 3
            },
            sleep = { millis -> sleeps += millis },
        )

        backend.awaitVerifiedPiece(9) { false }

        assertEquals(3, probes)
        assertEquals(listOf(50L, 50L), sleeps)
    }

    @Test
    fun cancelledWaitStopsBeforeSleeping() {
        var sleeps = 0
        val backend = backend(
            havePiece = { false },
            sleep = { sleeps++ },
        )

        assertThrows(IOException::class.java) {
            backend.awaitVerifiedPiece(4) { true }
        }
        assertEquals(0, sleeps)
    }

    @Test
    fun selectedFileReadUsesAbsoluteFilePosition() {
        val data = "0123456789".encodeToByteArray()
        val reads = mutableListOf<Pair<Long, Int>>()
        val backend = backend(
            readAt = { position, buffer, offset, length ->
                reads += position to length
                val count = minOf(length, data.size - position.toInt())
                data.copyInto(
                    destination = buffer,
                    destinationOffset = offset,
                    startIndex = position.toInt(),
                    endIndex = position.toInt() + count,
                )
                count
            },
        )
        val buffer = ByteArray(3)

        val read = backend.readSelectedFile(4, buffer, 0, buffer.size)

        assertEquals(3, read)
        assertEquals(listOf(4L to 3), reads)
        assertArrayEquals("456".encodeToByteArray(), buffer)
    }

    private fun backend(
        setPriority: (Int, Priority) -> Unit = { _, _ -> },
        clearDeadlines: () -> Unit = {},
        setDeadline: (Int, Int) -> Unit = { _, _ -> },
        havePiece: (Int) -> Boolean = { true },
        sleep: (Long) -> Unit = {},
        readAt: (Long, ByteArray, Int, Int) -> Int = { _, _, _, _ -> -1 },
    ): LibtorrentTorrentPieceBackend = LibtorrentTorrentPieceBackend(
        setPriority = setPriority,
        clearDeadlines = clearDeadlines,
        setDeadline = setDeadline,
        havePiece = havePiece,
        sleep = sleep,
        readAt = readAt,
    )
}
