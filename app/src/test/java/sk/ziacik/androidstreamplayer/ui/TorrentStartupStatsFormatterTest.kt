package sk.ziacik.androidstreamplayer.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import sk.ziacik.androidstreamplayer.torrent.TorrentStartupStats

class TorrentStartupStatsFormatterTest {
	@Test
	fun `formats peers seeds speed and preload progress`() {
		val text = formatTorrentStartupStats(
			TorrentStartupStats(
				activePeers = 3,
				totalPeers = 17,
				connectedSeeders = 2,
				downloadSpeedBytesPerSecond = 1.8 * 1024 * 1024,
				preloadedBytes = 24L * 1024 * 1024,
				preloadSizeBytes = 50L * 1024 * 1024,
			),
		)

		assertEquals("Peers 3/17 · Seeds 2 · 1.8 MB/s · Buffered 24/50 MB", text)
	}

	@Test
	fun `omits buffer until TorrServer reports preload size`() {
		val text = formatTorrentStartupStats(
			TorrentStartupStats(
				activePeers = 0,
				totalPeers = 4,
				connectedSeeders = 0,
				downloadSpeedBytesPerSecond = 0.0,
			),
		)

		assertEquals("Peers 0/4 · Seeds 0 · 0 B/s", text)
	}
}
