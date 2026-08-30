package sk.ziacik.androidstreamplayer.ui

internal fun shouldRevealOverlayForPlaybackState(
	isPlaying: Boolean,
	playWhenReady: Boolean,
): Boolean = !isPlaying && !playWhenReady
