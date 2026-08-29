package sk.ziacik.androidstreamplayer.torrent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PieceMapperTest {
    private val mapper = PieceMapper(
        fileOffsetBytes = 300L,
        fileSizeBytes = 3_000L,
        pieceLengthBytes = 1_024,
        torrentPieceCount = 10,
    )

    @Test
    fun mapsStartToContainingTorrentPiece() {
        assertEquals(0, mapper.pieceForFileOffset(0))
        assertEquals(1, mapper.pieceForFileOffset(724))
    }

    @Test
    fun mapsRangeAcrossPieces() {
        assertEquals(PieceRange(0, 2), mapper.piecesForFileRange(0, 2_000))
    }

    @Test
    fun limitsReadsAtPieceBoundary() {
        assertEquals(724, mapper.bytesUntilPieceEnd(0))
    }

    @Test
    fun endOfFileHasNoReadableBytes() {
        assertEquals(0, mapper.bytesUntilPieceEnd(3_000))
    }

    @Test
    fun rejectsNegativePosition() {
        assertThrows(IllegalArgumentException::class.java) {
            mapper.pieceForFileOffset(-1)
        }
    }

    @Test
    fun rejectsPositionPastEnd() {
        assertThrows(IllegalArgumentException::class.java) {
            mapper.pieceForFileOffset(3_001)
        }
    }

    @Test
    fun rejectsRangePastEnd() {
        assertThrows(IllegalArgumentException::class.java) {
            mapper.piecesForFileRange(2_900, 101)
        }
    }
}
