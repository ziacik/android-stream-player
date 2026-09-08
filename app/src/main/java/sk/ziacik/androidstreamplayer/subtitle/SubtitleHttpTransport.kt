package sk.ziacik.androidstreamplayer.subtitle

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

internal data class SubtitleHttpResponse(
	val code: Int,
	val body: ByteArray,
	val headers: Map<String, String> = emptyMap(),
) {
	val isSuccessful: Boolean
		get() = code in 200..299

	fun header(name: String): String? = headers.entries
		.firstOrNull { (key, _) -> key.equals(name, ignoreCase = true) }
		?.value
}

internal fun interface SubtitleHttpTransport {
	suspend fun execute(request: Request): SubtitleHttpResponse
}

internal class OkHttpSubtitleTransport(
	private val client: OkHttpClient = OkHttpClient(),
) : SubtitleHttpTransport {
	override suspend fun execute(request: Request): SubtitleHttpResponse =
		withContext(Dispatchers.IO) {
			client.newCall(request).execute().use { response ->
				SubtitleHttpResponse(
					code = response.code,
					body = response.body.bytes(),
					headers = response.headers.names().associateWith { name ->
						response.header(name).orEmpty()
					},
				)
			}
		}
}
