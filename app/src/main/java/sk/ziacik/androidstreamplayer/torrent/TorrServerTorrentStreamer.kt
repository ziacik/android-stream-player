package sk.ziacik.androidstreamplayer.torrent

import sk.ziacik.androidstreamplayer.search.TorrentSearchResult

internal class TorrServerTorrentStreamer(
    private val runtime: TorrServerRuntime,
) : TorrentStreamer {
    override suspend fun prepare(result: TorrentSearchResult): TorrentSource {
        val magnet = result.magnetUri.trim()
        require(magnet.startsWith("magnet:?")) {
            "Torrent source must be a magnet URI"
        }

        runtime.ensureReady()
        return TorrentSource(
            uri = runtime.streamUrl(magnet, fileIndex = 1),
        )
    }
}
