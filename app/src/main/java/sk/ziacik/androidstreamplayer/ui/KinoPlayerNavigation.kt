package sk.ziacik.androidstreamplayer.ui

internal enum class KinoPlayerFocus {
    PROGRESS,
    SEEK_BACK,
    PLAY_PAUSE,
    SEEK_FORWARD,
}

internal sealed interface KinoPlayerAction {
    data class SeekBy(
        val deltaMs: Long,
        val showOverlay: Boolean,
    ) : KinoPlayerAction

    data class MoveFocus(val focus: KinoPlayerFocus) : KinoPlayerAction

    data class ScrubBy(val deltaMs: Long) : KinoPlayerAction
}

internal fun kinoHorizontalAction(
    overlayVisible: Boolean,
    focus: KinoPlayerFocus,
    direction: Int,
    repeatCount: Int,
): KinoPlayerAction {
    val sign = if (direction < 0) -1L else 1L

    if (!overlayVisible) {
        return KinoPlayerAction.SeekBy(
            deltaMs = sign * 10_000L,
            showOverlay = false,
        )
    }

    if (focus == KinoPlayerFocus.PROGRESS) {
        val stepMs = when {
            repeatCount >= 50 -> 300_000L
            repeatCount >= 20 -> 60_000L
            repeatCount >= 6 -> 30_000L
            else -> 10_000L
        }
        return KinoPlayerAction.ScrubBy(sign * stepMs)
    }

    val nextFocus = when (focus) {
        KinoPlayerFocus.SEEK_BACK -> if (direction > 0) {
            KinoPlayerFocus.PLAY_PAUSE
        } else {
            KinoPlayerFocus.SEEK_BACK
        }

        KinoPlayerFocus.PLAY_PAUSE -> if (direction > 0) {
            KinoPlayerFocus.SEEK_FORWARD
        } else {
            KinoPlayerFocus.SEEK_BACK
        }

        KinoPlayerFocus.SEEK_FORWARD -> if (direction < 0) {
            KinoPlayerFocus.PLAY_PAUSE
        } else {
            KinoPlayerFocus.SEEK_FORWARD
        }

        KinoPlayerFocus.PROGRESS -> KinoPlayerFocus.PROGRESS
    }

    return KinoPlayerAction.MoveFocus(nextFocus)
}

internal fun kinoVerticalFocus(
    focus: KinoPlayerFocus,
    direction: Int,
): KinoPlayerFocus = when {
    direction < 0 && focus != KinoPlayerFocus.PROGRESS -> KinoPlayerFocus.PROGRESS
    direction > 0 && focus == KinoPlayerFocus.PROGRESS -> KinoPlayerFocus.PLAY_PAUSE
    else -> focus
}
