package sk.ziacik.androidstreamplayer.search

import java.io.IOException
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
	override suspend fun search(query: String): List<TorrentSearchResult> =
		searchOnce(
			query = query,
			categories = listOf(MOVIES_CATEGORY, TV_CATEGORY),
		)

	suspend fun search(movie: MovieTorrentSearchRequest): List<TorrentSearchResult> {
		val merged = linkedMapOf<String, TorrentSearchResult>()

		movieFallbackQueries(movie).forEach { query ->
			searchOnce(
				query = query,
				categories = listOf(MOVIES_CATEGORY),
			).forEach { result ->
				val key = result.deduplicationKey()
				val existing = merged[key]
				if (existing == null || result.seederCount() > existing.seederCount()) {
					merged[key] = result
				}
			}
		}

		return merged.values.sortedWith(
			compareByDescending<TorrentSearchResult> { it.seederCount() }
				.thenByDescending { qualityRank(it.quality) }
				.thenByDescending { it.sizeBytes ?: -1L },
		)
	}

	private suspend fun searchOnce(
		query: String,
		categories: List<Int>,
	): List<TorrentSearchResult> {
		val normalizedQuery = query.trim()
		if (normalizedQuery.isEmpty()) return emptyList()

		val requestBody = JSONObject()
			.put("query", normalizedQuery)
			.put("order_by", "seeders")
			.put("order_direction", "desc")
			.put(
				"categories",
				JSONArray().apply {
					categories.forEach(::put)
				},
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
						quality = inferQuality(title),
						sizeBytes = hit.optLong("bytes", -1L).takeIf { it >= 0L },
						seeders = hit.optInt("seeders", -1).takeIf { it >= 0 },
						source = hit.optString("cachedOrigin", "").takeIf { it.isNotBlank() },
					),
				)
			}
		}
	}

	private fun movieFallbackQueries(movie: MovieTorrentSearchRequest): List<String> = buildList {
		fun addUnique(value: String) {
			val normalized = value.trim().replace(WHITESPACE, " ")
			if (normalized.isNotEmpty() && none { it.equals(normalized, ignoreCase = true) }) {
				add(normalized)
			}
		}

		val yearSuffix = movie.year?.let { " $it" }.orEmpty()
		addUnique(movie.originalTitle + yearSuffix)
		addUnique(movie.title + yearSuffix)
		addUnique(movie.originalTitle)
		addUnique(movie.title)
	}

	private fun TorrentSearchResult.deduplicationKey(): String {
		val infoHash = INFO_HASH.find(magnetUri)?.groupValues?.getOrNull(1)
			?.lowercase()
		if (!infoHash.isNullOrBlank()) {
			return "hash:$infoHash"
		}

		val normalizedTitle = title
			.lowercase()
			.replace(NON_ALPHANUMERIC, " ")
			.trim()
			.replace(WHITESPACE, " ")
		return "fallback:$normalizedTitle:${sizeBytes ?: -1L}"
	}

	private fun TorrentSearchResult.seederCount(): Int = seeders ?: -1

	private fun qualityRank(quality: String?): Int = when (quality?.lowercase()) {
		"2160p" -> 4
		"1080p" -> 3
		"720p" -> 2
		"480p" -> 1
		else -> 0
	}

	private fun inferQuality(title: String): String? = when {
		title.contains("2160p", ignoreCase = true) ||
			title.contains("4k", ignoreCase = true) -> "2160p"
		title.contains("1080p", ignoreCase = true) -> "1080p"
		title.contains("720p", ignoreCase = true) -> "720p"
		title.contains("480p", ignoreCase = true) -> "480p"
		else -> null
	}

	private companion object {
		const val API_URL = "https://api.knaben.org/v1"
		const val MOVIES_CATEGORY = 3_000_000
		const val TV_CATEGORY = 2_000_000
		const val RESULT_LIMIT = 50
		val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
		val WHITESPACE = Regex("\\s+")
		val NON_ALPHANUMERIC = Regex("[^a-z0-9]+")
		val INFO_HASH = Regex("(?:[?&])xt=urn:btih:([A-Za-z0-9]+)", RegexOption.IGNORE_CASE)
	}
}
