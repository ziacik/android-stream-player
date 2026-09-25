package sk.ziacik.androidstreamplayer.search

import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

internal data class SkTorrentHttpResponse(
    val code: Int,
    val body: String,
) {
    val isSuccessful: Boolean
        get() = code in 200..299
}

internal interface SkTorrentHttpSession {
    suspend fun get(url: HttpUrl): SkTorrentHttpResponse
    suspend fun postForm(url: HttpUrl, fields: Map<String, String>): SkTorrentHttpResponse
    fun clearCookies()
}

internal class OkHttpSkTorrentSession : SkTorrentHttpSession {
    private val cookieJar = InMemoryCookieJar()
    private val client = OkHttpClient.Builder()
        .cookieJar(cookieJar)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    override suspend fun get(url: HttpUrl): SkTorrentHttpResponse =
        execute(Request.Builder().url(url).get().header("User-Agent", USER_AGENT).build())

    override suspend fun postForm(
        url: HttpUrl,
        fields: Map<String, String>,
    ): SkTorrentHttpResponse {
        val body = FormBody.Builder().apply {
            fields.forEach { (key, value) -> add(key, value) }
        }.build()
        return execute(
            Request.Builder()
                .url(url)
                .post(body)
                .header("User-Agent", USER_AGENT)
                .build(),
        )
    }

    override fun clearCookies() {
        cookieJar.clear()
    }

    private suspend fun execute(request: Request): SkTorrentHttpResponse =
        withContext(Dispatchers.IO) {
            try {
                client.newCall(request).execute().use { response ->
                    SkTorrentHttpResponse(
                        code = response.code,
                        body = response.body.string(),
                    )
                }
            } catch (error: IOException) {
                throw error
            }
        }

    private class InMemoryCookieJar : CookieJar {
        private val cookies = mutableListOf<Cookie>()

        @Synchronized
        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            cookies.forEach { incoming ->
                this.cookies.removeAll { existing ->
                    existing.name == incoming.name &&
                        existing.domain == incoming.domain &&
                        existing.path == incoming.path
                }
                this.cookies += incoming
            }
        }

        @Synchronized
        override fun loadForRequest(url: HttpUrl): List<Cookie> {
            val now = System.currentTimeMillis()
            cookies.removeAll { it.expiresAt < now }
            return cookies.filter { it.matches(url) }
        }

        @Synchronized
        fun clear() {
            cookies.clear()
        }
    }

    private companion object {
        const val USER_AGENT = "Kino Android TV"
    }
}
