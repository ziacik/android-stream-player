package sk.ziacik.androidstreamplayer.torrent

data class TorrentFileEntry(
    val index: Int,
    val path: String,
    val sizeBytes: Long,
    val torrentOffsetBytes: Long,
)
