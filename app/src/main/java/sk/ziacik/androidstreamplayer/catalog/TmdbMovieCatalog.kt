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

        val url = "${API_BASE_URL}/search/multi".toHttpUrl()
            .newBuilder()
            .addQueryParameter("api_key", apiKey)
            .addQueryParameter("query", normalizedQuery)
            .addQueryParameter("include_adult", "false")
            .addQueryParameter("language", LANGUAGE)
            .build()

        val response = transport.execute(Request.Builder().url(url).get().build())
        if (!response.isSuccessful) {
            throw IOException("TMDB title search failed with HTTP ${response.code}")
        }

        return parseTitles(response.body, "Invalid TMDB title search response")
    }

    suspend fun trending(): List<Movie> {
        val url = "${API_BASE_URL}/trending/all/week".toHttpUrl()
            .newBuilder()
            .addQueryParameter("api_key", apiKey)
            .addQueryParameter("language", LANGUAGE)
            .build()

        val response = transport.execute(Request.Builder().url(url).get().build())
        if (!response.isSuccessful) {
            throw IOException("TMDB trending titles failed with HTTP ${response.code}")
        }

        return parseTitles(response.body, "Invalid TMDB trending titles response")
    }

    override suspend fun externalIds(tmdbId: Int): MovieExternalIds =
        externalIdsForPath("movie/$tmdbId")

    override suspend fun externalIds(movie: Movie): MovieExternalIds {
        val kind = when (movie.mediaType) {
            MediaType.MOVIE -> "movie"
            MediaType.SERIES, MediaType.EPISODE -> "tv"
        }
        return externalIdsForPath("$kind/${movie.tmdbId}")
    }

    override suspend fun seasons(tmdbId: Int): List<SeriesSeason> {
        val body = requestJson("tv/$tmdbId", "TMDB series details")
        val seasons = body.optJSONArray("seasons") ?: return emptyList()
        return buildList {
            for (index in 0 until seasons.length()) {
                val season = seasons.optJSONObject(index) ?: continue
                val number = season.optInt("season_number", -1)
                if (number <= 0) continue
                add(
                    SeriesSeason(
                        number = number,
                        name = season.optString("name", "Season $number").trim().ifEmpty { "Season $number" },
                        episodeCount = season.optInt("episode_count", 0).coerceAtLeast(0),
                        airYear = releaseYear(season.optNullableString("air_date")),
                        posterPath = season.optNullableString("poster_path"),
                    ),
                )
            }
        }.sortedBy { it.number }
    }

    override suspend fun episodes(tmdbId: Int, seasonNumber: Int): List<SeriesEpisode> {
        val body = requestJson("tv/$tmdbId/season/$seasonNumber", "TMDB season details")
        val episodes = body.optJSONArray("episodes") ?: return emptyList()
        return buildList {
            for (index in 0 until episodes.length()) {
                val episode = episodes.optJSONObject(index) ?: continue
                val number = episode.optInt("episode_number", -1)
                if (number <= 0) continue
                add(
                    SeriesEpisode(
                        number = number,
                        seasonNumber = seasonNumber,
                        name = episode.optString("name", "Episode $number").trim().ifEmpty { "Episode $number" },
                        overview = episode.optNullableString("overview"),
                        airYear = releaseYear(episode.optNullableString("air_date")),
                        voteAverage = episode.optNullableDouble("vote_average"),
                        stillPath = episode.optNullableString("still_path"),
                    ),
                )
            }
        }.sortedBy { it.number }
    }

    private suspend fun externalIdsForPath(path: String): MovieExternalIds {
        val body = requestJson("$path/external_ids", "TMDB external IDs")
        return MovieExternalIds(imdbId = body.optNullableString("imdb_id"))
    }

    private suspend fun requestJson(path: String, label: String): JSONObject {
        val url = "${API_BASE_URL}/$path".toHttpUrl()
            .newBuilder()
            .addQueryParameter("api_key", apiKey)
            .addQueryParameter("language", LANGUAGE)
            .build()
        val response = transport.execute(Request.Builder().url(url).get().build())
        if (!response.isSuccessful) {
            throw IOException("$label failed with HTTP ${response.code}")
        }
        return try {
            JSONObject(response.body)
        } catch (error: Exception) {
            throw IOException("Invalid $label response", error)
        }
    }

    private fun parseTitles(body: String, invalidResponseMessage: String): List<Movie> {
        val results = try {
            JSONObject(body).getJSONArray("results")
        } catch (error: Exception) {
            throw IOException(invalidResponseMessage, error)
        }

        return buildList {
            for (index in 0 until results.length()) {
                val item = results.optJSONObject(index) ?: continue
                val mediaType = when (item.optString("media_type")) {
                    "movie" -> MediaType.MOVIE
                    "tv" -> MediaType.SERIES
                    else -> continue
                }
                val tmdbId = item.optInt("id", -1)
                if (tmdbId <= 0) continue

                val titleKey = if (mediaType == MediaType.SERIES) "name" else "title"
                val originalTitleKey = if (mediaType == MediaType.SERIES) "original_name" else "original_title"
                val dateKey = if (mediaType == MediaType.SERIES) "first_air_date" else "release_date"
                val title = item.optString(titleKey, "").trim()
                if (title.isEmpty()) continue

                add(
                    Movie(
                        tmdbId = tmdbId,
                        title = title,
                        originalTitle = item.optString(originalTitleKey, title).trim().ifEmpty { title },
                        releaseYear = releaseYear(item.optNullableString(dateKey)),
                        overview = item.optNullableString("overview"),
                        voteAverage = item.optNullableDouble("vote_average"),
                        posterPath = item.optNullableString("poster_path"),
                        backdropPath = item.optNullableString("backdrop_path"),
                        mediaType = mediaType,
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

fun tmdbStillUrl(path: String?): String? =
    path?.takeIf { it.isNotBlank() }?.let { "https://image.tmdb.org/t/p/w500$it" }
