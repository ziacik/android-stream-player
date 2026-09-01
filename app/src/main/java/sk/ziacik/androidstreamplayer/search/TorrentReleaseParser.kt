package sk.ziacik.androidstreamplayer.search

fun interface TorrentReleaseParser {
	fun parse(title: String): TorrentReleaseInfo
}

object DefaultTorrentReleaseParser : TorrentReleaseParser {
	override fun parse(title: String): TorrentReleaseInfo {
		if (title.isBlank()) return TorrentReleaseInfo()

		val hdrFormats = buildSet {
			if (DOLBY_VISION.containsMatchIn(title)) add(HdrFormat.DOLBY_VISION)
			if (HDR10_PLUS.containsMatchIn(title)) add(HdrFormat.HDR10_PLUS)
			if (HDR10.containsMatchIn(title)) add(HdrFormat.HDR10)
			if (isEmpty() && GENERIC_HDR.containsMatchIn(title)) add(HdrFormat.HDR)
		}

		return TorrentReleaseInfo(
			resolution = parseResolution(title),
			releaseSource = parseReleaseSource(title),
			videoCodec = parseVideoCodec(title),
			hdrFormats = hdrFormats,
			audioCodec = parseAudioCodec(title),
			audioFeatures = buildSet {
				if (ATMOS.containsMatchIn(title)) add(AudioFeature.ATMOS)
			},
			audioChannels = CHANNELS.find(title)?.groupValues?.get(1),
			bitDepth = BIT_DEPTH.find(title)?.groupValues?.get(1)?.toIntOrNull(),
			languages = parseLanguages(title),
			year = YEAR.find(title)?.value?.toIntOrNull(),
			releaseGroup = parseReleaseGroup(title),
		)
	}

	private fun parseResolution(title: String): VideoResolution? = when {
		P2160.containsMatchIn(title) || FOUR_K.containsMatchIn(title) -> VideoResolution.P2160
		P1080.containsMatchIn(title) -> VideoResolution.P1080
		P720.containsMatchIn(title) -> VideoResolution.P720
		P480.containsMatchIn(title) -> VideoResolution.P480
		else -> null
	}

	private fun parseReleaseSource(title: String): ReleaseSource? = when {
		REMUX.containsMatchIn(title) -> ReleaseSource.REMUX
		BLURAY.containsMatchIn(title) -> ReleaseSource.BLURAY
		WEB_DL.containsMatchIn(title) -> ReleaseSource.WEB_DL
		WEBRIP.containsMatchIn(title) -> ReleaseSource.WEBRIP
		HDTV.containsMatchIn(title) -> ReleaseSource.HDTV
		DVD_RIP.containsMatchIn(title) -> ReleaseSource.DVD_RIP
		else -> null
	}

	private fun parseVideoCodec(title: String): VideoCodec? = when {
		HEVC.containsMatchIn(title) -> VideoCodec.HEVC
		H264.containsMatchIn(title) -> VideoCodec.H264
		AV1.containsMatchIn(title) -> VideoCodec.AV1
		MPEG2.containsMatchIn(title) -> VideoCodec.MPEG2
		else -> null
	}

	private fun parseAudioCodec(title: String): AudioCodec? = when {
		TRUE_HD.containsMatchIn(title) -> AudioCodec.TRUE_HD
		DTS_HD_MA.containsMatchIn(title) -> AudioCodec.DTS_HD_MA
		DTS_HD.containsMatchIn(title) -> AudioCodec.DTS_HD
		DTS.containsMatchIn(title) -> AudioCodec.DTS
		E_AC3.containsMatchIn(title) -> AudioCodec.E_AC3
		AC3.containsMatchIn(title) -> AudioCodec.AC3
		AAC.containsMatchIn(title) -> AudioCodec.AAC
		FLAC.containsMatchIn(title) -> AudioCodec.FLAC
		PCM.containsMatchIn(title) -> AudioCodec.PCM
		else -> null
	}

	private fun parseLanguages(title: String): List<String> = buildList {
		LANGUAGES.forEach { language ->
			if (language.regex.containsMatchIn(title)) add(language.label)
		}
	}

