package sk.ziacik.androidstreamplayer.torrent

interface TorrentSessionBackend {
    suspend fun fetchMetadata(magnetUri: String): TorrentMetadata

    suspend fun startDownload(
        metadata: TorrentMetadata,
        selectedFile: TorrentFileEntry,
    ): TorrentPieceBackend
}
