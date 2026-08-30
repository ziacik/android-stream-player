package sk.ziacik.androidstreamplayer.ui

import kotlin.math.abs

private const val WATCH_PROGRESS_PERSIST_INTERVAL_MS = 5_000L

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

internal fun shouldPersistWatchProgress(
    lastPersistedMs: Long?,
    positionMs: Long,
    durationMs: Long?,
): Boolean {
    if (durationMs == null || durationMs <= 0L) return false

    val normalizedPositionMs = positionMs.coerceAtLeast(0L)
    return if (lastPersistedMs == null) {
        normalizedPositionMs >= WATCH_PROGRESS_PERSIST_INTERVAL_MS
    } else {
        abs(normalizedPositionMs - lastPersistedMs) >= WATCH_PROGRESS_PERSIST_INTERVAL_MS
    }
}