	private fun parseReleaseGroup(title: String): String? {
		val group = RELEASE_GROUP.find(title.trim())?.groupValues?.getOrNull(1) ?: return null
		if (group.uppercase() in NON_GROUP_SUFFIXES) return null
		return group
	}

	private data class LanguagePattern(
		val label: String,
		val regex: Regex,
	)

	private fun token(pattern: String): Regex =
		Regex("(?<![A-Za-z0-9])(?:$pattern)(?![A-Za-z0-9])", RegexOption.IGNORE_CASE)

	private fun exactUpperToken(pattern: String): Regex =
		Regex("(?<![A-Za-z0-9])(?:$pattern)(?![A-Za-z0-9])")

	private val P2160 = token("2160P")
	private val FOUR_K = token("4K")
	private val P1080 = token("1080P")
	private val P720 = token("720P")
	private val P480 = token("480P")

	private val REMUX = token("REMUX")
	private val BLURAY = token("BLU[ ._-]?RAY|BD[ ._-]?RIP|BR[ ._-]?RIP")
	private val WEB_DL = token("WEB[ ._-]?DL")
	private val WEBRIP = token("WEB[ ._-]?RIP")
	private val HDTV = token("HDTV")
	private val DVD_RIP = token("DVD[ ._-]?RIP")

	private val HEVC = token("HEVC|H[ ._-]?265|X265")
	private val H264 = token("AVC|H[ ._-]?264|X264")
	private val AV1 = token("AV1")
	private val MPEG2 = token("MPEG[ ._-]?2")

	private val DOLBY_VISION = token("DOLBY[ ._-]?VISION|DOVI|DV")
	private val HDR10_PLUS = token("HDR10(?:\\+|PLUS)")
	private val HDR10 = token("HDR10(?!\\+|PLUS)")
	private val GENERIC_HDR = token("HDR")

	private val TRUE_HD = token("TRUE[ ._-]?HD")
	private val DTS_HD_MA = token("DTS[ ._-]?HD[ ._-]?MA|DTSHDMA")
	private val DTS_HD = token("DTS[ ._-]?HD")
	private val DTS = token("DTS")
	private val E_AC3 = Regex(
		"(?<![A-Za-z0-9])(?:E[ ._-]?AC[ ._-]?3|DDP|DD\\+)(?=\\d|[^A-Za-z0-9]|$)",
		RegexOption.IGNORE_CASE,
	)
	private val AC3 = token("AC[ ._-]?3|DD")
	private val AAC = token("AAC")
	private val FLAC = token("FLAC")
	private val PCM = token("L?PCM")
	private val ATMOS = token("ATMOS")

	private val CHANNELS = Regex("(?<!\\d)(2\\.0|5\\.1|7\\.1)(?!\\d)")
	private val BIT_DEPTH = Regex(
		"(?<![A-Za-z0-9])(8|10)[ ._-]?(?:BIT|BITS|B)(?![A-Za-z0-9])",
		RegexOption.IGNORE_CASE,
	)
	private val YEAR = Regex("(?<!\\d)(?:18(?:8[8-9]|9\\d)|19\\d{2}|20\\d{2})(?!\\d)")

	private val LANGUAGES = listOf(
		LanguagePattern("ENG", exactUpperToken("ENG|EN")),
		LanguagePattern("CZE", exactUpperToken("CZE|CZ")),
		LanguagePattern("SVK", exactUpperToken("SVK|SK")),
		LanguagePattern("GER", exactUpperToken("GER|DE")),
		LanguagePattern("FRA", exactUpperToken("FRA|FR")),
		LanguagePattern("ITA", exactUpperToken("ITA")),
		LanguagePattern("ESP", exactUpperToken("ESP|SPA")),
	)

	private val RELEASE_GROUP = Regex("-([A-Za-z0-9][A-Za-z0-9._]{1,24})$")
	private val NON_GROUP_SUFFIXES = setOf(
		"DL",
		"RIP",
		"HD",
		"MA",
		"VISION",
	)
}
