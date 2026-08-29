package sk.ziacik.androidstreamplayer.player

import androidx.media3.common.Player
import sk.ziacik.androidstreamplayer.torrent.TorrentSource

interface PlayerPort {
    val player: Player

    fun prepare(source: TorrentSource)
    fun play()
    fun pause()
    fun release()
}
