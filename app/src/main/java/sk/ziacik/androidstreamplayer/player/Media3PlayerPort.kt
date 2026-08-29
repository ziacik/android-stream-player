package sk.ziacik.androidstreamplayer.player

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import sk.ziacik.androidstreamplayer.torrent.TorrentSource

@UnstableApi
class Media3PlayerPort(context: Context) : PlayerPort {
    override val player: ExoPlayer = ExoPlayer.Builder(context).build()

    override fun prepare(source: TorrentSource) {
        val mediaItem = MediaItem.fromUri(source.uri)
        val pieceAccess = source.pieceAccess

        if (pieceAccess == null) {
            player.setMediaItem(mediaItem)
        } else {
            val dataSourceFactory = DataSource.Factory {
                TorrentDataSource(pieceAccess)
            }
            val mediaSource = ProgressiveMediaSource.Factory(dataSourceFactory)
                .createMediaSource(mediaItem)
            player.setMediaSource(mediaSource)
        }
        player.prepare()
    }

    override fun play() {
        player.play()
    }

    override fun pause() {
        player.pause()
    }

    override fun release() {
        player.release()
    }
}
