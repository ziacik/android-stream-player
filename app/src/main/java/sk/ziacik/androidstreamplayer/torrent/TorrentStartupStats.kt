package sk.ziacik.androidstreamplayer.torrent

data class TorrentStartupStats(
	val activePeers: Int = 0,
	val totalPeers: Int = 0,
	val connectedSeeders: Int = 0,
	val downloadSpeedBytesPerSecond: Double = 0.0,
	val preloadedBytes: Long = 0L,
	val preloadSizeBytes: Long = 0L,
)
