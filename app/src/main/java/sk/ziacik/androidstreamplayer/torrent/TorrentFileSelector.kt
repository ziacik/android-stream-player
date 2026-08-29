package sk.ziacik.androidstreamplayer.torrent

object TorrentFileSelector {
    private val supportedExtensions = setOf("mkv", "mp4", "m4v", "webm", "ts")

    fun selectMainVideo(files: List<TorrentFileEntry>): TorrentFileEntry? =
        files
            .asSequence()
            .filter { file ->
                file.path.substringAfterLast('.', missingDelimiterValue = "")
                    .lowercase() in supportedExtensions
            }
            .maxByOrNull(TorrentFileEntry::sizeBytes)
}
