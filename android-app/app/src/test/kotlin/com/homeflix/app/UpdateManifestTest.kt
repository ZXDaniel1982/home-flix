package com.homeflix.app

import com.homeflix.app.data.UpdateManifestException
import com.homeflix.app.data.UpdateManifestJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class UpdateManifestTest {

    @Test
    fun parse_readsAllFields() {
        val manifest = UpdateManifestJson.parse(
            """
            {
              "schemaVersion": 1,
              "versionCode": 2,
              "versionName": "0.2.0",
              "minSdk": 24,
              "apkUrl": "/updates/android/home-flix-0.2.0.apk",
              "sha256": "abc123",
              "size": 1234,
              "releaseNotes": "Adds the episode browser."
            }
            """.trimIndent()
        )

        assertEquals(1, manifest.schemaVersion)
        assertEquals(2, manifest.versionCode)
        assertEquals("0.2.0", manifest.versionName)
        assertEquals(24, manifest.minSdk)
        assertEquals("/updates/android/home-flix-0.2.0.apk", manifest.apkUrl)
        assertEquals("abc123", manifest.sha256)
        assertEquals(1234L, manifest.size)
        assertEquals("Adds the episode browser.", manifest.releaseNotes)
    }

    @Test
    fun parse_allowsMissingReleaseNotes() {
        val manifest = UpdateManifestJson.parse(
            """
            {"schemaVersion":1,"versionCode":2,"versionName":"0.2.0","minSdk":24,
             "apkUrl":"/updates/android/x.apk","sha256":"abc","size":10}
            """.trimIndent()
        )

        assertNull(manifest.releaseNotes)
    }

    @Test
    fun parse_rejectsUnsupportedSchemaVersion() {
        val json =
            """
            {"schemaVersion":99,"versionCode":2,"versionName":"0.2.0","minSdk":24,
             "apkUrl":"/updates/android/x.apk","sha256":"abc","size":10}
            """.trimIndent()

        assertThrows(UpdateManifestException::class.java) { UpdateManifestJson.parse(json) }
    }

    @Test
    fun parse_rejectsMissingRequiredField() {
        val json =
            """
            {"schemaVersion":1,"versionName":"0.2.0","minSdk":24,
             "apkUrl":"/updates/android/x.apk","sha256":"abc","size":10}
            """.trimIndent()

        assertThrows(Exception::class.java) { UpdateManifestJson.parse(json) }
    }

    @Test
    fun parse_rejectsBlankSha256() {
        val json =
            """
            {"schemaVersion":1,"versionCode":2,"versionName":"0.2.0","minSdk":24,
             "apkUrl":"/updates/android/x.apk","sha256":"","size":10}
            """.trimIndent()

        assertThrows(UpdateManifestException::class.java) { UpdateManifestJson.parse(json) }
    }
}
