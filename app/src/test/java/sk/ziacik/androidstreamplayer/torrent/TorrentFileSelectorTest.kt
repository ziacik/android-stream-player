package sk.ziacik.androidstreamplayer.torrent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TorrentFileSelectorTest {
    @Test
    fun selectsLargestSupportedVideo() {
        val files = listOf(
            TorrentFileEntry(0, "sample.txt", 9_000_000, 0),
            TorrentFileEntry(1, "episode.mkv", 8_000_000, 9_000_000),
            TorrentFileEntry(2, "trailer.mp4", 1_000_000, 17_000_000),
        )

        assertEquals(1, TorrentFileSelector.selectMainVideo(files)?.index)
    }

    @Test
    fun matchesVideoExtensionsCaseInsensitively() {
        val files = listOf(
            TorrentFileEntry(0, "small.MP4", 1_000, 0),
            TorrentFileEntry(1, "large.MKV", 2_000, 1_000),
        )

        assertEquals(1, TorrentFileSelector.selectMainVideo(files)?.index)
    }

    @Test
    fun supportsProofVideoExtensions() {
        val files = listOf(
            TorrentFileEntry(0, "a.m4v", 1_000, 0),
            TorrentFileEntry(1, "b.webm", 2_000, 1_000),
            TorrentFileEntry(2, "c.ts", 3_000, 3_000),
        )

        assertEquals(2, TorrentFileSelector.selectMainVideo(files)?.index)
    }

    @Test
    fun returnsNullWithoutSupportedVideo() {
        val files = listOf(
            TorrentFileEntry(0, "readme.nfo", 100, 0),
            TorrentFileEntry(1, "poster.jpg", 1_000_000, 100),
        )

        assertNull(TorrentFileSelector.selectMainVideo(files))
    }
}
