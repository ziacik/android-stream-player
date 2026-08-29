package sk.ziacik.androidstreamplayer.torrent

import java.io.IOException

interface TorrentPieceAccess {
    val fileName: String
    val fileLength: Long

    fun reprioritize(positionBytes: Long)

    @Throws(IOException::class)
    fun readVerified(
        positionBytes: Long,
        buffer: ByteArray,
        offset: Int,
        length: Int,
    ): Int

    fun cancelReader()
}
