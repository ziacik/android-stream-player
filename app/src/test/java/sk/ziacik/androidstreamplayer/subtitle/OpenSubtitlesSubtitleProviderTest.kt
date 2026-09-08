package sk.ziacik.androidstreamplayer.subtitle

import java.io.File
import kotlinx.coroutines.test.runTest
import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import sk.ziacik.androidstreamplayer.catalog.Movie
import sk.ziacik.androidstreamplayer.search.TorrentSearchResult
import sk.ziacik.androidstreamplayer.torrent.TorrentSource

class OpenSubtitlesSubtitleProviderTest {
	@get:Rule
	val temporaryFolder = TemporaryFolder()

	@Test
	fun `search hashes the prepared stream and returns exact matches first`() = runTest {
		val first = ByteArray(64 * 1024).apply { this[0] = 1 }
		val last = ByteArray(64 * 1024).apply { this[0] = 2 }
		val transport = QueueSubtitleTransport(
			SubtitleHttpResponse(
				code = 206,
				body = first,
				headers = mapOf("Content-Range" to "bytes 0-65535/200000"),
			),
			SubtitleHttpResponse(
				code = 206,
				body = last,
				headers = mapOf("Content-Range" to "bytes 134464-199999/200000"),
			),
			SubtitleHttpResponse(
				code = 200,
				body = searchJson(
					candidate(
						fileId = 11,
						language = "en",
						release = "The.Matrix.1999.1080p.BluRay.x264-GROUP",
						downloads = 100,
						exact = true,
					),
					candidate(
						fileId = 12,
						language = "sk",
						release = "The.Matrix.1999.WEBRip",
						downloads = 5_000,
						exact = false,
					),
				).encodeToByteArray(),
			),
		)
		val provider = OpenSubtitlesSubtitleProvider(
			apiKey = "kino-key",
			cacheDir = temporaryFolder.newFolder("search"),
			transport = transport,
		)

		val options = provider.search(
			movie(),
			result(),
			TorrentSource("http://127.0.0.1:18090/stream/The.Matrix.1999.mkv?link=abc&index=1&play"),
		)

		assertEquals(listOf("11", "12"), options.map { it.id })
		assertTrue(options.first().exactMatch)
		assertFalse(options.last().exactMatch)
		assertEquals("English", options.first().label)

		assertEquals("bytes=0-65535", transport.requests[0].header("Range"))
		assertEquals("bytes=134464-199999", transport.requests[1].header("Range"))
		val searchRequest = transport.requests[2]
		assertEquals("api.opensubtitles.com", searchRequest.url.host)
		assertEquals("/api/v1/subtitles", searchRequest.url.encodedPath)
		assertEquals("sk,cs,en", searchRequest.url.queryParameter("languages"))
		assertEquals("603", searchRequest.url.queryParameter("tmdb_id"))
		assertEquals("0000000000030d43", searchRequest.url.queryParameter("moviehash"))
		assertEquals("kino-key", searchRequest.header("Api-Key"))
		assertTrue(searchRequest.header("User-Agent")!!.startsWith("Kino/"))
	}

	@Test
	fun `search falls back to tmdb id when stream hashing is unavailable`() = runTest {
		val transport = QueueSubtitleTransport(
			SubtitleHttpResponse(code = 500, body = ByteArray(0)),
			SubtitleHttpResponse(code = 200, body = searchJson().encodeToByteArray()),
		)
		val provider = OpenSubtitlesSubtitleProvider(
			apiKey = "kino-key",
			cacheDir = temporaryFolder.newFolder("hash-fallback"),
			transport = transport,
		)

		val options = provider.search(
			movie(),
			result(),
			TorrentSource("http://127.0.0.1:18090/stream/The.Matrix.1999.mkv"),
		)

		assertTrue(options.isEmpty())
		assertEquals(2, transport.requests.size)
		assertEquals("603", transport.requests[1].url.queryParameter("tmdb_id"))
		assertEquals(null, transport.requests[1].url.queryParameter("moviehash"))
	}

	@Test
	fun `download spends quota once and reuses cached subtitle`() = runTest {
		val transport = QueueSubtitleTransport(
			SubtitleHttpResponse(
				code = 200,
				body = """{"link":"https://dl.opensubtitles.com/matrix-sk.srt","file_name":"matrix-sk.srt","remaining":4}""".encodeToByteArray(),
			),
			SubtitleHttpResponse(
				code = 200,
				body = "slovak subtitle".encodeToByteArray(),
			),
		)
		val provider = OpenSubtitlesSubtitleProvider(
			apiKey = "kino-key",
			cacheDir = temporaryFolder.newFolder("download"),
			transport = transport,
		)
		val option = SubtitleOption(
			id = "42",
			language = "sk",
			label = "Slovak",
			release = "The.Matrix.1999.1080p.BluRay.x264-GROUP",
			downloads = 100,
			exactMatch = true,
		)

		val first = provider.download(movie(), result(), option)
		val second = provider.download(movie(), result(), option)

		assertEquals(first, second)
		assertEquals("sk", first?.language)
		assertEquals("Slovak", first?.label)
		assertEquals("application/x-subrip", first?.mimeType)
		assertEquals("slovak subtitle", File(first!!.path).readText())
		assertEquals(2, transport.requests.size)
		assertEquals("POST", transport.requests[0].method)
		assertTrue(requestBody(transport.requests[0]).contains("\"file_id\":42"))
		assertEquals("https://dl.opensubtitles.com/matrix-sk.srt", transport.requests[1].url.toString())
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

	private fun requestBody(request: Request): String {
		val buffer = okio.Buffer()
		request.body!!.writeTo(buffer)
		return buffer.readUtf8()
	}

	private fun candidate(
		fileId: Int,
		language: String,
		release: String,
		downloads: Int,
		exact: Boolean,
	) = """
		{
			"id":"$fileId",
			"type":"subtitle",
			"attributes":{
				"language":"$language",
				"download_count":$downloads,
				"moviehash_match":$exact,
				"release":"$release",
				"files":[{"file_id":$fileId,"cd_number":1,"file_name":"$release.srt"}]
			}
		}
	""".trimIndent()

	private fun searchJson(vararg candidates: String) = """
		{
			"total_pages":1,
			"total_count":${candidates.size},
			"data":[${candidates.joinToString(",")}]
		}
	""".trimIndent()

	private fun movie() = Movie(
		tmdbId = 603,
		imdbId = "tt0133093",
		title = "The Matrix",
		originalTitle = "The Matrix",
		releaseYear = 1999,
		overview = null,
		voteAverage = null,
		posterPath = null,
		backdropPath = null,
	)

	private fun result() = TorrentSearchResult(
		id = "matrix",
		title = "The.Matrix.1999.1080p.BluRay.x264-GROUP",
		magnetUri = "magnet:?xt=urn:btih:matrix",
		seeders = 10,
	)
}
