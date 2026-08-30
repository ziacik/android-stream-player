package sk.ziacik.androidstreamplayer.playback

import sk.ziacik.androidstreamplayer.search.TorrentSearchResult

data class PlaybackUiState(
	val selectedResult: TorrentSearchResult? = null,
	val status: String? = null,
) {
	val startingResultId: String?
		get() = selectedResult?.id.takeIf { status == "Preparing stream…" }

	val startupErrorMessage: String?
		get() = when (status) {
			"Streaming unavailable",
			"Stream failed",
			"Playback failed",
			-> "Could not start stream. Try another version."

			else -> null
		}
}
