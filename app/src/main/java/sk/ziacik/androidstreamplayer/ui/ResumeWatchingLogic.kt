package sk.ziacik.androidstreamplayer.ui

internal sealed interface ResumeWatchingKeyAction {
	data object OpenActions : ResumeWatchingKeyAction
	data object Consume : ResumeWatchingKeyAction
	data object PassThrough : ResumeWatchingKeyAction
}

internal fun resumeWatchingKeyAction(
	isConfirm: Boolean,
	isDown: Boolean,
	isUp: Boolean,
	repeatCount: Int,
	longPressTriggered: Boolean,
): ResumeWatchingKeyAction = when {
	isConfirm && isDown && repeatCount > 0 && !longPressTriggered -> ResumeWatchingKeyAction.OpenActions
	isConfirm && isDown && repeatCount > 0 -> ResumeWatchingKeyAction.Consume
	isConfirm && isUp && longPressTriggered -> ResumeWatchingKeyAction.Consume
	else -> ResumeWatchingKeyAction.PassThrough
}

internal fun startingResumeMovieId(
	playbackStatus: String,
	playbackMovieId: Int?,
): Int? = playbackMovieId?.takeIf { playbackStatus == "Preparing stream…" }
