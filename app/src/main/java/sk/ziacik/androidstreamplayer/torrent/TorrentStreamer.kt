package sk.ziacik.androidstreamplayer.torrent

import sk.ziacik.androidstreamplayer.search.TorrentSearchResult

fun interface TorrentStreamer {
    suspend fun prepare(result: TorrentSearchResult): TorrentSource
}
