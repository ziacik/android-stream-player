package sk.ziacik.androidstreamplayer.torrent

import sk.ziacik.androidstreamplayer.search.TorrentSearchResult

internal class TorrServerTorrentStreamer(
    private val runtime: TorrServerRuntime,
) : TorrentStreamer {
    override suspend fun prepare(result: TorrentSearchResult): TorrentSource =
        prepare(result) {}

    override suspend fun prepare(
        result: TorrentSearchResult,
        onStartupStats: (TorrentStartupStats) -> Unit,
    ): TorrentSource {
        val magnet = result.magnetUri.trim()
        require(magnet.startsWith("magnet:?")) {
            "Torrent source must be a magnet URI"
        }

        runtime.ensureReady()
        return TorrentSource(
            uri = runtime.prepareStreamUrl(
                magnet = magnet,
                onStartupStats = onStartupStats,
            ),
        )
    }
}
