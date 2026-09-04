package sk.ziacik.androidstreamplayer.subtitle

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.util.Locale
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.w3c.dom.Element
import sk.ziacik.androidstreamplayer.catalog.Movie
import sk.ziacik.androidstreamplayer.search.TorrentSearchResult

class PodnapisiSubtitleProvider internal constructor(
	private val cacheDir: File,
	private val transport: SubtitleHttpTransport,
) {
	suspend fun find(movie: Movie, result: TorrentSearchResult): SubtitleTrack? {
		val cacheKey = cacheKey(movie, result)
		findCached(cacheKey)?.let { return it }

		val title = movie.originalTitle.ifBlank { movie.title }
		var candidates = search(title, movie.releaseYear)
		if (candidates.isEmpty() && movie.title.isNotBlank() && movie.title != title) {
			candidates = search(movie.title, movie.releaseYear)
		}
		val best = candidates.maxWithOrNull(
			compareBy<SubtitleCandidate> { languageScore(it.language) }
				.thenBy { releaseSimilarity(it.release, result.title) }
				.thenBy { it.downloads },
		) ?: return null

		val response = transport.execute(
			Request.Builder()
				.url(
					BASE_URL.toHttpUrl()
						.newBuilder()
						.addPathSegment("subtitles")
						.addPathSegment(best.pid)
						.addPathSegment("download")
						.build(),
				)
				.build(),
		)
		if (!response.isSuccessful) return null

		val extracted = extractSubtitle(response.body) ?: return null
		cacheDir.mkdirs()
		val output = File(cacheDir, "$cacheKey-${best.language}.${extracted.extension}")
		val temp = File(cacheDir, ".${output.name}.tmp")
		temp.writeBytes(extracted.bytes)
		if (!temp.renameTo(output)) {
			temp.copyTo(output, overwrite = true)
			temp.delete()
		}

		return output.toTrack(best.language)
	}

	private suspend fun search(title: String, year: Int?): List<SubtitleCandidate> {
		val url = BASE_URL.toHttpUrl()
			.newBuilder()
			.addPathSegments("subtitles/search/old")
			.addQueryParameter("sXML", "1")
			.addQueryParameter("sK", title)
			.addQueryParameter("sL", LANGUAGE_IDS)
			.apply {
				year?.let { addQueryParameter("sY", it.toString()) }
			}
			.build()
		val response = transport.execute(Request.Builder().url(url).build())
		if (!response.isSuccessful) return emptyList()
		return parseCandidates(response.body)
	}

	private fun parseCandidates(body: ByteArray): List<SubtitleCandidate> {
		val xml = body.toString(Charsets.UTF_8)
		if (xml.contains("<!DOCTYPE", ignoreCase = true) || xml.contains("<!ENTITY", ignoreCase = true)) {
			return emptyList()
		}

		val factory = DocumentBuilderFactory.newInstance().apply {
			isXIncludeAware = false
			isExpandEntityReferences = false
			setFeatureIfSupported("http://apache.org/xml/features/disallow-doctype-decl", true)
			setFeatureIfSupported("http://xml.org/sax/features/external-general-entities", false)
			setFeatureIfSupported("http://xml.org/sax/features/external-parameter-entities", false)
			setFeatureIfSupported("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
		}
		val document = factory.newDocumentBuilder().parse(ByteArrayInputStream(body))
		val nodes = document.getElementsByTagName("subtitle")
		return buildList {
			for (index in 0 until nodes.length) {
				val element = nodes.item(index) as? Element ?: continue
				val pid = element.childText("pid")?.trim().orEmpty()
				val language = normalizeLanguage(element.childText("language")) ?: continue
				if (pid.isEmpty()) continue
				add(
					SubtitleCandidate(
						pid = pid,
						release = element.childText("release").orEmpty(),
						language = language,
						downloads = element.childText("downloads")?.trim()?.toIntOrNull() ?: 0,
					),
				)
			}
		}
	}

	private fun findCached(cacheKey: String): SubtitleTrack? {
		if (!cacheDir.isDirectory) return null
		return cacheDir.listFiles()
			?.asSequence()
			?.filter { file ->
				file.isFile &&
					file.name.startsWith("$cacheKey-") &&
					file.extension.lowercase(Locale.ROOT) in SUPPORTED_EXTENSIONS
			}
			?.mapNotNull { file ->
				val language = file.name
					.removePrefix("$cacheKey-")
					.substringBefore('.')
					.takeIf { it in LANGUAGE_PRIORITY }
					?: return@mapNotNull null
				file.toTrack(language)
			}
			?.maxByOrNull { track -> LANGUAGE_PRIORITY.getValue(track.language) }
	}

	private fun File.toTrack(language: String) = SubtitleTrack(
		path = absolutePath,
		language = language,
		mimeType = mimeType(extension.lowercase(Locale.ROOT)),
		label = languageLabel(language),
	)

	private fun extractSubtitle(body: ByteArray): ExtractedSubtitle? {
		if (!body.isZip()) {
			return body.takeIf { it.size <= MAX_SUBTITLE_BYTES }
				?.let { ExtractedSubtitle(it, "srt") }
		}

		ZipInputStream(ByteArrayInputStream(body)).use { zip ->
			while (true) {
				val entry = zip.nextEntry ?: break
				val extension = entry.name.substringAfterLast('.', "").lowercase(Locale.ROOT)
				if (!entry.isDirectory && extension in SUPPORTED_EXTENSIONS) {
					val output = ByteArrayOutputStream()
					val buffer = ByteArray(8 * 1024)
					var total = 0
					while (true) {
						val read = zip.read(buffer)
						if (read < 0) break
						total += read
						if (total > MAX_SUBTITLE_BYTES) return null
						output.write(buffer, 0, read)
					}
					return ExtractedSubtitle(output.toByteArray(), extension)
				}
				zip.closeEntry()
			}
		}
		return null
	}

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

	private fun cacheKey(movie: Movie, result: TorrentSearchResult): String {
		val source = result.id.ifBlank { result.title }
		val digest = MessageDigest.getInstance("SHA-256").digest(source.encodeToByteArray())
		val shortHash = digest.take(8).joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
		return "${movie.tmdbId}-$shortHash"
	}

	private fun languageScore(language: String) = LANGUAGE_PRIORITY[language] ?: 0

	private fun normalizeLanguage(value: String?): String? = when (value?.trim()?.lowercase(Locale.ROOT)) {
		"37", "sk", "sl" -> "sk"
		"7", "cs", "cz" -> "cs"
		"2", "en" -> "en"
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

	private fun ByteArray.isZip() = size >= 4 &&
		this[0] == 0x50.toByte() &&
		this[1] == 0x4b.toByte() &&
		this[2] == 0x03.toByte() &&
		this[3] == 0x04.toByte()

	private fun DocumentBuilderFactory.setFeatureIfSupported(name: String, value: Boolean) {
		runCatching { setFeature(name, value) }
	}

	private fun Element.childText(name: String): String? =
		getElementsByTagName(name).item(0)?.textContent

	private data class SubtitleCandidate(
		val pid: String,
		val release: String,
		val language: String,
		val downloads: Int,
	)

	private data class ExtractedSubtitle(
		val bytes: ByteArray,
		val extension: String,
	)

	private companion object {
		const val BASE_URL = "https://www.podnapisi.net"
		const val LANGUAGE_IDS = "37,7,2"
		const val MAX_SUBTITLE_BYTES = 5 * 1024 * 1024
		val LANGUAGE_PRIORITY = mapOf("sk" to 3, "cs" to 2, "en" to 1)
		val SUPPORTED_EXTENSIONS = setOf("srt", "vtt", "ass", "ssa")
		val TOKEN_REGEX = Regex("[a-z0-9]+")
	}
}

internal data class SubtitleHttpResponse(
	val code: Int,
	val body: ByteArray,
) {
	val isSuccessful: Boolean
		get() = code in 200..299
}

internal fun interface SubtitleHttpTransport {
	suspend fun execute(request: Request): SubtitleHttpResponse
}
