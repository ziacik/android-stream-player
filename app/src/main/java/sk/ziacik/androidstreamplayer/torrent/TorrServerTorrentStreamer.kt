package sk.ziacik.androidstreamplayer.torrent

import sk.ziacik.androidstreamplayer.search.TorrentSearchResult

internal class TorrServerTorrentStreamer(
    private val runtime: TorrServerRuntime,
    private val torrentFileFetcher: (suspend (String) -> ByteArray)? = null,
) : TorrentStreamer {
    override suspend fun prepare(result: TorrentSearchResult): TorrentSource =
        prepare(result) {}

    override suspend fun prepare(
        result: TorrentSearchResult,
        onStartupStats: (TorrentStartupStats) -> Unit,
    ): TorrentSource {
        runtime.ensureReady()

        val torrentFileUrl = result.torrentFileUrl
        val uri = if (torrentFileUrl != null) {
            val fetcher = requireNotNull(torrentFileFetcher) {
                "Torrent file fetcher is not configured"
            }
            val torrentFile = fetcher(torrentFileUrl)
            runtime.prepareStreamUrl(
                torrentFile = torrentFile,
                torrentFileName = "${result.id}.torrent",
                preferredFilePattern = result.preferredFilePattern,
                onStartupStats = onStartupStats,
            )
        } else {
            val magnet = result.magnetUri.trim()
            require(magnet.startsWith("magnet:?")) {
                "Torrent source must be a magnet URI"
            }
            runtime.prepareStreamUrl(
                magnet = magnet,
                preferredFilePattern = result.preferredFilePattern,
                onStartupStats = onStartupStats,
            )
        }

        return TorrentSource(uri = uri)
    }
}
