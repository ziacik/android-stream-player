package sk.ziacik.androidstreamplayer.ui

import java.util.Locale
import sk.ziacik.androidstreamplayer.torrent.TorrentStartupStats

internal fun formatTorrentStartupStats(stats: TorrentStartupStats): String = buildList {
	add("Peers ${stats.activePeers}/${stats.totalPeers}")
	add("Seeds ${stats.connectedSeeders}")
	add(formatDownloadSpeed(stats.downloadSpeedBytesPerSecond))
	if (stats.preloadSizeBytes > 0L || stats.preloadedBytes > 0L) {
		add("Buffered ${formatBuffered(stats.preloadedBytes, stats.preloadSizeBytes)}")
	}
}.joinToString(" · ")

private fun formatDownloadSpeed(bytesPerSecond: Double): String = when {
	bytesPerSecond >= MEBIBYTE -> String.format(Locale.US, "%.1f MB/s", bytesPerSecond / MEBIBYTE)
	bytesPerSecond >= KIBIBYTE -> String.format(Locale.US, "%.0f kB/s", bytesPerSecond / KIBIBYTE)
	else -> String.format(Locale.US, "%.0f B/s", bytesPerSecond.coerceAtLeast(0.0))
}

private fun formatBuffered(preloadedBytes: Long, preloadSizeBytes: Long): String {
	val loadedMiB = preloadedBytes.coerceAtLeast(0L).toDouble() / MEBIBYTE
	if (preloadSizeBytes <= 0L) {
		return String.format(Locale.US, "%.0f MB", loadedMiB)
	}
	val totalMiB = preloadSizeBytes.toDouble() / MEBIBYTE
	return String.format(Locale.US, "%.0f/%.0f MB", loadedMiB, totalMiB)
}

private const val KIBIBYTE = 1024.0
private const val MEBIBYTE = 1024.0 * 1024.0
