package sk.ziacik.androidstreamplayer.torrent

import kotlin.math.min

data class PieceDeadline(
    val piece: Int,
    val deadlineMs: Int,
)

data class StreamingPriorityPlan(
    val topPriorityPieces: IntRange,
    val readaheadPieces: IntRange,
    val deadlines: List<PieceDeadline>,
)

class StreamingPriorityPlanner(
    private val mapper: PieceMapper,
    private val immediateBytes: Long = 4L * 1024 * 1024,
    private val readaheadBytes: Long = 48L * 1024 * 1024,
) {
    fun bootstrapRanges(): List<IntRange> {
        if (mapper.fileSizeBytes == 0L) return emptyList()

        val headLength = min(HEAD_BYTES, mapper.fileSizeBytes)
        val tailStart = (mapper.fileSizeBytes - TAIL_BYTES).coerceAtLeast(0L)
        val tailLength = mapper.fileSizeBytes - tailStart

        val head = mapper.piecesForFileRange(0, headLength).toIntRange()
        val tail = mapper.piecesForFileRange(tailStart, tailLength).toIntRange()

        return mergeRanges(listOf(head, tail))
    }

    fun plan(positionBytes: Long): StreamingPriorityPlan {
        require(positionBytes >= 0 && positionBytes < mapper.fileSizeBytes) {
            "Position $positionBytes is outside file size ${mapper.fileSizeBytes}"
        }

        val remainingBytes = mapper.fileSizeBytes - positionBytes
        val immediateLength = min(immediateBytes, remainingBytes)
        val topPriority = mapper.piecesForFileRange(positionBytes, immediateLength).toIntRange()

        val readaheadStart = positionBytes + immediateLength
        val readaheadRemaining = mapper.fileSizeBytes - readaheadStart
        val readahead = if (readaheadRemaining > 0) {
            val length = min(readaheadBytes, readaheadRemaining)
            mapper.piecesForFileRange(readaheadStart, length).toIntRange()
        } else {
            IntRange.EMPTY
        }

        return StreamingPriorityPlan(
            topPriorityPieces = topPriority,
            readaheadPieces = readahead,
            deadlines = topPriority.mapIndexed { index, piece ->
                PieceDeadline(
                    piece = piece,
                    deadlineMs = FIRST_DEADLINE_MS + index * DEADLINE_STEP_MS,
                )
            },
        )
    }

    private fun mergeRanges(ranges: List<IntRange>): List<IntRange> {
        val sorted = ranges
            .filterNot(IntRange::isEmpty)
            .sortedBy(IntRange::first)

        if (sorted.isEmpty()) return emptyList()

        val merged = mutableListOf<IntRange>()
        var current = sorted.first()

        for (next in sorted.drop(1)) {
            if (next.first <= current.last + 1) {
                current = current.first..maxOf(current.last, next.last)
            } else {
                merged += current
                current = next
            }
        }

        merged += current
        return merged
    }

    private fun PieceRange.toIntRange(): IntRange = first..lastInclusive

    private companion object {
        const val HEAD_BYTES = 8L * 1024 * 1024
        const val TAIL_BYTES = 4L * 1024 * 1024
        const val FIRST_DEADLINE_MS = 250
        const val DEADLINE_STEP_MS = 250
    }
}
