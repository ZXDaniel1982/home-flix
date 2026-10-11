package com.homeflix.app

import com.homeflix.app.data.HttpException
import com.homeflix.app.data.HttpUpdateRepository
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateRepositoryTest {

    private val payload = "hello home flix".toByteArray()

    @Test
    fun fetchManifest_requestsTheDefaultPath() = runTest {
        val transport = FakeHttpTransport().apply {
            text =
                """
                {"schemaVersion":1,"versionCode":2,"versionName":"0.2.0","minSdk":24,
                 "apkUrl":"/updates/android/x.apk","sha256":"abc","size":10}
                """.trimIndent()
        }
        val repo = HttpUpdateRepository(transport)

        val manifest = repo.fetchManifest("http://host")

        assertEquals("http://host/updates/android/manifest.json", transport.textUrls.single())
        assertEquals(2, manifest.versionCode)
    }

    @Test
    fun fetchManifest_propagatesHttpErrors() = runTest {
        val transport = FakeHttpTransport().apply { textError = HttpException(404) }
        val repo = HttpUpdateRepository(transport)

        val error = runCatching { repo.fetchManifest("http://host") }.exceptionOrNull()

        assertTrue(error is HttpException)
    }

    @Test
    fun download_writesEveryByteAndReturnsTheDigest() = runTest {
        val transport = FakeHttpTransport().apply { streamBytes = payload }
        val repo = HttpUpdateRepository(transport)
        val sink = ByteArrayOutputStream()

        val digest = repo.download("http://host/x.apk", payload.size.toLong(), { _, _ -> }, sink)

        assertArrayEquals(payload, sink.toByteArray())
        assertEquals(sha256Hex(payload), digest)
    }

    @Test
    fun download_reportsProgressUpToTheTotal() = runTest {
        val transport = FakeHttpTransport().apply { streamBytes = payload }
        val repo = HttpUpdateRepository(transport)
        val progress = mutableListOf<Pair<Long, Long>>()

        repo.download("http://host/x.apk", payload.size.toLong(), { done, total -> progress.add(done to total) }, ByteArrayOutputStream())

        assertEquals(payload.size.toLong(), progress.last().first)
        assertEquals(payload.size.toLong(), progress.last().second)
    }

    @Test
    fun download_usesManifestSizeWhenContentLengthIsUnknown() = runTest {
        val transport = object : com.homeflix.app.data.HttpTransport {
            override fun getText(url: String) = ""
            override fun openStream(url: String) = com.homeflix.app.data.HttpBody(
                java.io.ByteArrayInputStream(payload), -1L
            )
        }
        val repo = HttpUpdateRepository(transport)
        val progress = mutableListOf<Pair<Long, Long>>()

        repo.download("http://host/x.apk", payload.size.toLong(), { done, total -> progress.add(done to total) }, ByteArrayOutputStream())

        assertEquals(payload.size.toLong(), progress.last().second)
    }

    @Test
    fun download_propagatesStreamErrors() = runTest {
        val transport = FakeHttpTransport().apply { streamError = HttpException(500) }
        val repo = HttpUpdateRepository(transport)

        val error = runCatching {
            repo.download("http://host/x.apk", 1L, { _, _ -> }, ByteArrayOutputStream())
        }.exceptionOrNull()

        assertTrue(error is HttpException)
    }
}
