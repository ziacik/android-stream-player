package sk.ziacik.androidstreamplayer.ui

internal fun seekTargetMs(
    currentMs: Long,
    deltaMs: Long,
    durationMs: Long?,
): Long {
    val target = (currentMs + deltaMs).coerceAtLeast(0L)
    val finiteDuration = durationMs?.takeIf { it > 0L }
    return finiteDuration?.let(target::coerceAtMost) ?: target
}

internal fun formatPlaybackTime(positionMs: Long): String {
    val totalSeconds = positionMs.coerceAtLeast(0L) / 1_000L
    val hours = totalSeconds / 3_600L
    val minutes = (totalSeconds % 3_600L) / 60L
    val seconds = totalSeconds % 60L

    return if (hours > 0L) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}
