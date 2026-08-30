package sk.ziacik.androidstreamplayer.watch

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import sk.ziacik.androidstreamplayer.catalog.Movie
import sk.ziacik.androidstreamplayer.search.TorrentSearchResult

class SharedPreferencesWatchProgressStorage(context: Context) : WatchProgressStorage {
	private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

	override fun load(): List<WatchProgressEntry> = WatchProgressJson.decode(
		preferences.getString(KEY_ENTRIES, null) ?: "[]",
	)

	override fun save(entries: List<WatchProgressEntry>) {
		preferences.edit()
			.putString(KEY_ENTRIES, WatchProgressJson.encode(entries))
			.apply()
	}

	private companion object {
		const val PREFERENCES_NAME = "watch_progress"
		const val KEY_ENTRIES = "entries"
	}
}

internal object WatchProgressJson {
	fun encode(entries: List<WatchProgressEntry>): String {
		val array = JSONArray()
		entries.forEach { entry ->
			array.put(
				JSONObject()
					.put("movie", encodeMovie(entry.movie))
					.put("result", encodeResult(entry.result))
					.put("positionMs", entry.positionMs)
					.put("durationMs", entry.durationMs)
					.put("updatedAtEpochMs", entry.updatedAtEpochMs),
			)
		}
		return array.toString()
	}

	fun decode(value: String): List<WatchProgressEntry> = try {
		val array = JSONArray(value)
		buildList {
			for (index in 0 until array.length()) {
				val item = array.getJSONObject(index)
				add(
					WatchProgressEntry(
						movie = decodeMovie(item.getJSONObject("movie")),
						result = decodeResult(item.getJSONObject("result")),
						positionMs = item.getLong("positionMs"),
						durationMs = item.getLong("durationMs"),
						updatedAtEpochMs = item.getLong("updatedAtEpochMs"),
					),
				)
			}
		}
	} catch (_: Throwable) {
		emptyList()
	}

	private fun encodeMovie(movie: Movie) = JSONObject()
		.put("tmdbId", movie.tmdbId)
		.putNullable("imdbId", movie.imdbId)
		.put("title", movie.title)
		.put("originalTitle", movie.originalTitle)
		.putNullable("releaseYear", movie.releaseYear)
		.putNullable("overview", movie.overview)
		.putNullable("voteAverage", movie.voteAverage)
		.putNullable("posterPath", movie.posterPath)
		.putNullable("backdropPath", movie.backdropPath)

	private fun decodeMovie(value: JSONObject) = Movie(
		tmdbId = value.getInt("tmdbId"),
		imdbId = value.nullableString("imdbId"),
		title = value.getString("title"),
		originalTitle = value.getString("originalTitle"),
		releaseYear = value.nullableInt("releaseYear"),
		overview = value.nullableString("overview"),
		voteAverage = value.nullableDouble("voteAverage"),
		posterPath = value.nullableString("posterPath"),
		backdropPath = value.nullableString("backdropPath"),
	)

	private fun encodeResult(result: TorrentSearchResult) = JSONObject()
		.put("id", result.id)
		.put("title", result.title)
		.put("magnetUri", result.magnetUri)
		.putNullable("quality", result.quality)
		.putNullable("sizeBytes", result.sizeBytes)
		.putNullable("seeders", result.seeders)
		.putNullable("source", result.source)

	private fun decodeResult(value: JSONObject) = TorrentSearchResult(
		id = value.getString("id"),
		title = value.getString("title"),
		magnetUri = value.getString("magnetUri"),
		quality = value.nullableString("quality"),
		sizeBytes = value.nullableLong("sizeBytes"),
		seeders = value.nullableInt("seeders"),
		source = value.nullableString("source"),
	)
}

private fun JSONObject.putNullable(key: String, value: Any?): JSONObject =
	put(key, value ?: JSONObject.NULL)

private fun JSONObject.nullableString(key: String): String? =
	if (isNull(key)) null else getString(key)

private fun JSONObject.nullableInt(key: String): Int? =
	if (isNull(key)) null else getInt(key)

private fun JSONObject.nullableLong(key: String): Long? =
	if (isNull(key)) null else getLong(key)

private fun JSONObject.nullableDouble(key: String): Double? =
	if (isNull(key)) null else getDouble(key)
