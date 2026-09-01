package sk.ziacik.androidstreamplayer.torrent

internal interface TorrServerRuntime {
    suspend fun ensureReady()
    suspend fun prepareStreamUrl(magnet: String): String
    suspend fun prepareStreamUrl(
        magnet: String,
        onStartupStats: (TorrentStartupStats) -> Unit,
    ): String = prepareStreamUrl(magnet)
    suspend fun stop()
}

internal class LocalTorrServerRuntime(
    private val process: TorrServerProcess,
    private val client: TorrServerClient,
) : TorrServerRuntime {
    override suspend fun ensureReady() {
        process.ensureStarted()
    }

    override suspend fun prepareStreamUrl(magnet: String): String =
        client.prepareStreamUrl(magnet)

    override suspend fun prepareStreamUrl(
        magnet: String,
        onStartupStats: (TorrentStartupStats) -> Unit,
    ): String = client.prepareStreamUrl(
        magnet = magnet,
        onStartupStats = onStartupStats,
    )

    override suspend fun stop() {
        process.stop()
    }
}
