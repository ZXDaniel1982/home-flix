package com.homeflix.app.data

import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Minimal HTTP surface so the update logic can be tested without a socket. */
interface HttpTransport {
    /** GET [url] and return the body as text. Throws [HttpException] on a non-200 response. */
    fun getText(url: String): String

    /** GET [url] and return the body stream plus its content length (-1 if unknown). */
    fun openStream(url: String): HttpBody
}

data class HttpBody(val stream: InputStream, val contentLength: Long)

class HttpException(val code: Int) : Exception("HTTP $code")

class UrlConnectionHttpTransport(
    private val connectTimeoutMs: Int = 10_000,
    private val readTimeoutMs: Int = 30_000
) : HttpTransport {

    override fun getText(url: String): String {
        val connection = openConnection(url)
        try {
            val code = connection.responseCode
            if (code != HttpURLConnection.HTTP_OK) throw HttpException(code)
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    override fun openStream(url: String): HttpBody {
        val connection = openConnection(url)
        val code = connection.responseCode
        if (code != HttpURLConnection.HTTP_OK) {
            connection.disconnect()
            throw HttpException(code)
        }
        return HttpBody(connection.inputStream, connection.contentLengthLong)
    }

    private fun openConnection(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = connectTimeoutMs
            readTimeout = readTimeoutMs
            instanceFollowRedirects = true
        }
}

interface UpdateRepository {
    /** Fetch and parse the manifest for [origin] (e.g. `http://orangepi3b.local`). */
    suspend fun fetchManifest(origin: String): UpdateManifest

    /**
     * Stream [apkUrl] into [sink], reporting progress, and return the SHA-256 hex
     * digest of the bytes written. Never verifies the digest itself.
     */
    suspend fun download(
        apkUrl: String,
        size: Long,
        onProgress: (downloaded: Long, total: Long) -> Unit,
        sink: OutputStream
    ): String
}

class HttpUpdateRepository(
    private val transport: HttpTransport = UrlConnectionHttpTransport(),
    private val manifestPath: String = DEFAULT_MANIFEST_PATH
) : UpdateRepository {

    override suspend fun fetchManifest(origin: String): UpdateManifest = withContext(Dispatchers.IO) {
        UpdateManifestJson.parse(transport.getText(joinUrl(origin, manifestPath)))
    }

    override suspend fun download(
        apkUrl: String,
        size: Long,
        onProgress: (downloaded: Long, total: Long) -> Unit,
        sink: OutputStream
    ): String = withContext(Dispatchers.IO) {
        val body = transport.openStream(apkUrl)
        val total = if (body.contentLength > 0) body.contentLength else size
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var downloaded = 0L
        body.stream.use { input ->
            var read = input.read(buffer)
            while (read >= 0) {
                sink.write(buffer, 0, read)
                digest.update(buffer, 0, read)
                downloaded += read
                onProgress(downloaded, total)
                read = input.read(buffer)
            }
        }
        digest.digest().joinToString("") { "%02x".format(it) }
    }

    companion object {
        const val DEFAULT_MANIFEST_PATH = "/updates/android/manifest.json"
    }
}
