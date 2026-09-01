package sk.ziacik.androidstreamplayer.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import sk.ziacik.androidstreamplayer.search.AudioCodec
import sk.ziacik.androidstreamplayer.search.AudioFeature
import sk.ziacik.androidstreamplayer.search.HdrFormat
import sk.ziacik.androidstreamplayer.search.ReleaseSource
import sk.ziacik.androidstreamplayer.search.TorrentReleaseInfo
import sk.ziacik.androidstreamplayer.search.VideoCodec
import sk.ziacik.androidstreamplayer.search.VideoResolution

class TorrentReleaseBadgeLabelsTest {
	@Test
	fun `badge labels use stable scan friendly order`() {
		val info = TorrentReleaseInfo(
			resolution = VideoResolution.P2160,
			releaseSource = ReleaseSource.REMUX,
			videoCodec = VideoCodec.HEVC,
			hdrFormats = setOf(HdrFormat.HDR10, HdrFormat.DOLBY_VISION),
			audioCodec = AudioCodec.TRUE_HD,
			audioFeatures = setOf(AudioFeature.ATMOS),
			audioChannels = "7.1",
			languages = listOf("ENG"),
			year = 2024,
		)

		assertEquals(
			listOf(
				"4K",
				"REMUX",
				"DV",
				"HDR10",
				"HEVC",
				"TrueHD",
				"Atmos",
				"7.1",
				"ENG",
				"2024",
			),
			torrentReleaseBadgeLabels(info),
		)
	}

	@Test
	fun `missing release metadata produces no placeholder badges`() {
		assertEquals(emptyList<String>(), torrentReleaseBadgeLabels(TorrentReleaseInfo()))
	}
}
