package sk.ziacik.androidstreamplayer.torrent

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import okhttp3.Request
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TorrServerStartupPollingTest {
	@Test
	fun `prepare polls and reports torrent status while preload is still running`() = runTest {
		val preloadStarted = CompletableDeferred<Unit>()
		val releasePreload = CompletableDeferred<Unit>()
		val requests = mutableListOf<Request>()
		val reportedStats = mutableListOf<TorrentStartupStats>()
		val transport = object : TorrServerHttpTransport {
			override suspend fun execute(request: Request): TorrServerHttpResponse {
				requests += request
				return when {
					request.url.encodedPath == "/torrents" && request.action() == "add" -> {
						TorrServerHttpResponse(
							code = 200,
							body = """{"hash":"hash123","file_stats":[{"id":7,"path":"movie.mkv","length":9000}]}""",
						)
					}

					request.url.encodedPath.startsWith("/stream/") &&
						request.url.queryParameterNames.contains("preload") -> {
						preloadStarted.complete(Unit)
						releasePreload.await()
						TorrServerHttpResponse(code = 200, body = "")
					}

					request.url.encodedPath == "/torrents" && request.action() == "get" -> {
						TorrServerHttpResponse(
							code = 200,
							body = """{"hash":"hash123","active_peers":3,"total_peers":17,"connected_seeders":2,"download_speed":1887436.8,"preloaded_bytes":25165824,"preload_size":52428800,"file_stats":[{"id":7,"path":"movie.mkv","length":9000}]}""",
						)
					}

					else -> TorrServerHttpResponse(code = 500, body = "")
				}
			}
		}
		val client = TorrServerClient(
			transport = transport,
			pollIntervalMs = 1,
		)
		val preparing = async {
			client.prepareStreamUrl(
				magnet = "magnet:?xt=urn:btih:abcdef&dn=Video",
				timeoutMs = 1_000,
				onStartupStats = { reportedStats += it },
			)
		}

		preloadStarted.await()
		advanceTimeBy(5)
		runCurrent()

		try {
			assertTrue(
				"Expected TorrServer status to be polled while preload is blocked",
				requests.any { it.url.encodedPath == "/torrents" && it.action() == "get" },
			)
			val stats = reportedStats.last()
			assertEquals(3, stats.activePeers)
			assertEquals(17, stats.totalPeers)
			assertEquals(2, stats.connectedSeeders)
			assertEquals(1_887_436.8, stats.downloadSpeedBytesPerSecond, 0.01)
			assertEquals(25_165_824L, stats.preloadedBytes)
			assertEquals(52_428_800L, stats.preloadSizeBytes)
		} finally {
			releasePreload.complete(Unit)
			preparing.cancel()
		}
	}

	@Test
	fun `completed preload does not wait for the next telemetry poll`() = runTest {
		val requests = mutableListOf<Request>()
		val transport = object : TorrServerHttpTransport {
			override suspend fun execute(request: Request): TorrServerHttpResponse {
				requests += request
				return when {
					request.url.encodedPath == "/torrents" && request.action() == "add" -> {
						TorrServerHttpResponse(
							code = 200,
							body = """{"hash":"hash123","file_stats":[{"id":7,"path":"movie.mkv","length":9000}]}""",
						)
					}

					request.url.encodedPath.startsWith("/stream/") &&
						request.url.queryParameterNames.contains("preload") -> {
						TorrServerHttpResponse(code = 200, body = "")
					}

					request.url.encodedPath == "/torrents" && request.action() == "get" -> {
						TorrServerHttpResponse(code = 200, body = """{"hash":"hash123"}""")
					}

					else -> TorrServerHttpResponse(code = 500, body = "")
				}
			}
		}
		val client = TorrServerClient(
			transport = transport,
			pollIntervalMs = 200,
		)
		val before = testScheduler.currentTime

		client.prepareStreamUrl(
			magnet = "magnet:?xt=urn:btih:abcdef&dn=Video",
			timeoutMs = 1_000,
		)

		assertEquals(before, testScheduler.currentTime)
		assertFalse(requests.any { it.url.encodedPath == "/torrents" && it.action() == "get" })
	}

	private fun Request.action(): String? {
		if (method != "POST" || body == null) return null
		val buffer = okio.Buffer()
		body?.writeTo(buffer)
		return runCatching { JSONObject(buffer.readUtf8()).optString("action") }.getOrNull()
	}
}
