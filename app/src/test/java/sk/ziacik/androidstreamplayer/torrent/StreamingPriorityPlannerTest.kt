package sk.ziacik.androidstreamplayer.torrent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamingPriorityPlannerTest {
    private val mib = 1024L * 1024L

    @Test
    fun bootstrapPrioritizesHeadAndTail() {
        val planner = planner(fileSizeMiB = 200)

        assertEquals(listOf(0..7, 196..199), planner.bootstrapRanges())
    }

    @Test
    fun bootstrapMergesOverlappingRangesForSmallFile() {
        val planner = planner(fileSizeMiB = 6)

        assertEquals(listOf(0..5), planner.bootstrapRanges())
    }

    @Test
    fun activePlanHasImmediateAndReadaheadWindows() {
        val planner = planner(fileSizeMiB = 200)

        val plan = planner.plan(positionBytes = 100 * mib)

        assertEquals(100..103, plan.topPriorityPieces)
        assertEquals(104..151, plan.readaheadPieces)
        assertEquals(listOf(100, 101, 102, 103), plan.deadlines.map(PieceDeadline::piece))
        assertTrue(plan.deadlines.zipWithNext().all { (a, b) -> a.deadlineMs < b.deadlineMs })
    }

    @Test
    fun seekMovesPriorityWindowsToNewPosition() {
        val planner = planner(fileSizeMiB = 200)

        val before = planner.plan(positionBytes = 10 * mib)
        val after = planner.plan(positionBytes = 120 * mib)

        assertEquals(10..13, before.topPriorityPieces)
        assertEquals(120..123, after.topPriorityPieces)
        assertEquals(124..171, after.readaheadPieces)
    }

    @Test
    fun windowsClampAtEndOfFile() {
        val planner = planner(fileSizeMiB = 200)

        val plan = planner.plan(positionBytes = 198 * mib)

        assertEquals(198..199, plan.topPriorityPieces)
        assertTrue(plan.readaheadPieces.isEmpty())
        assertEquals(listOf(198, 199), plan.deadlines.map(PieceDeadline::piece))
    }

    private fun planner(fileSizeMiB: Int): StreamingPriorityPlanner {
        val mapper = PieceMapper(
            fileOffsetBytes = 0,
            fileSizeBytes = fileSizeMiB * mib,
            pieceLengthBytes = mib.toInt(),
            torrentPieceCount = fileSizeMiB,
        )
        return StreamingPriorityPlanner(mapper)
    }
}
