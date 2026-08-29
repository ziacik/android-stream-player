package sk.ziacik.androidstreamplayer.torrent

data class TorrentSource(
    val uri: String,
    val pieceAccess: TorrentPieceAccess? = null,
)
