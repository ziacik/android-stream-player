package sk.ziacik.androidstreamplayer.search

import java.io.IOException
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.Jsoup

internal class SkTorrentTorrentFileFetcher(
    private val credentialsStore: SkTorrentCredentialsStore,
    private val session: SkTorrentHttpSession,
) {
    private var authenticatedCredentialFingerprint: Int? = null

    suspend fun fetch(url: String): ByteArray {
        val credentials = credentialsStore.load()?.takeIf { it.isComplete }
            ?: throw IOException("SkTorrent credentials are not configured")
        ensureAuthenticated(credentials)

        val parsedUrl = try {
            url.toHttpUrl()
        } catch (error: IllegalArgumentException) {
            throw IOException("Invalid SkTorrent torrent URL", error)
        }
        if (parsedUrl.host != HOST || !parsedUrl.encodedPath.endsWith("/torrent/download.php")) {
            throw IOException("Unexpected SkTorrent torrent URL")
        }

        var response = session.getBytes(parsedUrl)
        if (!isTorrentResponse(response)) {
            authenticatedCredentialFingerprint = null
            ensureAuthenticated(credentials)
            response = session.getBytes(parsedUrl)
        }
        if (!response.isSuccessful) {
            throw IOException("SkTorrent torrent download failed with HTTP ${response.code}")
        }
        if (!isTorrentResponse(response)) {
            throw IOException("SkTorrent did not return a torrent file")
        }
        if (response.body.size > MAX_TORRENT_BYTES) {
            throw IOException("SkTorrent torrent file is unexpectedly large")
        }
        return response.body
    }

    private suspend fun ensureAuthenticated(credentials: SkTorrentCredentials) {
        val fingerprint = 31 * credentials.username.hashCode() + credentials.password.hashCode()
        if (authenticatedCredentialFingerprint == fingerprint) return

        session.clearCookies()
        val response = session.postForm(
            LOGIN_URL.toHttpUrl(),
            mapOf(
                "uid" to credentials.username,
                "pwd" to credentials.password,
            ),
        )
        if (!response.isSuccessful) {
            throw IOException("SkTorrent login failed with HTTP ${response.code}")
        }

        val verification = session.get(INDEX_URL.toHttpUrl())
        if (!verification.isSuccessful || !isAuthenticatedPage(verification.body)) {
            authenticatedCredentialFingerprint = null
            throw IOException("SkTorrent login failed")
        }
        authenticatedCredentialFingerprint = fingerprint
    }

    private fun isAuthenticatedPage(body: String): Boolean {
        val document = Jsoup.parse(body, BASE_URL)
        return document.selectFirst("""a[href^="usercp.php"], a[href*="logout"]""") != null
    }

    private fun isTorrentResponse(response: SkTorrentBinaryResponse): Boolean {
        val contentType = response.contentType.orEmpty().lowercase()
        if ("application/x-bittorrent" in contentType || "application/octet-stream" in contentType) {
            return response.body.isNotEmpty()
        }
        // Bencoded .torrent files are dictionaries and therefore start with 'd'.
        return response.body.firstOrNull() == 'd'.code.toByte()
    }

    private companion object {
        const val HOST = "sktorrent.eu"
        const val BASE_URL = "https://sktorrent.eu/"
        const val LOGIN_URL = "https://sktorrent.eu/torrent/login.php?returnto=index.php"
        const val INDEX_URL = "https://sktorrent.eu/torrent/index.php"
        const val MAX_TORRENT_BYTES = 10 * 1024 * 1024
    }
}
