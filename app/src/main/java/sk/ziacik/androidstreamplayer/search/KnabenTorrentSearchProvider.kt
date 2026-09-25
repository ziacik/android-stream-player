package sk.ziacik.androidstreamplayer.search

import java.io.IOException
import sk.ziacik.androidstreamplayer.catalog.MediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

internal data class TorrentSearchHttpResponse(
	val code: Int,
	val body: String,
) {
	val isSuccessful: Boolean
		get() = code in 200..299
}

internal fun interface TorrentSearchHttpTransport {
	suspend fun execute(request: Request): TorrentSearchHttpResponse
}

internal class OkHttpTorrentSearchTransport(
	private val client: OkHttpClient = OkHttpClient(),
) : TorrentSearchHttpTransport {
	override suspend fun execute(request: Request): TorrentSearchHttpResponse =
		withContext(Dispatchers.IO) {
			client.newCall(request).execute().use { response ->
				TorrentSearchHttpResponse(
					code = response.code,
					body = response.body.string(),
				)
			}
		}
}

internal class KnabenTorrentSearchProvider(
	private val transport: TorrentSearchHttpTransport = OkHttpTorrentSearchTransport(),
) : TorrentSearchProvider {
	override suspend fun search(movie: MovieTorrentSearchRequest): List<TorrentSearchResult> {
		val merged = linkedMapOf<String, TorrentSearchResult>()

		fallbackQueries(movie).forEach { query ->
			searchQuery(query, movie.mediaType)
				.filter { result -> result.matchesEpisodeRequest(movie) }
				.forEach { result ->
				val key = result.deduplicationKey()
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

	private suspend fun searchQuery(query: String, mediaType: MediaType): List<TorrentSearchResult> {
		val normalizedQuery = query.trim()
		if (normalizedQuery.isEmpty()) return emptyList()

		val requestBody = JSONObject()
			.put("query", normalizedQuery)
			.put("order_by", "seeders")
			.put("order_direction", "desc")
			.put(
				"categories",
				JSONArray().put(if (mediaType == MediaType.MOVIE) MOVIES_CATEGORY else TV_CATEGORY),
			)
			.put("size", RESULT_LIMIT)
			.put("hide_unsafe", true)
			.put("hide_xxx", true)

		val response = transport.execute(
			Request.Builder()
				.url(API_URL)
				.post(requestBody.toString().toRequestBody(JSON_MEDIA_TYPE))
				.build(),
		)
		if (!response.isSuccessful) {
			throw IOException("Knaben search failed with HTTP ${response.code}")
		}

		val hits = try {
			JSONObject(response.body).getJSONArray("hits")
		} catch (error: Exception) {
			throw IOException("Invalid Knaben search response", error)
		}

		return buildList {
			for (index in 0 until hits.length()) {
				val hit = hits.optJSONObject(index) ?: continue
				val title = hit.optString("title", "").trim()
				val magnetUri = hit.optString("magnetUrl", "").trim()
				if (title.isEmpty() || !magnetUri.startsWith("magnet:?", ignoreCase = true)) {
					continue
				}

				val id = hit.optString("id", "").takeIf { it.isNotBlank() }
					?: hit.optString("hash", "").takeIf { it.isNotBlank() }
					?: magnetUri

				add(
					TorrentSearchResult(
						id = id,
						title = title,
						magnetUri = magnetUri,
						sizeBytes = hit.optLong("bytes", -1L).takeIf { it >= 0L },
						seeders = hit.optInt("seeders", -1).takeIf { it >= 0 },
						source = hit.optString("cachedOrigin", "").takeIf { it.isNotBlank() },
					),
				)
			}
		}
	}

	private fun TorrentSearchResult.deduplicationKey(): String {
		val infoHash = infoHash(magnetUri)
		return if (infoHash != null) {
			"hash:$infoHash"
		} else {
			"id:${id.lowercase()}"
		}
	}

	private fun TorrentSearchResult.seederCount(): Int = seeders ?: -1

	private companion object {
		const val API_URL = "https://api.knaben.org/v1"
		const val MOVIES_CATEGORY = 3_000_000
		const val TV_CATEGORY = 5_000_000
		const val RESULT_LIMIT = 50
		val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
	}
}

internal fun fallbackQueries(movie: MovieTorrentSearchRequest): List<String> = buildList {
	fun addUnique(value: String) {
		val normalized = value.trim().replace(Regex("\\s+"), " ")
		if (normalized.isNotBlank() && none { it.equals(normalized, ignoreCase = true) }) {
			add(normalized)
		}
	}

	if (movie.mediaType == MediaType.EPISODE) {
		val code = movie.episodeCode
		val seriesTitles = listOfNotNull(movie.originalSeriesTitle, movie.seriesTitle)
		if (code != null) {
			seriesTitles.forEach { addUnique("$it $code") }
			val alternate = movie.seasonNumber?.let { season ->
				movie.episodeNumber?.let { episode -> "${season}x%02d".format(episode) }
			}
			if (alternate != null) seriesTitles.forEach { addUnique("$it $alternate") }
		}
		movie.seasonNumber?.let { season ->
			val seasonCode = "S%02d".format(season)
			seriesTitles.forEach { title ->
				addUnique("$title $seasonCode")
				addUnique("$title Season $season")
				addUnique("$title Season %02d".format(season))
				addUnique("$title $season. séria")
				addUnique("$title $season. seria")
				addUnique("$title Séria $season")
				addUnique("$title Seria $season")
				addUnique("$title $season. sezóna")
				addUnique("$title $season. sezona")
				addUnique("$title Sezóna $season")
				addUnique("$title Sezona $season")
				addUnique("$title $season. série")
				addUnique("$title Série $season")
				addUnique(title)
			}
		}
	} else {
		movie.year?.let { addUnique("${movie.originalTitle} $it") }
		movie.year?.let { addUnique("${movie.title} $it") }
		addUnique(movie.originalTitle)
		addUnique(movie.title)
	}
}

private fun infoHash(magnet: String): String? =
	Regex("(?i)[?&]xt=urn:btih:([A-Za-z0-9]+)")
		.find(magnet)
		?.groupValues
		?.getOrNull(1)
		?.lowercase()


internal fun TorrentSearchResult.matchesEpisodeRequest(movie: MovieTorrentSearchRequest): Boolean {
	if (movie.mediaType != MediaType.EPISODE) return true
	val season = movie.seasonNumber ?: return true
	val episode = movie.episodeNumber ?: return true
	val value = title.lowercase()

	val exactEpisodePatterns = listOf(
		Regex("""(?i)(?<![a-z0-9])s0?$season[ ._-]*e0?$episode(?!\d)"""),
		Regex("""(?i)(?<!\d)0?$season[ ._-]*x[ ._-]*0?$episode(?!\d)"""),
		Regex("""(?i)season[ ._-]*0?$season[ ._-]*(?:episode|ep)[ ._-]*0?$episode(?!\d)"""),
	)
	if (exactEpisodePatterns.any { it.containsMatchIn(value) }) return true

	val anyEpisodeFromSeasonPatterns = listOf(
		Regex("""(?i)(?<![a-z0-9])s0?$season[ ._-]*e\d+"""),
		Regex("""(?i)(?<!\d)0?$season[ ._-]*x[ ._-]*\d+"""),
		Regex("""(?i)season[ ._-]*0?$season[ ._-]*(?:episode|ep)[ ._-]*\d+"""),
	)
	if (anyEpisodeFromSeasonPatterns.any { it.containsMatchIn(value) }) return false

	val seasonPackPatterns = listOf(
		Regex("""(?i)(?<![a-z0-9])s0?$season(?![ ._-]*e\d)"""),
		Regex("""(?i)season[ ._-]*0?$season(?![ ._-]*(?:episode|ep)\d)"""),
		Regex("""(?i)(?:séria|seria|série|sezona|sezóna)[ ._-]*0?$season"""),
		Regex("""(?i)0?$season[ ._-]*(?:séria|seria|série|sezona|sezóna)"""),
	)
	if (seasonPackPatterns.any { it.containsMatchIn(value) }) return true

	val completePackPatterns = listOf(
		Regex("""(?i)\bcomplete\b"""),
		Regex("""(?i)\bcomplete[ ._-]*(?:series|collection)\b"""),
		Regex("""(?i)\ball[ ._-]*seasons\b"""),
		Regex("""(?i)\bkomplet\b"""),
	)
	return completePackPatterns.any { it.containsMatchIn(value) }
}
