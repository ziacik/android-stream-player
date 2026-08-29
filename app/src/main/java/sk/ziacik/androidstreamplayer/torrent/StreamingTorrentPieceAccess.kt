package sk.ziacik.androidstreamplayer.torrent

import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

class StreamingTorrentPieceAccess(
    override val fileName: String,
    private val mapper: PieceMapper,
    private val planner: StreamingPriorityPlanner,
    private val backend: TorrentPieceBackend,
) : TorrentPieceAccess {
    override val fileLength: Long = mapper.fileSizeBytes

    private val cancelled = AtomicBoolean(false)
    private val bootstrapPieces = planner.bootstrapRanges()
        .flatMap { it.asIterable() }
        .toSet()
    private var dynamicPieces: Set<Int> = emptySet()

    init {
        bootstrapPieces.forEach { piece ->
            backend.setPiecePriority(piece, TorrentPiecePriority.TOP)
        }
    }

    override fun reprioritize(positionBytes: Long) {
        cancelled.set(false)

        dynamicPieces.forEach { piece ->
            if (piece !in bootstrapPieces) {
                backend.setPiecePriority(piece, TorrentPiecePriority.DEFAULT)
            }
        }

        backend.clearPieceDeadlines()
        val plan = planner.plan(positionBytes)

        plan.readaheadPieces.forEach { piece ->
            if (piece !in bootstrapPieces) {
                backend.setPiecePriority(piece, TorrentPiecePriority.READAHEAD)
            }
        }
        plan.topPriorityPieces.forEach { piece ->
            backend.setPiecePriority(piece, TorrentPiecePriority.TOP)
        }
        bootstrapPieces.forEach { piece ->
            backend.setPiecePriority(piece, TorrentPiecePriority.TOP)
        }
        plan.deadlines.forEach { deadline ->
            backend.setPieceDeadline(deadline.piece, deadline.deadlineMs)
        }

        dynamicPieces = buildSet {
            addAll(plan.topPriorityPieces)
            addAll(plan.readaheadPieces)
        }
    }

    @Throws(IOException::class)
    override fun readVerified(
        positionBytes: Long,
        buffer: ByteArray,
        offset: Int,
        length: Int,
    ): Int {
        require(positionBytes >= 0)
        require(offset >= 0)
        require(length >= 0)
        require(offset <= buffer.size - length)

        if (length == 0) return 0
        if (cancelled.get()) throw IOException("Read cancelled")
        if (positionBytes >= fileLength) return -1

        val piece = mapper.pieceForFileOffset(positionBytes)
        backend.awaitVerifiedPiece(piece) { cancelled.get() }
        if (cancelled.get()) throw IOException("Read cancelled")

        val readable = minOf(length, mapper.bytesUntilPieceEnd(positionBytes))
        return backend.readSelectedFile(
            positionBytes = positionBytes,
            buffer = buffer,
            offset = offset,
            length = readable,
        )
    }

    override fun cancelReader() {
        cancelled.set(true)
    }
}
