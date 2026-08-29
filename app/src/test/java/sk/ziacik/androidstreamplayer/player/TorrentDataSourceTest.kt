package sk.ziacik.androidstreamplayer.player

import androidx.media3.common.C
import androidx.media3.datasource.DataSourceException
import androidx.media3.datasource.DataSpec
import java.io.IOException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import sk.ziacik.androidstreamplayer.torrent.TorrentPieceAccess

class TorrentDataSourceTest {
    @Test
    fun openAtPositionReprioritizesAndReturnsRemainingLength() {
        val access = FakeTorrentPieceAccess("0123456789".encodeToByteArray())
        val source = TorrentDataSource(access)

        val length = source.open(dataSpec(position = 4))

        assertEquals(6L, length)
        assertEquals(listOf(4L), access.reprioritizedPositions)
    }

    @Test
    fun explicitRequestLengthIsRespected() {
        val access = FakeTorrentPieceAccess("0123456789".encodeToByteArray())
        val source = TorrentDataSource(access)

        val length = source.open(dataSpec(position = 2, length = 3))
        val buffer = ByteArray(8)
        val firstRead = source.read(buffer, 0, buffer.size)
        val secondRead = source.read(buffer, firstRead, buffer.size - firstRead)

        assertEquals(3L, length)
        assertEquals(3, firstRead)
        assertEquals(C.RESULT_END_OF_INPUT, secondRead)
        assertArrayEquals("234".encodeToByteArray(), buffer.copyOf(firstRead))
    }

    @Test
    fun readsAdvanceTheAbsoluteFilePosition() {
        val access = FakeTorrentPieceAccess("0123456789".encodeToByteArray(), maxReadSize = 2)
        val source = TorrentDataSource(access)
        source.open(dataSpec(position = 3))
        val buffer = ByteArray(4)

        val first = source.read(buffer, 0, 4)
        val second = source.read(buffer, first, 4 - first)

        assertEquals(2, first)
        assertEquals(2, second)
        assertEquals(listOf(3L, 5L), access.readPositions)
        assertArrayEquals("3456".encodeToByteArray(), buffer)
    }

    @Test
    fun openAtExactEndSucceedsAndReadReturnsEof() {
        val access = FakeTorrentPieceAccess("0123456789".encodeToByteArray())
        val source = TorrentDataSource(access)

        assertEquals(0L, source.open(dataSpec(position = 10)))
        assertEquals(C.RESULT_END_OF_INPUT, source.read(ByteArray(1), 0, 1))
    }

    @Test
    fun openPastEndThrowsPositionOutOfRange() {
        val access = FakeTorrentPieceAccess("0123456789".encodeToByteArray())
        val source = TorrentDataSource(access)

        val error = org.junit.Assert.assertThrows(IOException::class.java) {
            source.open(dataSpec(position = 11))
        }

        assertTrue(DataSourceException.isCausedByPositionOutOfRange(error))
    }

    @Test
    fun zeroLengthReadReturnsZero() {
        val source = TorrentDataSource(FakeTorrentPieceAccess("abc".encodeToByteArray()))
        source.open(dataSpec())

        assertEquals(0, source.read(ByteArray(1), 0, 0))
    }

    @Test
    fun closeCancelsOnlyTheReader() {
        val access = FakeTorrentPieceAccess("abc".encodeToByteArray())
        val source = TorrentDataSource(access)
        source.open(dataSpec())

        source.close()

        assertEquals(1, access.cancelCount)
    }

    private fun dataSpec(
        position: Long = 0,
        length: Long = C.LENGTH_UNSET.toLong(),
    ): DataSpec = DataSpec.Builder()
        .setUri("torrent://selected-file")
        .setPosition(position)
        .setLength(length)
        .build()

    private class FakeTorrentPieceAccess(
        private val bytes: ByteArray,
        private val maxReadSize: Int = Int.MAX_VALUE,
    ) : TorrentPieceAccess {
        override val fileName: String = "video.mkv"
        override val fileLength: Long = bytes.size.toLong()

        val reprioritizedPositions = mutableListOf<Long>()
        val readPositions = mutableListOf<Long>()
        var cancelCount = 0

        override fun reprioritize(positionBytes: Long) {
            reprioritizedPositions += positionBytes
        }

        override fun readVerified(
            positionBytes: Long,
            buffer: ByteArray,
            offset: Int,
            length: Int,
        ): Int {
            readPositions += positionBytes
            if (positionBytes >= bytes.size) return -1

            val count = minOf(
                length,
                maxReadSize,
                bytes.size - positionBytes.toInt(),
            )
            bytes.copyInto(
                destination = buffer,
                destinationOffset = offset,
                startIndex = positionBytes.toInt(),
                endIndex = positionBytes.toInt() + count,
            )
            return count
        }

        override fun cancelReader() {
            cancelCount++
        }
    }
}
