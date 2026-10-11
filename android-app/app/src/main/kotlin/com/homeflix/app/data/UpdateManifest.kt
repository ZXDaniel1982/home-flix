package com.homeflix.app.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class UpdateManifest(
    val schemaVersion: Int,
    val versionCode: Int,
    val versionName: String,
    val minSdk: Int,
    val apkUrl: String,
    val sha256: String,
    val size: Long,
    val releaseNotes: String? = null
)

/** Raised when a manifest is well-formed JSON but not usable. */
class UpdateManifestException(message: String) : Exception(message)

object UpdateManifestJson {

    const val SUPPORTED_SCHEMA_VERSION = 1

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Parses and validates a manifest. Throws [UpdateManifestException] for an
     * unsupported schema or a blank required field, and a
     * `kotlinx.serialization.SerializationException` when a required field is absent.
     */
    fun parse(text: String): UpdateManifest {
        val manifest = json.decodeFromString(UpdateManifest.serializer(), text)
        if (manifest.schemaVersion != SUPPORTED_SCHEMA_VERSION) {
            throw UpdateManifestException("Unsupported manifest schema ${manifest.schemaVersion}")
        }
        if (manifest.apkUrl.isBlank() || manifest.sha256.isBlank() || manifest.versionName.isBlank()) {
            throw UpdateManifestException("Manifest is missing required fields")
        }
        return manifest
    }
}
