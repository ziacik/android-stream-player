package sk.ziacik.androidstreamplayer.playback

import sk.ziacik.androidstreamplayer.search.TorrentSearchResult
import sk.ziacik.androidstreamplayer.subtitle.SubtitleOption

data class SubtitleUiState(
	val isSearching: Boolean = false,
	val options: List<SubtitleOption> = emptyList(),
	val selectedId: String? = null,
	val loadingId: String? = null,
	val message: String? = null,
)

data class PlaybackUiState(
	val selectedResult: TorrentSearchResult? = null,
	val status: String? = null,
	val subtitles: SubtitleUiState = SubtitleUiState(),
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
