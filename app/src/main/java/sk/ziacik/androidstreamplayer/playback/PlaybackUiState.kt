package sk.ziacik.androidstreamplayer.playback

import sk.ziacik.androidstreamplayer.search.TorrentSearchResult

data class PlaybackUiState(
	val selectedResult: TorrentSearchResult? = null,
	val status: String? = null,
)
