package sk.ziacik.androidstreamplayer.torrent

import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class StreamingTorrentPieceAccessTest {
    @Test
    fun reprioritizeAppliesBootstrapUrgentReadaheadAndDeadlines() {
        val backend = FakeTorrentPieceBackend()
        val access = access(backend = backend)

        access.reprioritize(32L * MIB)

        assertEquals(TorrentPiecePriority.TOP, backend.priorities[0])
        assertEquals(TorrentPiecePriority.TOP, backend.priorities[7])
        assertEquals(TorrentPiecePriority.TOP, backend.priorities[32])
        assertEquals(TorrentPiecePriority.TOP, backend.priorities[35])
        assertEquals(TorrentPiecePriority.READAHEAD, backend.priorities[36])
        assertEquals(TorrentPiecePriority.READAHEAD, backend.priorities[83])
        assertEquals(TorrentPiecePriority.TOP, backend.priorities[124])
        assertEquals(TorrentPiecePriority.TOP, backend.priorities[127])
        assertEquals(1, backend.clearDeadlineCount)
        assertEquals(250, backend.deadlines[32])
        assertEquals(1_000, backend.deadlines[35])
    }

    @Test
    fun seekDropsOldDynamicPrioritiesButKeepsBootstrap() {
        val backend = FakeTorrentPieceBackend()
        val access = access(backend = backend)
        access.reprioritize(32L * MIB)

        access.reprioritize(88L * MIB)

        assertEquals(TorrentPiecePriority.DEFAULT, backend.priorities[32])
        assertEquals(TorrentPiecePriority.DEFAULT, backend.priorities[60])
        assertEquals(TorrentPiecePriority.TOP, backend.priorities[0])
        assertEquals(TorrentPiecePriority.TOP, backend.priorities[124])
        assertEquals(TorrentPiecePriority.TOP, backend.priorities[88])
        assertEquals(TorrentPiecePriority.READAHEAD, backend.priorities[92])
        assertEquals(2, backend.clearDeadlineCount)
    }

    @Test
    fun readWaitsForVerifiedPieceAndStopsAtPieceBoundary() {
        val backend = FakeTorrentPieceBackend(
            bytes = "abcdefghij".encodeToByteArray(),
        )
        val mapper = PieceMapper(
            fileOffsetBytes = 2,
            fileSizeBytes = 10,
            pieceLengthBytes = 4,
            torrentPieceCount = 4,
        )
        val access = StreamingTorrentPieceAccess(
            fileName = "video.mkv",
            mapper = mapper,
            planner = StreamingPriorityPlanner(mapper),
            backend = backend,
        )
        val buffer = ByteArray(5)

        val read = access.readVerified(0, buffer, 0, buffer.size)

        assertEquals(2, read)
        assertEquals(listOf(0), backend.awaitedPieces)
        assertEquals(listOf(0L to 2), backend.reads)
        assertEquals("ab", buffer.copyOf(read).decodeToString())
    }

    @Test
    fun cancelledReaderFailsWithoutReadingDisk() {
        val backend = FakeTorrentPieceBackend(bytes = "abc".encodeToByteArray())
        val mapper = PieceMapper(0, 3, 4, 1)
        val access = StreamingTorrentPieceAccess(
            fileName = "video.mkv",
            mapper = mapper,
            planner = StreamingPriorityPlanner(mapper),
            backend = backend,
        )
        access.cancelReader()

        assertThrows(IOException::class.java) {
            access.readVerified(0, ByteArray(1), 0, 1)
        }
        assertEquals(emptyList<Pair<Long, Int>>(), backend.reads)
    }

    @Test
    fun openingAfterCancelReenablesReader() {
        val backend = FakeTorrentPieceBackend(bytes = "abc".encodeToByteArray())
        val mapper = PieceMapper(0, 3, 4, 1)
        val access = StreamingTorrentPieceAccess(
            fileName = "video.mkv",
            mapper = mapper,
            planner = StreamingPriorityPlanner(mapper),
            backend = backend,
        )
        access.cancelReader()

        access.reprioritize(0)
        val read = access.readVerified(0, ByteArray(1), 0, 1)

        assertEquals(1, read)
    }

    private fun access(
        backend: FakeTorrentPieceBackend,
    ): StreamingTorrentPieceAccess {
        val mapper = PieceMapper(
            fileOffsetBytes = 0,
            fileSizeBytes = 128L * MIB,
            pieceLengthBytes = MIB,
            torrentPieceCount = 128,
        )
        return StreamingTorrentPieceAccess(
            fileName = "movie.mkv",
            mapper = mapper,
            planner = StreamingPriorityPlanner(mapper),
            backend = backend,
        )
    }

    private class FakeTorrentPieceBackend(
        private val bytes: ByteArray = ByteArray(0),
    ) : TorrentPieceBackend {
        val priorities = mutableMapOf<Int, TorrentPiecePriority>()
        val deadlines = mutableMapOf<Int, Int>()
        val awaitedPieces = mutableListOf<Int>()
        val reads = mutableListOf<Pair<Long, Int>>()
        var clearDeadlineCount = 0

        override fun setPiecePriority(piece: Int, priority: TorrentPiecePriority) {
            priorities[piece] = priority
        }

        override fun clearPieceDeadlines() {
            clearDeadlineCount++
            deadlines.clear()
        }

        override fun setPieceDeadline(piece: Int, deadlineMs: Int) {
            deadlines[piece] = deadlineMs
        }

        override fun awaitVerifiedPiece(piece: Int, isCancelled: () -> Boolean) {
            if (isCancelled()) throw IOException("Read cancelled")
            awaitedPieces += piece
        }

        override fun readSelectedFile(
            positionBytes: Long,
            buffer: ByteArray,
            offset: Int,
            length: Int,
        ): Int {
            reads += positionBytes to length
            if (positionBytes >= bytes.size) return -1
            val count = minOf(length, bytes.size - positionBytes.toInt())
            bytes.copyInto(buffer, offset, positionBytes.toInt(), positionBytes.toInt() + count)
            return count
        }
    }

    private companion object {
        const val MIB = 1024 * 1024
    }
}
