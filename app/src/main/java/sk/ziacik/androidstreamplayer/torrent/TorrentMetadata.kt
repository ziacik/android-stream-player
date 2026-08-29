package sk.ziacik.androidstreamplayer.torrent

data class TorrentMetadata(
    val id: String,
    val pieceLengthBytes: Int,
    val pieceCount: Int,
    val files: List<TorrentFileEntry>,
)
