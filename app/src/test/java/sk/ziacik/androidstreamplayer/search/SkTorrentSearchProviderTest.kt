package sk.ziacik.androidstreamplayer.search

import kotlinx.coroutines.test.runTest
import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SkTorrentSearchProviderTest {
	@Test
	fun `search uses public movie search and maps infohash to playable magnet`() = runTest {
		val hash = "99e9e4198403b74f43a5f934df1409ba447bedec"
		val transport = RecordingTransport(
			TorrentSearchHttpResponse(
				code = 200,
				body = """
					<table class="lista"><tbody><tr><td>
					<table class="lista"><tbody>
					<tr>
					  <td><a href="torrents.php?category=1">Filmy CZ/SK dabing</a></td>
					  <td>
					    <a href="details.php?id=$hash" title="Drama / Lóve 2 (2024)(SK)[1080p] = CSFD 55%">Lóve 2</a>
					    <a href="download.php?id=$hash&f=Love.2.mkv">download</a>
					  </td>
					  <td>Velkost 3.8 GB | Pridany 05/03/2025</td>
					  <td>ignored</td>
					  <td>12</td>
					  <td>0</td>
					</tr>
					</tbody></table>
					</td></tr></tbody></table>
				""".trimIndent(),
			),
		)
		val provider = SkTorrentSearchProvider(transport)

		val results = provider.search(movieRequest("Lóve 2"))

		assertEquals(1, transport.requests.size)
		val request = transport.requests.single()
		assertEquals("GET", request.method)
		assertEquals("Lóve 2", request.url.queryParameter("search"))
		assertEquals("0", request.url.queryParameter("active"))
		assertTrue(request.url.queryParameter("category")!!.contains("1"))

		val result = results.single()
		assertEquals(hash, result.id)
		assertEquals("Lóve 2 (2024)(SK)[1080p]", result.title)
		assertEquals(12, result.seeders)
		assertEquals((3.8 * 1024 * 1024 * 1024).toLong(), result.sizeBytes)
		assertEquals("SkTorrent", result.source)
		assertTrue(result.magnetUri.startsWith("magnet:?xt=urn:btih:$hash"))
		assertTrue(result.magnetUri.contains("announce.sktorrent.eu"))
		assertTrue(result.magnetUri.contains("tracker.opentrackr.org"))
	}

	@Test
	fun `fallback searches deduplicate the same SkTorrent infohash`() = runTest {
		val hash = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
		val html = """
			<table><tr>
			<td>movie</td>
			<td>
			  <a href="details.php?id=$hash" title="Film (2020)(CZ)[1080p]">Film</a>
			  <a href="download.php?id=$hash">download</a>
			</td>
			<td>Velkost 1.0 GB |</td><td>x</td><td>5</td><td>0</td>
			</tr></table>
		""".trimIndent()
		val transport = QueueTransport(
			listOf(
				TorrentSearchHttpResponse(200, html),
				TorrentSearchHttpResponse(200, html.replace(">5<", ">20<")),
			),
		)
		val provider = SkTorrentSearchProvider(transport)

		val results = provider.search(
			MovieTorrentSearchRequest(
				tmdbId = 1,
				imdbId = null,
				title = "Film",
				originalTitle = "Original Film",
				year = null,
			),
		)

		assertEquals(1, results.size)
		assertEquals(20, results.single().seeders)
	}

	private fun movieRequest(title: String) = MovieTorrentSearchRequest(
		tmdbId = 0,
		imdbId = null,
		title = title,
		originalTitle = title,
		year = null,
	)

	private class RecordingTransport(
		private val response: TorrentSearchHttpResponse,
	) : TorrentSearchHttpTransport {
		val requests = mutableListOf<Request>()

		override suspend fun execute(request: Request): TorrentSearchHttpResponse {
			requests += request
			return response
		}
	}

	private class QueueTransport(
		responses: List<TorrentSearchHttpResponse>,
	) : TorrentSearchHttpTransport {
		private val responses = ArrayDeque(responses)
		override suspend fun execute(request: Request): TorrentSearchHttpResponse = responses.removeFirst()
	}
}
