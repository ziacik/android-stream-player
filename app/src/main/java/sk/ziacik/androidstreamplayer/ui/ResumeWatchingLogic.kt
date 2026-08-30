package sk.ziacik.androidstreamplayer.ui

internal const val RESUME_WATCHING_LABEL = "Resume Watching"

internal sealed interface ResumeWatchingKeyAction {
	data object OpenActions : ResumeWatchingKeyAction
	data object Consume : ResumeWatchingKeyAction
	data object PassThrough : ResumeWatchingKeyAction
}

internal enum class ResumeWatchingActivation {
	Resume,
	Cancel,
}

internal data class ResumeActionsKeyDecision(
	val consume: Boolean,
	val waitingForRelease: Boolean,
)

internal data class ResumeActionFocusStyle(
	val scale: Float,
	val borderWidthDp: Int,
)

internal fun resumeWatchingRemoveLabel(): String = "Remove from $RESUME_WATCHING_LABEL"

internal fun resumeActionFocusStyle(focused: Boolean): ResumeActionFocusStyle =
	if (focused) {
		ResumeActionFocusStyle(scale = 1.05f, borderWidthDp = 3)
	} else {
		ResumeActionFocusStyle(scale = 1f, borderWidthDp = 0)
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

internal fun resumeWatchingActivation(isStarting: Boolean): ResumeWatchingActivation =
	if (isStarting) ResumeWatchingActivation.Cancel else ResumeWatchingActivation.Resume

internal fun resumeWatchingOptionsEnabled(isStarting: Boolean): Boolean = !isStarting

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
