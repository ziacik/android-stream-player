package sk.ziacik.androidstreamplayer.subtitle

import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import sk.ziacik.androidstreamplayer.catalog.Movie
import sk.ziacik.androidstreamplayer.search.TorrentSearchResult

class PodnapisiSubtitleProviderTest {
	@get:Rule
	val temporaryFolder = TemporaryFolder()

	@Test
	fun `find prefers Slovak and best matching release then caches subtitle`() = kotlinx.coroutines.test.runTest {
		val release = "The.Matrix.1999.1080p.BluRay.x264-GROUP"
		val transport = QueueSubtitleTransport(
			SubtitleHttpResponse(
				code = 200,
				body = searchXml(
					candidate("en-exact", release, "2", 9_000),
					candidate("cs-exact", release, "7", 500),
					candidate("sk-cam", "The.Matrix.1999.CAM-X", "37", 5_000),
					candidate("sk-best", release, "37", 20),
				).encodeToByteArray(),
			),
			SubtitleHttpResponse(
				code = 200,
				body = zipSubtitle("The.Matrix.1999.sk.srt", "slovak subtitle"),
			),
		)
		val provider = PodnapisiSubtitleProvider(
			cacheDir = temporaryFolder.newFolder("subtitles"),
			transport = transport,
		)
		val movie = movie()
		val result = result(release)

		val first = provider.find(movie, result)

		assertNotNull(first)
		assertEquals("sk", first?.language)
		assertEquals("Slovak", first?.label)
		assertEquals("application/x-subrip", first?.mimeType)
		assertEquals("slovak subtitle", File(first!!.path).readText())
		assertEquals(2, transport.requests.size)
		assertEquals("The Matrix", transport.requests[0].url.queryParameter("sK"))
		assertEquals("1999", transport.requests[0].url.queryParameter("sY"))
		assertEquals("37,7,2", transport.requests[0].url.queryParameter("sL"))
		assertEquals("1", transport.requests[0].url.queryParameter("sXML"))
		assertTrue(transport.requests[1].url.encodedPath.endsWith("/subtitles/sk-best/download"))

		val second = provider.find(movie, result)

		assertEquals(first, second)
		assertEquals(2, transport.requests.size)
	}

	@Test
	fun `find falls back to Czech when Slovak is unavailable`() = kotlinx.coroutines.test.runTest {
		val release = "Arrival.2016.1080p.WEB-DL.DD5.1.H264-GROUP"
		val transport = QueueSubtitleTransport(
			SubtitleHttpResponse(
				code = 200,
				body = searchXml(
					candidate("en", release, "2", 1_000),
					candidate("cs", release, "7", 10),
				).encodeToByteArray(),
			),
			SubtitleHttpResponse(
				code = 200,
				body = zipSubtitle("Arrival.cs.srt", "czech subtitle"),
			),
		)
		val provider = PodnapisiSubtitleProvider(
			cacheDir = temporaryFolder.newFolder("czech"),
			transport = transport,
		)

		val subtitle = provider.find(
			movie = movie(title = "Arrival", originalTitle = "Arrival", year = 2016, tmdbId = 329865),
			result = result(release),
		)

		assertEquals("cs", subtitle?.language)
		assertEquals("Czech", subtitle?.label)
		assertTrue(transport.requests[1].url.encodedPath.endsWith("/subtitles/cs/download"))
	}

	private class QueueSubtitleTransport(
		vararg responses: SubtitleHttpResponse,
	) : SubtitleHttpTransport {
		private val responses = ArrayDeque(responses.toList())
		val requests = mutableListOf<Request>()

		override suspend fun execute(request: Request): SubtitleHttpResponse {
			requests += request
			return responses.removeFirst()
		}
	}

	private fun candidate(
		pid: String,
		release: String,
		language: String,
		downloads: Int,
	) = """
		<subtitle>
			<pid>$pid</pid>
			<title>The Matrix</title>
			<release>$release</release>
			<language>$language</language>
			<downloads>$downloads</downloads>
		</subtitle>
	""".trimIndent()

	private fun searchXml(vararg candidates: String) = buildString {
		appendLine("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
		appendLine("<results>")
		candidates.forEach(::appendLine)
		append("</results>")
	}

	private fun zipSubtitle(name: String, text: String): ByteArray {
		val output = ByteArrayOutputStream()
		ZipOutputStream(output).use { zip ->
			zip.putNextEntry(ZipEntry(name))
			zip.write(text.encodeToByteArray())
			zip.closeEntry()
		}
		return output.toByteArray()
	}

	private fun movie(
		title: String = "The Matrix",
		originalTitle: String = "The Matrix",
		year: Int = 1999,
		tmdbId: Int = 603,
	) = Movie(
		tmdbId = tmdbId,
		imdbId = null,
		title = title,
		originalTitle = originalTitle,
		releaseYear = year,
		overview = null,
		voteAverage = null,
		posterPath = null,
		backdropPath = null,
	)

	private fun result(title: String) = TorrentSearchResult(
		id = title,
		title = title,
		magnetUri = "magnet:?xt=urn:btih:ABC123",
	)
}
