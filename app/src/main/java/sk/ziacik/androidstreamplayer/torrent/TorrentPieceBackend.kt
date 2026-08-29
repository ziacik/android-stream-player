package sk.ziacik.androidstreamplayer.torrent

import java.io.IOException

enum class TorrentPiecePriority {
    DEFAULT,
    READAHEAD,
    TOP,
}

interface TorrentPieceBackend {
    fun setPiecePriority(piece: Int, priority: TorrentPiecePriority)
    fun clearPieceDeadlines()
    fun setPieceDeadline(piece: Int, deadlineMs: Int)

    @Throws(IOException::class)
    fun awaitVerifiedPiece(
        piece: Int,
        isCancelled: () -> Boolean,
    )

    @Throws(IOException::class)
    fun readSelectedFile(
        positionBytes: Long,
        buffer: ByteArray,
        offset: Int,
        length: Int,
    ): Int
}
