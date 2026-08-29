package sk.ziacik.androidstreamplayer.player

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import sk.ziacik.androidstreamplayer.torrent.TorrentSource

@UnstableApi
class Media3PlayerPort(context: Context) : PlayerPort {
    override val player: ExoPlayer = ExoPlayer.Builder(context).build()

    override fun prepare(source: TorrentSource) {
        player.setMediaItem(MediaItem.fromUri(source.uri))
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
