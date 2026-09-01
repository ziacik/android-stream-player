package sk.ziacik.androidstreamplayer.search

enum class VideoResolution(
	val label: String,
	val releaseLabel: String,
) {
	P2160("4K", "2160p"),
	P1080("1080p", "1080p"),
	P720("720p", "720p"),
	P480("480p", "480p"),
}

enum class ReleaseSource(val label: String) {
	REMUX("REMUX"),
	BLURAY("BluRay"),
	WEB_DL("WEB-DL"),
	WEBRIP("WEBRip"),
	HDTV("HDTV"),
	DVD_RIP("DVDRip"),
}

enum class VideoCodec(val label: String) {
	HEVC("HEVC"),
	H264("H.264"),
	AV1("AV1"),
	MPEG2("MPEG-2"),
}

enum class HdrFormat(
	val label: String,
	val displayOrder: Int,
) {
	DOLBY_VISION("DV", 0),
	HDR10_PLUS("HDR10+", 1),
	HDR10("HDR10", 2),
	HDR("HDR", 3),
}

enum class AudioCodec(val label: String) {
	TRUE_HD("TrueHD"),
	DTS_HD_MA("DTS-HD MA"),
	DTS_HD("DTS-HD"),
	DTS("DTS"),
	E_AC3("DD+"),
	AC3("DD"),
	AAC("AAC"),
	FLAC("FLAC"),
	PCM("PCM"),
}

enum class AudioFeature(
	val label: String,
	val displayOrder: Int,
) {
	ATMOS("Atmos", 0),
}

data class TorrentReleaseInfo(
	val resolution: VideoResolution? = null,
	val releaseSource: ReleaseSource? = null,
	val videoCodec: VideoCodec? = null,
	val hdrFormats: Set<HdrFormat> = emptySet(),
	val audioCodec: AudioCodec? = null,
	val audioFeatures: Set<AudioFeature> = emptySet(),
	val audioChannels: String? = null,
	val bitDepth: Int? = null,
	val languages: List<String> = emptyList(),
	val year: Int? = null,
	val releaseGroup: String? = null,
)
