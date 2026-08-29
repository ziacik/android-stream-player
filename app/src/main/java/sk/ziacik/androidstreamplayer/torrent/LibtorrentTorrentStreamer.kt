package sk.ziacik.androidstreamplayer.torrent

import java.io.IOException
import java.net.URLEncoder
import sk.ziacik.androidstreamplayer.search.TorrentSearchResult

class LibtorrentTorrentStreamer(
    private val session: TorrentSessionBackend,
) : TorrentStreamer {
    @Throws(IOException::class)
    override suspend fun prepare(result: TorrentSearchResult): TorrentSource {
        val metadata = session.fetchMetadata(result.magnetUri)
        val selectedFile = TorrentFileSelector.selectMainVideo(metadata.files)
            ?: throw IOException("Torrent contains no supported video")
        val backend = session.startDownload(metadata, selectedFile)
        val mapper = PieceMapper(
            fileOffsetBytes = selectedFile.torrentOffsetBytes,
            fileSizeBytes = selectedFile.sizeBytes,
            pieceLengthBytes = metadata.pieceLengthBytes,
            torrentPieceCount = metadata.pieceCount,
        )
        val pieceAccess = StreamingTorrentPieceAccess(
            fileName = selectedFile.path.substringAfterLast('/').substringAfterLast('\\'),
            mapper = mapper,
            planner = StreamingPriorityPlanner(mapper),
            backend = backend,
        )
        val encodedFileName = URLEncoder.encode(pieceAccess.fileName, Charsets.UTF_8.name())
            .replace("+", "%20")

        return TorrentSource(
            uri = "torrent://stream/$encodedFileName",
            pieceAccess = pieceAccess,
        )
    }
}
