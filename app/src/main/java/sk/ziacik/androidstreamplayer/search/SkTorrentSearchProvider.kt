package sk.ziacik.androidstreamplayer.search

import java.io.IOException
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.jsoup.Jsoup

internal class SkTorrentSearchProvider(
	private val transport: TorrentSearchHttpTransport = OkHttpTorrentSearchTransport(),
) : TorrentSearchProvider {
	override suspend fun search(movie: MovieTorrentSearchRequest): List<TorrentSearchResult> {
		val merged = linkedMapOf<String, TorrentSearchResult>()

		fallbackQueries(movie).forEach { query ->
			searchQuery(query).forEach { result ->
				val key = result.infoHashKey()
				val existing = merged[key]
				if (existing == null || result.seederCount() > existing.seederCount()) {
					merged[key] = result
				}
			}
		}

		return merged.values.sortedWith(
			compareByDescending<TorrentSearchResult> { it.seederCount() }
				.thenBy { it.title.lowercase() },
		)
	}

	private suspend fun searchQuery(query: String): List<TorrentSearchResult> {
		val normalizedQuery = query.trim()
		if (normalizedQuery.isEmpty()) return emptyList()

		val url = SEARCH_URL.toHttpUrl().newBuilder()
			.addQueryParameter("search", normalizedQuery)
			.addQueryParameter("category", MOVIE_CATEGORIES.joinToString(";"))
			.addQueryParameter("active", "0")
			.build()

		val response = transport.execute(
			Request.Builder()
				.url(url)
				.get()
				.header("User-Agent", USER_AGENT)
				.build(),
		)
		if (!response.isSuccessful) {
			throw IOException("SkTorrent search failed with HTTP ${response.code}")
		}

		val document = try {
			Jsoup.parse(response.body, BASE_URL)
		} catch (error: Exception) {
			throw IOException("Invalid SkTorrent search response", error)
		}

		return document
			.select("""tr:has(a[href*="download.php?id="])""")
			.mapNotNull { row ->
				val download = row.selectFirst("""a[href*="download.php?id="]""") ?: return@mapNotNull null
				val infoHash = INFO_HASH_REGEX.find(download.attr("href"))
					?.groupValues
					?.getOrNull(1)
					?.lowercase()
					?: return@mapNotNull null

				val details = row.selectFirst("""a[href*="details.php?id="]""")
				val rawTitle = details
					?.attr("title")
					.orEmpty()
					.ifBlank { details?.text().orEmpty() }
				val title = cleanTitle(rawTitle)
				if (title.isBlank()) return@mapNotNull null

				val cells = row.children().filter { it.tagName().equals("td", ignoreCase = true) }
				val infoCell = cells.getOrNull(2)?.text().orEmpty()
				val seeders = cells.getOrNull(4)?.text()?.trim()?.toIntOrNull()

				TorrentSearchResult(
					id = infoHash,
					title = title,
					magnetUri = buildMagnet(infoHash, title),
					sizeBytes = parseSizeBytes(infoCell),
					seeders = seeders,
					source = SOURCE,
				)
			}
	}

	private fun cleanTitle(value: String): String = value
		.trim()
		.replace(Regex("""^VA\s*\|""", RegexOption.IGNORE_CASE), "VA -")
		.replace(Regex("""^.*?\s/\s*|^.*?\s\|\s*"""), "")
		.replace(
			Regex(
				"""\|\s*\d+\%\s*CSFD\.cz/?|\s*=*\s*CSFD\s*\d+\%.*$""",
				RegexOption.IGNORE_CASE,
			),
			"",
		)
		.trim()

	private fun parseSizeBytes(text: String): Long? {
		val match = SIZE_REGEX.find(text) ?: return null
		val amount = match.groupValues[1].replace(',', '.').toDoubleOrNull() ?: return null
		val unit = match.groupValues[2].uppercase()
		val multiplier = when (unit) {
			"B" -> 1.0
			"KB", "KIB" -> 1024.0
			"MB", "MIB" -> 1024.0 * 1024.0
			"GB", "GIB" -> 1024.0 * 1024.0 * 1024.0
			"TB", "TIB" -> 1024.0 * 1024.0 * 1024.0 * 1024.0
			else -> return null
		}
		return (amount * multiplier).toLong()
	}

	private fun buildMagnet(infoHash: String, title: String): String = buildList {
		add("xt=urn:btih:$infoHash")
		add("dn=${encodeMagnetValue(title)}")
		TRACKERS.forEach { tracker ->
			add("tr=${encodeMagnetValue(tracker)}")
		}
	}.joinToString(prefix = "magnet:?", separator = "&")

	private fun encodeMagnetValue(value: String): String =
		URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20")

	private fun TorrentSearchResult.infoHashKey(): String = "hash:${id.lowercase()}"
	private fun TorrentSearchResult.seederCount(): Int = seeders ?: -1

	private companion object {
		const val BASE_URL = "https://sktorrent.eu/"
		const val SEARCH_URL = "https://sktorrent.eu/torrent/torrents.php"
		const val SOURCE = "SkTorrent"
		const val USER_AGENT = "Kino Android TV"
		val MOVIE_CATEGORIES = listOf(1, 5, 14, 15, 20, 31, 3, 19, 28, 29, 43)
		val INFO_HASH_REGEX = Regex("""[?&]id=([a-fA-F0-9]{40})""")
		val SIZE_REGEX = Regex(
			"""(\d+(?:[.,]\d+)?)\s*(B|KB|KiB|MB|MiB|GB|GiB|TB|TiB)""",
			RegexOption.IGNORE_CASE,
		)
		val TRACKERS = listOf(
			"https://announce.sktorrent.eu/announce.php",
			"udp://ipv4announce.sktorrent.eu:6969/announce",
			"udp://tracker.opentrackr.org:1337/announce",
		)
	}
}
