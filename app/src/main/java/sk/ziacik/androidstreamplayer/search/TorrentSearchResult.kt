package sk.ziacik.androidstreamplayer.search

data class TorrentSearchResult(
    val id: String,
    val title: String,
    val magnetUri: String,
    val torrentFileUrl: String? = null,
    val quality: String? = null,
    val sizeBytes: Long? = null,
    val seeders: Int? = null,
    val source: String? = null,
    val preferredFilePattern: String? = null,
    val releaseInfo: TorrentReleaseInfo = TorrentReleaseInfo(),
)
