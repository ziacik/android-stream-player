package sk.ziacik.androidstreamplayer.torrent

import kotlin.math.min

data class PieceRange(
    val first: Int,
    val lastInclusive: Int,
)

class PieceMapper(
    private val fileOffsetBytes: Long,
    val fileSizeBytes: Long,
    val pieceLengthBytes: Int,
    private val torrentPieceCount: Int,
) {
    init {
        require(fileOffsetBytes >= 0)
        require(fileSizeBytes >= 0)
        require(pieceLengthBytes > 0)
        require(torrentPieceCount > 0)
    }

    fun pieceForFileOffset(positionBytes: Long): Int {
        require(positionBytes >= 0 && positionBytes < fileSizeBytes) {
            "Position $positionBytes is outside file size $fileSizeBytes"
        }
        return torrentPieceForGlobalOffset(fileOffsetBytes + positionBytes)
    }

    fun piecesForFileRange(positionBytes: Long, lengthBytes: Long): PieceRange {
        require(positionBytes >= 0)
        require(lengthBytes > 0)
        require(positionBytes <= fileSizeBytes - lengthBytes) {
            "Range $positionBytes..${positionBytes + lengthBytes} exceeds file size $fileSizeBytes"
        }

        val globalStart = fileOffsetBytes + positionBytes
        val globalEndInclusive = globalStart + lengthBytes - 1
        return PieceRange(
            first = torrentPieceForGlobalOffset(globalStart),
            lastInclusive = torrentPieceForGlobalOffset(globalEndInclusive),
        )
    }

    fun bytesUntilPieceEnd(positionBytes: Long): Int {
        require(positionBytes >= 0 && positionBytes <= fileSizeBytes) {
            "Position $positionBytes is outside file size $fileSizeBytes"
        }
        if (positionBytes == fileSizeBytes) return 0

        val globalOffset = fileOffsetBytes + positionBytes
        val offsetInPiece = globalOffset % pieceLengthBytes
        val bytesInPiece = pieceLengthBytes - offsetInPiece
        val bytesInFile = fileSizeBytes - positionBytes
        return min(bytesInPiece, bytesInFile).toInt()
    }

    private fun torrentPieceForGlobalOffset(globalOffsetBytes: Long): Int {
        val piece = (globalOffsetBytes / pieceLengthBytes).toInt()
        require(piece in 0 until torrentPieceCount) {
            "Offset $globalOffsetBytes maps outside torrent piece count $torrentPieceCount"
        }
        return piece
    }
}
