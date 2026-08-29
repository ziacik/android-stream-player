package sk.ziacik.androidstreamplayer.player

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSourceException
import androidx.media3.datasource.DataSpec
import java.io.IOException
import sk.ziacik.androidstreamplayer.torrent.TorrentPieceAccess

@UnstableApi
class TorrentDataSource(
    private val access: TorrentPieceAccess,
) : BaseDataSource(false) {
    private var currentUri: Uri? = null
    private var readPosition = 0L
    private var bytesRemaining = 0L
    private var opened = false

    override fun open(dataSpec: DataSpec): Long {
        transferInitializing(dataSpec)

        if (dataSpec.position > access.fileLength) {
            throw DataSourceException(
                PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE,
            )
        }

        currentUri = dataSpec.uri
        readPosition = dataSpec.position
        bytesRemaining = if (dataSpec.length == C.LENGTH_UNSET.toLong()) {
            access.fileLength - dataSpec.position
        } else {
            dataSpec.length
        }

        if (dataSpec.position < access.fileLength) {
            access.reprioritize(dataSpec.position)
        }

        opened = true
        transferStarted(dataSpec)
        return bytesRemaining
    }

    @Throws(IOException::class)
    override fun read(
        buffer: ByteArray,
        offset: Int,
        length: Int,
    ): Int {
        if (length == 0) return 0
        if (bytesRemaining == 0L || readPosition >= access.fileLength) {
            return C.RESULT_END_OF_INPUT
        }

        val readableInFile = access.fileLength - readPosition
        val requested = minOf(length.toLong(), bytesRemaining, readableInFile).toInt()
        if (requested == 0) return C.RESULT_END_OF_INPUT

        val read = access.readVerified(
            positionBytes = readPosition,
            buffer = buffer,
            offset = offset,
            length = requested,
        )
        if (read == -1) return C.RESULT_END_OF_INPUT

        readPosition += read
        bytesRemaining -= read
        bytesTransferred(read)
        return read
    }

    override fun getUri(): Uri? = currentUri

    override fun close() {
        currentUri = null
        readPosition = 0
        bytesRemaining = 0
        access.cancelReader()

        if (opened) {
            opened = false
            transferEnded()
        }
    }
}
