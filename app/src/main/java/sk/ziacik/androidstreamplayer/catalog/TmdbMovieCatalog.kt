package sk.ziacik.androidstreamplayer.catalog

import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

internal data class TmdbHttpResponse(
    val code: Int,
    val body: String,
) {
    val isSuccessful: Boolean
        get() = code in 200..299
}

internal fun interface TmdbHttpTransport {
    suspend fun execute(request: Request): TmdbHttpResponse
}

internal class OkHttpTmdbTransport(
    private val client: OkHttpClient = OkHttpClient(),
) : TmdbHttpTransport {
    override suspend fun execute(request: Request): TmdbHttpResponse =
        withContext(Dispatchers.IO) {
            client.newCall(request).execute().use { response ->
                TmdbHttpResponse(
                    code = response.code,
                    body = response.body.string(),
                )
            }
        }
}

class TmdbMovieCatalog internal constructor(
    private val apiKey: String,
    private val transport: TmdbHttpTransport = OkHttpTmdbTransport(),
) : MovieCatalog {
    override suspend fun search(query: String): List<Movie> {
        val normalizedQuery = query.trim()
        if (normalizedQuery.isEmpty()) return emptyList()

        val url = "$API_BASE_URL/search/movie".toHttpUrl()
            .newBuilder()
            .addQueryParameter("api_key", apiKey)
            .addQueryParameter("query", normalizedQuery)
            .addQueryParameter("include_adult", "false")
            .addQueryParameter("language", LANGUAGE)
            .build()

        val response = transport.execute(Request.Builder().url(url).get().build())
        if (!response.isSuccessful) {
            throw IOException("TMDB movie search failed with HTTP ${response.code}")
        }

        return parseMovies(response.body, "Invalid TMDB movie search response")
    }

    suspend fun trending(): List<Movie> {
        val url = "$API_BASE_URL/trending/movie/week".toHttpUrl()
            .newBuilder()
            .addQueryParameter("api_key", apiKey)
            .addQueryParameter("language", LANGUAGE)
            .build()

        val response = transport.execute(Request.Builder().url(url).get().build())
        if (!response.isSuccessful) {
            throw IOException("TMDB trending movies failed with HTTP ${response.code}")
        }

        return parseMovies(response.body, "Invalid TMDB trending movies response")
    }

    override suspend fun externalIds(tmdbId: Int): MovieExternalIds {
        val url = "$API_BASE_URL/movie/$tmdbId/external_ids".toHttpUrl()
            .newBuilder()
            .addQueryParameter("api_key", apiKey)
            .build()

        val response = transport.execute(Request.Builder().url(url).get().build())
        if (!response.isSuccessful) {
            throw IOException("TMDB external IDs failed with HTTP ${response.code}")
        }

        val body = try {
            JSONObject(response.body)
        } catch (error: Exception) {
            throw IOException("Invalid TMDB external IDs response", error)
        }

        return MovieExternalIds(
            imdbId = body.optNullableString("imdb_id"),
        )
    }

    private fun parseMovies(body: String, invalidResponseMessage: String): List<Movie> {
        val results = try {
            JSONObject(body).getJSONArray("results")
        } catch (error: Exception) {
            throw IOException(invalidResponseMessage, error)
        }

        return buildList {
            for (index in 0 until results.length()) {
                val item = results.optJSONObject(index) ?: continue
                val tmdbId = item.optInt("id", -1)
                val title = item.optString("title", "").trim()
                if (tmdbId <= 0 || title.isEmpty()) continue

                add(
                    Movie(
                        tmdbId = tmdbId,
                        title = title,
                        originalTitle = item.optString("original_title", title)
                            .trim()
                            .ifEmpty { title },
                        releaseYear = releaseYear(item.optNullableString("release_date")),
                        overview = item.optNullableString("overview"),
                        voteAverage = item.optNullableDouble("vote_average"),
                        posterPath = item.optNullableString("poster_path"),
                        backdropPath = item.optNullableString("backdrop_path"),
                    ),
                )
            }
        }
    }

    private fun JSONObject.optNullableString(name: String): String? =
        if (!has(name) || isNull(name)) null else optString(name).trim().takeIf { it.isNotEmpty() }

    private fun JSONObject.optNullableDouble(name: String): Double? =
        if (!has(name) || isNull(name)) null else optDouble(name).takeIf { !it.isNaN() }

    private fun releaseYear(date: String?): Int? =
        date?.takeIf { it.length >= 4 }?.take(4)?.toIntOrNull()

    private companion object {
        const val API_BASE_URL = "https://api.themoviedb.org/3"
        const val LANGUAGE = "en-US"
    }
}

fun tmdbPosterUrl(path: String?): String? =
    path?.takeIf { it.isNotBlank() }?.let { "https://image.tmdb.org/t/p/w500$it" }

fun tmdbBackdropUrl(path: String?): String? =
    path?.takeIf { it.isNotBlank() }?.let { "https://image.tmdb.org/t/p/w1280$it" }
