package sk.ziacik.androidstreamplayer.torrent

internal interface TorrServerRuntime {
    suspend fun ensureReady()
    fun streamUrl(magnet: String, fileIndex: Int = 1): String
    suspend fun stop()
}

internal class LocalTorrServerRuntime(
    private val process: TorrServerProcess,
    private val client: TorrServerClient,
) : TorrServerRuntime {
    override suspend fun ensureReady() {
        process.ensureStarted()
    }

    override fun streamUrl(magnet: String, fileIndex: Int): String =
        client.streamUrl(magnet, fileIndex)

    override suspend fun stop() {
        process.stop()
    }
}
