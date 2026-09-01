package sk.ziacik.androidstreamplayer.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TorrentReleaseParserTest {
	@Test
	fun `parses remux dolby vision hdr and lossless atmos release`() {
		val info = DefaultTorrentReleaseParser.parse(
			"Dune.Part.Two.2024.2160p.UHD.BluRay.REMUX.DV.HDR10.HEVC.TrueHD.Atmos.7.1-GROUP",
		)

		assertEquals(VideoResolution.P2160, info.resolution)
		assertEquals(ReleaseSource.REMUX, info.releaseSource)
		assertEquals(VideoCodec.HEVC, info.videoCodec)
		assertEquals(
			setOf(HdrFormat.DOLBY_VISION, HdrFormat.HDR10),
			info.hdrFormats,
		)
		assertEquals(AudioCodec.TRUE_HD, info.audioCodec)
		assertEquals(setOf(AudioFeature.ATMOS), info.audioFeatures)
		assertEquals("7.1", info.audioChannels)
		assertEquals(2024, info.year)
		assertEquals("GROUP", info.releaseGroup)
	}

	@Test
	fun `parses compact web dl ddp channels and h264 aliases`() {
		val info = DefaultTorrentReleaseParser.parse(
			"Movie.2023.1080p.WEB-DL.DDP5.1.H.264-GROUP",
		)

		assertEquals(VideoResolution.P1080, info.resolution)
		assertEquals(ReleaseSource.WEB_DL, info.releaseSource)
		assertEquals(VideoCodec.H264, info.videoCodec)
		assertEquals(AudioCodec.E_AC3, info.audioCodec)
		assertEquals("5.1", info.audioChannels)
		assertEquals(2023, info.year)
	}

	@Test
	fun `normalizes bluray x264 and dts`() {
		val info = DefaultTorrentReleaseParser.parse(
			"Movie.1999.720p.BluRay.x264.DTS-GROUP",
		)

		assertEquals(VideoResolution.P720, info.resolution)
		assertEquals(ReleaseSource.BLURAY, info.releaseSource)
		assertEquals(VideoCodec.H264, info.videoCodec)
		assertEquals(AudioCodec.DTS, info.audioCodec)
		assertEquals(1999, info.year)
	}

	@Test
	fun `specific hdr and audio formats win over generic prefixes`() {
		val info = DefaultTorrentReleaseParser.parse(
			"Movie.2025.2160p.WEBDL.HDR10Plus.AV1.EAC3.5.1.DTS-HD.MA",
		)

		assertEquals(ReleaseSource.WEB_DL, info.releaseSource)
		assertEquals(VideoCodec.AV1, info.videoCodec)
		assertEquals(setOf(HdrFormat.HDR10_PLUS), info.hdrFormats)
		assertEquals(AudioCodec.DTS_HD_MA, info.audioCodec)
		assertEquals("5.1", info.audioChannels)
	}

	@Test
	fun `parses mixed separators aliases bit depth and languages`() {
		val info = DefaultTorrentReleaseParser.parse(
			"Film [2022] 4K_WEBRip_DoVi_H265_10bit_DDP_Atmos_7.1_ENG_CZE-SOMEGROUP",
		)

		assertEquals(VideoResolution.P2160, info.resolution)
		assertEquals(ReleaseSource.WEBRIP, info.releaseSource)
		assertEquals(VideoCodec.HEVC, info.videoCodec)
		assertEquals(setOf(HdrFormat.DOLBY_VISION), info.hdrFormats)
		assertEquals(AudioCodec.E_AC3, info.audioCodec)
		assertEquals(setOf(AudioFeature.ATMOS), info.audioFeatures)
		assertEquals("7.1", info.audioChannels)
		assertEquals(10, info.bitDepth)
		assertEquals(listOf("ENG", "CZE"), info.languages)
		assertEquals(2022, info.year)
	}

	@Test
	fun `generic hdr is omitted when a specific hdr format exists`() {
		val info = DefaultTorrentReleaseParser.parse(
			"Movie.2024.2160p.HDR.HDR10.DV",
		)

		assertEquals(
			setOf(HdrFormat.DOLBY_VISION, HdrFormat.HDR10),
			info.hdrFormats,
		)
	}

	@Test
	fun `does not guess metadata from ordinary words`() {
		val info = DefaultTorrentReleaseParser.parse(
			"Adventure.Drive.DVD.Documentary.1800.Random.Title",
		)

		assertNull(info.resolution)
		assertNull(info.videoCodec)
		assertNull(info.audioCodec)
		assertNull(info.audioChannels)
		assertNull(info.year)
		assertTrue(info.hdrFormats.isEmpty())
		assertTrue(info.languages.isEmpty())
	}

	@Test
	fun `empty title returns empty metadata`() {
		assertEquals(TorrentReleaseInfo(), DefaultTorrentReleaseParser.parse(""))
	}
}
