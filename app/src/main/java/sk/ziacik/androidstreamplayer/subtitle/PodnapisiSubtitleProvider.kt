package sk.ziacik.androidstreamplayer.subtitle

import java.io.File
import okhttp3.Request
import sk.ziacik.androidstreamplayer.catalog.Movie
import sk.ziacik.androidstreamplayer.search.TorrentSearchResult

class PodnapisiSubtitleProvider internal constructor(
	private val cacheDir: File,
	private val transport: SubtitleHttpTransport,
) {
	suspend fun find(movie: Movie, result: TorrentSearchResult): SubtitleTrack? = null
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
