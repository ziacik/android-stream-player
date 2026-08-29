package sk.ziacik.androidstreamplayer.search

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeTorrentSearchProviderTest {
    @Test
    fun `blank query returns no results`() = runTest {
        val provider = FakeTorrentSearchProvider()

        assertTrue(provider.search("   ").isEmpty())
    }

    @Test
    fun `movie query returns deterministic quality variants`() = runTest {
        val provider = FakeTorrentSearchProvider()

        val results = provider.search("Alien")

        assertEquals(listOf("2160p", "1080p", "720p"), results.map { it.quality })
        assertTrue(results.all { it.title.contains("Alien") })
        assertTrue(results.all { it.source == "Fake" })
        assertTrue(results.all { it.magnetUri.startsWith("magnet:?") })
    }
}
