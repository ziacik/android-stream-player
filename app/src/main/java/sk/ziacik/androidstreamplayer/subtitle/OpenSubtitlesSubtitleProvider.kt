package sk.ziacik.androidstreamplayer.subtitle

import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import kotlinx.coroutines.CancellationException
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import sk.ziacik.androidstreamplayer.catalog.Movie
import sk.ziacik.androidstreamplayer.search.TorrentSearchResult
import sk.ziacik.androidstreamplayer.torrent.TorrentSource

class OpenSubtitlesSubtitleProvider internal constructor(
	private val apiKey: String,
	private val cacheDir: File,
	private val userAgent: String = "Kino/0.1.0",
	private val transport: SubtitleHttpTransport = OkHttpSubtitleTransport(),
) {
	suspend fun search(
		movie: Movie,
		result: TorrentSearchResult,
		source: TorrentSource,
	): List<SubtitleOption> {
		requireApiKey()

		val movieHash = try {
			computeMovieHash(source.uri)
		} catch (error: CancellationException) {
			throw error
		} catch (_: Throwable) {
			null
		}

		val url = "$API_BASE_URL/subtitles".toHttpUrl()
			.newBuilder()
			.addQueryParameter("languages", LANGUAGES)
			.addQueryParameter("tmdb_id", movie.tmdbId.toString())
			.apply {
				movieHash?.let { addQueryParameter("moviehash", it) }
			}
			.build()

		val response = transport.execute(
			apiRequestBuilder(url.toString())
				.get()
				.build(),
		)
		if (!response.isSuccessful) {
			throw IOException("OpenSubtitles search failed with HTTP ${response.code}")
		}

		return parseOptions(response.body)
			.sortedWith(
				compareByDescending<SubtitleOption> { it.exactMatch }
					.thenByDescending { languageScore(it.language) }
					.thenByDescending { releaseSimilarity(it.release, result.title) }
					.thenByDescending { it.downloads },
			)
	}

	suspend fun download(
		movie: Movie,
		result: TorrentSearchResult,
		option: SubtitleOption,
	): SubtitleTrack? {
		requireApiKey()
		val cacheKey = optionCacheKey(movie, result, option)
		findCached(cacheKey, option.language)?.let { return it }

		val fileId = option.id.toLongOrNull() ?: return null
		val requestBody = JSONObject()
			.put("file_id", fileId)
			.toString()
			.toRequestBody(JSON_MEDIA_TYPE)
		val downloadResponse = transport.execute(
			apiRequestBuilder("$API_BASE_URL/download")
				.post(requestBody)
				.build(),
		)
		if (!downloadResponse.isSuccessful) {
			throw IOException("OpenSubtitles download request failed with HTTP ${downloadResponse.code}")
		}

		val downloadJson = try {
			JSONObject(downloadResponse.body.toString(Charsets.UTF_8))
		} catch (error: Exception) {
			throw IOException("Invalid OpenSubtitles download response", error)
		}
		val link = downloadJson.optString("link").trim()
		if (link.isEmpty()) return null
		val fileName = downloadJson.optString("file_name").trim()
		val extension = fileName.substringAfterLast('.', "srt")
			.lowercase(Locale.ROOT)
			.takeIf { it in SUPPORTED_EXTENSIONS }
			?: "srt"

		val subtitleResponse = transport.execute(
			Request.Builder()
				.url(link)
				.header("User-Agent", userAgent)
				.get()
				.build(),
		)
		if (!subtitleResponse.isSuccessful) {
			throw IOException("OpenSubtitles file download failed with HTTP ${subtitleResponse.code}")
		}
		if (subtitleResponse.body.isEmpty() || subtitleResponse.body.size > MAX_SUBTITLE_BYTES) return null

		cacheDir.mkdirs()
		val output = File(cacheDir, "$cacheKey.$extension")
		val temporary = File(cacheDir, ".${output.name}.tmp")
		temporary.writeBytes(subtitleResponse.body)
		if (!temporary.renameTo(output)) {
			temporary.copyTo(output, overwrite = true)
			temporary.delete()
		}

		return output.toTrack(option.language)
	}

	private suspend fun computeMovieHash(streamUri: String): String {
		val first = rangeRequest(streamUri, "bytes=0-${HASH_CHUNK_SIZE - 1}")
		if (first.code != HTTP_PARTIAL_CONTENT || first.body.size != HASH_CHUNK_SIZE) {
			throw IOException("Stream does not support OpenSubtitles hash ranges")
		}
		val fileSize = parseFileSize(first.header("Content-Range"))
		if (fileSize < HASH_CHUNK_SIZE * 2L) {
			throw IOException("Stream is too small for OpenSubtitles hash")
		}

		val lastStart = fileSize - HASH_CHUNK_SIZE
		val last = rangeRequest(streamUri, "bytes=$lastStart-${fileSize - 1}")
		if (last.code != HTTP_PARTIAL_CONTENT || last.body.size != HASH_CHUNK_SIZE) {
			throw IOException("Could not read final OpenSubtitles hash range")
		}

		var hash = fileSize
		hash += chunkSum(first.body)
		hash += chunkSum(last.body)
		return java.lang.Long.toUnsignedString(hash, 16).padStart(16, '0')
	}

	private suspend fun rangeRequest(uri: String, range: String): SubtitleHttpResponse =
		transport.execute(
			Request.Builder()
				.url(uri)
				.header("Range", range)
				.get()
				.build(),
		)

	private fun parseFileSize(contentRange: String?): Long {
		val value = contentRange ?: throw IOException("Missing Content-Range")
		return CONTENT_RANGE_REGEX.matchEntire(value.trim())
			?.groupValues
			?.get(1)
			?.toLongOrNull()
			?: throw IOException("Invalid Content-Range")
	}

	private fun chunkSum(bytes: ByteArray): Long {
		val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
		var sum = 0L
		while (buffer.remaining() >= java.lang.Long.BYTES) {
			sum += buffer.long
		}
		return sum
	}

	private fun parseOptions(body: ByteArray): List<SubtitleOption> {
		val root = try {
			JSONObject(body.toString(Charsets.UTF_8))
		} catch (error: Exception) {
			throw IOException("Invalid OpenSubtitles search response", error)
		}
		val data = root.optJSONArray("data") ?: return emptyList()

		return buildList {
			for (index in 0 until data.length()) {
				val attributes = data.optJSONObject(index)
					?.optJSONObject("attributes")
					?: continue
				val language = normalizeLanguage(attributes.optString("language")) ?: continue
				val release = attributes.optString("release").trim()
				val downloads = attributes.optInt("download_count", 0)
				val exactMatch = attributes.optBoolean("moviehash_match", false)
				val files = attributes.optJSONArray("files") ?: continue
				for (fileIndex in 0 until files.length()) {
					val file = files.optJSONObject(fileIndex) ?: continue
					val fileId = file.optLong("file_id", -1L)
					if (fileId <= 0L) continue
					val fileName = file.optString("file_name").trim()
					add(
						SubtitleOption(
							id = fileId.toString(),
							language = language,
							label = languageLabel(language),
							release = release.ifBlank { fileName },
							downloads = downloads,
							exactMatch = exactMatch,
						),
					)
				}
			}
		}
	}

	private fun apiRequestBuilder(url: String): Request.Builder = Request.Builder()
		.url(url)
		.header("Api-Key", apiKey)
		.header("User-Agent", userAgent)
		.header("Accept", "application/json")

	private fun requireApiKey() {
		if (apiKey.isBlank()) {
			throw IOException("OpenSubtitles API key is not configured")
		}
	}

	private fun findCached(cacheKey: String, language: String): SubtitleTrack? {
		if (!cacheDir.isDirectory) return null
		return cacheDir.listFiles()
			?.firstOrNull { file ->
				file.isFile &&
					file.nameWithoutExtension == cacheKey &&
					file.extension.lowercase(Locale.ROOT) in SUPPORTED_EXTENSIONS
			}
			?.toTrack(language)
	}

	private fun optionCacheKey(
		@Suppress("UNUSED_PARAMETER") movie: Movie,
		@Suppress("UNUSED_PARAMETER") result: TorrentSearchResult,
		option: SubtitleOption,
	): String = "opensubtitles-${option.id}-${option.language}"

	private fun File.toTrack(language: String) = SubtitleTrack(
		path = absolutePath,
		language = language,
		mimeType = mimeType(extension.lowercase(Locale.ROOT)),
		label = languageLabel(language),
	)

	private fun releaseSimilarity(candidate: String, torrent: String): Int {
		val candidateTokens = tokenize(candidate)
		val torrentTokens = tokenize(torrent)
		if (candidateTokens.isEmpty() || torrentTokens.isEmpty()) return 0
		if (candidateTokens == torrentTokens) return 10_000
		val common = candidateTokens.intersect(torrentTokens).size
		val union = candidateTokens.union(torrentTokens).size
		return common * 1_000 / union
	}

	private fun tokenize(value: String): Set<String> = TOKEN_REGEX
		.findAll(value.lowercase(Locale.ROOT))
		.map { it.value }
		.toSet()

	private fun languageScore(language: String) = LANGUAGE_PRIORITY[language] ?: 0

	private fun normalizeLanguage(value: String?): String? = when (value?.trim()?.lowercase(Locale.ROOT)) {
		"sk", "slo", "slk" -> "sk"
		"cs", "cz", "cze", "ces" -> "cs"
		"en", "eng" -> "en"
		else -> null
	}

	private fun languageLabel(language: String) = when (language) {
		"sk" -> "Slovak"
		"cs" -> "Czech"
		else -> "English"
	}

	private fun mimeType(extension: String) = when (extension) {
		"vtt" -> "text/vtt"
		"ass", "ssa" -> "text/x-ssa"
		else -> "application/x-subrip"
	}

	private companion object {
		const val API_BASE_URL = "https://api.opensubtitles.com/api/v1"
		const val LANGUAGES = "sk,cs,en"
		const val HASH_CHUNK_SIZE = 64 * 1024
		const val HTTP_PARTIAL_CONTENT = 206
		const val MAX_SUBTITLE_BYTES = 5 * 1024 * 1024
		val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
		val LANGUAGE_PRIORITY = mapOf("sk" to 3, "cs" to 2, "en" to 1)
		val SUPPORTED_EXTENSIONS = setOf("srt", "vtt", "ass", "ssa")
		val TOKEN_REGEX = Regex("[a-z0-9]+")
		val CONTENT_RANGE_REGEX = Regex("(?i)bytes\\s+\\d+-\\d+/(\\d+)")
	}
}
