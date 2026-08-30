package sk.ziacik.androidstreamplayer.ui

internal sealed interface ResumeWatchingKeyAction {
	data object OpenActions : ResumeWatchingKeyAction
	data object Consume : ResumeWatchingKeyAction
	data object PassThrough : ResumeWatchingKeyAction
}

internal data class ResumeActionsKeyDecision(
	val consume: Boolean,
	val waitingForRelease: Boolean,
)

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

internal fun resumeActionsKeyDecision(
	isConfirm: Boolean,
	isUp: Boolean,
	waitingForRelease: Boolean,
): ResumeActionsKeyDecision = when {
	!waitingForRelease || !isConfirm -> ResumeActionsKeyDecision(
		consume = false,
		waitingForRelease = waitingForRelease,
	)
	isUp -> ResumeActionsKeyDecision(
		consume = true,
		waitingForRelease = false,
	)
	else -> ResumeActionsKeyDecision(
		consume = true,
		waitingForRelease = true,
	)
}

internal fun startingResumeMovieId(
	playbackStatus: String?,
	playbackMovieId: Int?,
): Int? = playbackMovieId?.takeIf { playbackStatus == "Preparing stream…" }
