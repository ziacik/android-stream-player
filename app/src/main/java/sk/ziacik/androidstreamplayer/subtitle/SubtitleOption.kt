package sk.ziacik.androidstreamplayer.subtitle

data class SubtitleOption(
	val id: String,
	val language: String,
	val label: String,
	val release: String,
	val downloads: Int,
	val exactMatch: Boolean = false,
)
