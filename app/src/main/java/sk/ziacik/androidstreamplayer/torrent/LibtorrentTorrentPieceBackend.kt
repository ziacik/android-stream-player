package sk.ziacik.androidstreamplayer.torrent

import java.io.File
import java.io.IOException
import java.io.InterruptedIOException
import java.io.RandomAccessFile
import org.libtorrent4j.Priority
import org.libtorrent4j.TorrentHandle

class LibtorrentTorrentPieceBackend internal constructor(
    private val setPriority: (Int, Priority) -> Unit,
    private val clearDeadlines: () -> Unit,
    private val setDeadline: (Int, Int) -> Unit,
    private val havePiece: (Int) -> Boolean,
    private val sleep: (Long) -> Unit,
    private val readAt: (Long, ByteArray, Int, Int) -> Int,
) : TorrentPieceBackend {
    constructor(
        handle: TorrentHandle,
        selectedFile: File,
    ) : this(
        setPriority = { piece, priority -> handle.piecePriority(piece, priority) },
        clearDeadlines = handle::clearPieceDeadlines,
        setDeadline = { piece, millis -> handle.setPieceDeadline(piece, millis) },
        havePiece = handle::havePiece,
        sleep = Thread::sleep,
        readAt = RandomAccessReader(selectedFile)::readAt,
    )

    override fun setPiecePriority(piece: Int, priority: TorrentPiecePriority) {
        setPriority(piece, priority.toLibtorrentPriority())
    }

    override fun clearPieceDeadlines() {
        clearDeadlines.invoke()
    }

    override fun setPieceDeadline(piece: Int, deadlineMs: Int) {
        setDeadline(piece, deadlineMs)
    }

    @Throws(IOException::class)
    override fun awaitVerifiedPiece(
        piece: Int,
        isCancelled: () -> Boolean,
    ) {
        while (!havePiece(piece)) {
            if (isCancelled()) throw IOException("Read cancelled")
            try {
                sleep(POLL_INTERVAL_MS)
            } catch (error: InterruptedException) {
                Thread.currentThread().interrupt()
                throw InterruptedIOException("Interrupted waiting for torrent piece").apply {
                    initCause(error)
                }
            }
        }
    }

    @Throws(IOException::class)
    override fun readSelectedFile(
        positionBytes: Long,
        buffer: ByteArray,
        offset: Int,
        length: Int,
    ): Int = readAt(positionBytes, buffer, offset, length)

    private fun TorrentPiecePriority.toLibtorrentPriority(): Priority = when (this) {
        TorrentPiecePriority.DEFAULT -> Priority.DEFAULT
        TorrentPiecePriority.READAHEAD -> Priority.FIVE
        TorrentPiecePriority.TOP -> Priority.TOP_PRIORITY
    }

    private class RandomAccessReader(
        private val file: File,
    ) {
        private var reader: RandomAccessFile? = null

        @Synchronized
        fun readAt(
            positionBytes: Long,
            buffer: ByteArray,
            offset: Int,
            length: Int,
        ): Int {
            val activeReader = reader ?: RandomAccessFile(file, "r").also { reader = it }
            activeReader.seek(positionBytes)
            return activeReader.read(buffer, offset, length)
        }
    }

    private companion object {
        const val POLL_INTERVAL_MS = 50L
    }
}
