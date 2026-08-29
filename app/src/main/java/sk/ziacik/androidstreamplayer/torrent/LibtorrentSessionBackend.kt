package sk.ziacik.androidstreamplayer.torrent

import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.libtorrent4j.Priority
import org.libtorrent4j.SessionManager
import org.libtorrent4j.TorrentHandle
import org.libtorrent4j.TorrentInfo
import org.libtorrent4j.swig.torrent_flags_t

class LibtorrentSessionBackend(
    private val sessionManager: SessionManager,
    private val rootDir: File,
    private val metadataTimeoutSeconds: Int = 30,
    private val handleTimeoutMs: Long = 10_000,
) : TorrentSessionBackend {
    private val torrentInfos = ConcurrentHashMap<String, TorrentInfo>()

    override suspend fun fetchMetadata(magnetUri: String): TorrentMetadata = withContext(Dispatchers.IO) {
        require(magnetUri.startsWith("magnet:?")) { "Expected a magnet URI" }
        ensureSessionStarted()

        val metadataDir = File(rootDir, "metadata").apply { mkdirs() }
        val metadataBytes = sessionManager.fetchMagnet(
            magnetUri,
            metadataTimeoutSeconds,
            metadataDir,
        ) ?: throw IOException("Timed out fetching torrent metadata")

        val torrentInfo = TorrentInfo.bdecode(metadataBytes)
        if (!torrentInfo.isValid) {
            throw IOException("Invalid torrent metadata")
        }

        val id = UUID.randomUUID().toString()
        torrentInfos[id] = torrentInfo
        val files = torrentInfo.files()

        TorrentMetadata(
            id = id,
            pieceLengthBytes = torrentInfo.pieceLength(),
            pieceCount = torrentInfo.numPieces(),
            files = (0 until files.numFiles()).map { index ->
                TorrentFileEntry(
                    index = index,
                    path = files.filePath(index),
                    sizeBytes = files.fileSize(index),
                    torrentOffsetBytes = files.fileOffset(index),
                )
            },
        )
    }

    override suspend fun startDownload(
        metadata: TorrentMetadata,
        selectedFile: TorrentFileEntry,
    ): TorrentPieceBackend = withContext(Dispatchers.IO) {
        ensureSessionStarted()
        val torrentInfo = torrentInfos[metadata.id]
            ?: throw IOException("Torrent metadata is no longer available")
        if (selectedFile.index !in 0 until torrentInfo.numFiles()) {
            throw IOException("Selected torrent file index is invalid")
        }

        val torrentDir = File(rootDir, metadata.id).apply { mkdirs() }
        val priorities = Priority.array(Priority.IGNORE, torrentInfo.numFiles()).also {
            it[selectedFile.index] = Priority.DEFAULT
        }
        sessionManager.download(
            torrentInfo,
            torrentDir,
            null,
            priorities,
            null,
            torrent_flags_t(),
        )

        val handle = awaitHandle(torrentInfo)
        handle.resume()
        val localFile = File(
            torrentInfo.files().filePath(selectedFile.index, torrentDir.absolutePath),
        )
        LibtorrentTorrentPieceBackend(handle, localFile)
    }

    suspend fun stop() = withContext(Dispatchers.IO) {
        if (sessionManager.isRunning) {
            sessionManager.stop()
        }
        torrentInfos.clear()
    }

    private fun ensureSessionStarted() {
        rootDir.mkdirs()
        if (!sessionManager.isRunning) {
            sessionManager.start()
        }
    }

    private suspend fun awaitHandle(torrentInfo: TorrentInfo): TorrentHandle {
        return try {
            withTimeout(handleTimeoutMs) {
                while (true) {
                    sessionManager.find(torrentInfo.infoHash())?.let { return@withTimeout it }
                    delay(HANDLE_POLL_INTERVAL_MS)
                }
                @Suppress("UNREACHABLE_CODE")
                throw IOException("Torrent handle did not become available")
            }
        } catch (error: TimeoutCancellationException) {
            throw IOException("Timed out waiting for torrent handle", error)
        }
    }

    private companion object {
        const val HANDLE_POLL_INTERVAL_MS = 50L
    }
}
