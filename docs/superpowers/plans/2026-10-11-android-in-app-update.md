# Android In-App Update Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let the Android app discover a newer release on the Orange Pi and download, verify, and install it from inside the app.

**Architecture:** Caddy serves `updates/android/manifest.json` plus version-named APKs from the existing `/srv` mount. The app derives the origin from its stored Jellyfin base URL (stripping `/api`), fetches and validates the manifest, then streams the APK into a `PackageInstaller` session — committing only after the SHA-256 matches. A single app-scoped `UpdateViewModel` drives both the launch-time check and the manual "Check for updates" action on a new Settings screen.

**Tech Stack:** Kotlin / Jetpack Compose / Material3 / `kotlinx-serialization-json` / `java.net.HttpURLConnection` / Android `PackageInstaller` (Android); Caddy + bash (server).

**Spec:** `docs/superpowers/specs/2026-10-11-android-in-app-update-design.md`

## Global Constraints

- Local home network only: the update origin is derived from the stored server URL; no public internet, no Play Store / F-Droid.
- No Jellyfin or transcoding changes; no web-frontend changes.
- Android: Kotlin + Compose, MVVM, `minSdk = 24`, `targetSdk = 36`.
- **No new third-party runtime dependencies.** `kotlinx-serialization-json` is promoted from `testImplementation` to `implementation` (already in the version catalog) — this is not a new dependency. The Kotlin serialization compiler plugin ships with Kotlin.
- Manifest contract is fixed: `schemaVersion` (currently `1`), `versionCode`, `versionName`, `minSdk`, `apkUrl` (relative to origin), `sha256`, `size`, optional `releaseNotes`.
- Release APKs are signed with one stable keystore; secrets (`keystore.properties`, `*.jks`, `*.keystore`) are never committed.
- The Android dev machine is headless: steps that need a real device/emulator are explicitly marked **deferred** and run later on the Windows PC.
- Commit after each task with the message shown in the task.

---

## File Structure

**Server / scripts**
- `docker/Caddyfile` — add the `/updates/*` route.
- `scripts/publish-android-release.sh` — build, sign, manifest, deploy.
- `scripts/test-publish-android-release.sh` — smoke test for the above.

**Android build**
- `android-app/gradle/libs.versions.toml` — add the serialization plugin alias; keep `kotlinx-serialization-json`.
- `android-app/build.gradle.kts` — register the serialization plugin (`apply false`).
- `android-app/app/build.gradle.kts` — apply the serialization plugin, `buildConfig = true`, `signingConfigs.release`, `printVersionInfo` task.
- `android-app/app/src/main/AndroidManifest.xml` — add `REQUEST_INSTALL_PACKAGES`.
- `.gitignore` — ignore keystore secrets.

**Android data layer** (`app/src/main/kotlin/com/homeflix/app/data/`)
- `UpdateManifest.kt` — model + `UpdateManifestJson.parse`.
- `UpdateOrigin.kt` — `updateOrigin`, `joinUrl`.
- `UpdateRepository.kt` — `HttpTransport`, `HttpBody`, `UrlConnectionHttpTransport`, `HttpException`, `UpdateRepository`, `HttpUpdateRepository`.
- `UpdateInstaller.kt` — `InstallResult`, `UpdateInstaller`.
- `PackageInstallerUpdateInstaller.kt` — the platform implementation.
- `SettingsRepository.kt` — add `skippedVersionCode` / `setSkippedVersionCode`.
- `di/AppContainer.kt` — wire the two new dependencies.

**Android UI** (`app/src/main/kotlin/com/homeflix/app/`)
- `ui/screens/UpdateViewModel.kt` — app-scoped state and orchestration.
- `ui/screens/SettingsScreen.kt` — version, check-for-updates, installs permission, log out.
- `ui/screens/UpdateDialogs.kt` — available-update and install-progress dialogs.
- `navigation/Routes.kt` — add `SETTINGS`.
- `navigation/AppNavHost.kt` — settings route, top-bar gear callbacks, dialogs, launch check.
- `ui/screens/{Home,Movies,TvSeries,Search}Screen.kt` — gear icon replaces the "Log out" text button.
- `MainActivity.kt` — create and pass the `UpdateViewModel`.

**Android tests** (`app/src/test/kotlin/com/homeflix/app/`)
- `TestDoubles.kt` — extend `FakeSettingsRepository`; add `FakeHttpTransport`, `FakeUpdateRepository`, `FakeUpdateInstaller`, `sha256Hex`.
- `UpdateManifestTest.kt`, `UpdateOriginTest.kt`, `UpdateRepositoryTest.kt`, `UpdateViewModelTest.kt`.

---

## Task 1: Caddy `/updates/*` route

**Files:**
- Modify: `docker/Caddyfile`

**Interfaces:**
- Consumes: nothing.
- Produces: the URL space `/updates/android/manifest.json` and `/updates/android/*.apk` served from `/srv`.

- [ ] **Step 1: Add the route**

In `docker/Caddyfile`, insert this block **before** the final catch-all `handle {` (so it is not swallowed by the SPA fallback):

```caddyfile
	# Android release artifacts. Served from the same mount as the web app but
	# explicitly routed so the SPA fallback never rewrites them to index.html.
	# The manifest revalidates so a new release is seen immediately; versioned
	# APK filenames are immutable.
	handle /updates/* {
		root * /srv
		header /updates/android/manifest.json Cache-Control "no-cache"
		header /updates/android/*.apk Cache-Control "public, max-age=31536000, immutable"
		file_server
	}
```

- [ ] **Step 2: Validate the Caddyfile**

Run:

```bash
docker run --rm \
  -v "$PWD/docker/Caddyfile:/etc/caddy/Caddyfile:ro" \
  -e SERVER_HOSTNAME=orangepi3b.local -e SERVER_IP=192.168.68.58 \
  caddy:2.11.4-alpine caddy validate --config /etc/caddy/Caddyfile --adapter caddyfile
```

Expected: `Valid configuration`. If Docker cannot pull the image (no network), defer this check to deploy time and review the block by eye.

- [ ] **Step 3: Commit**

```bash
git add docker/Caddyfile
git commit -m "feat(caddy): serve Android release artifacts under /updates"
```

---

## Task 2: Android build foundations (serialization, BuildConfig, version task)

**Files:**
- Modify: `android-app/gradle/libs.versions.toml`
- Modify: `android-app/build.gradle.kts`
- Modify: `android-app/app/build.gradle.kts`

**Interfaces:**
- Consumes: nothing.
- Produces: `BuildConfig.VERSION_CODE` / `BuildConfig.VERSION_NAME`; the Gradle task `:app:printVersionInfo` printing `"<versionCode> <versionName> <minSdk>"`; `kotlinx.serialization` available to main source.

- [ ] **Step 1: Add the serialization plugin alias**

In `android-app/gradle/libs.versions.toml`, under `[plugins]`, add:

```toml
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
```

- [ ] **Step 2: Register the plugin in the root build**

Replace `android-app/build.gradle.kts` with:

```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}
```

- [ ] **Step 3: Apply the plugin, enable BuildConfig, add the version task**

In `android-app/app/build.gradle.kts`:

Apply the serialization plugin — change the `plugins` block to:

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}
```

Enable `BuildConfig` — change `buildFeatures` to:

```kotlin
    buildFeatures {
        compose = true
        buildConfig = true
    }
```

Add the version task at the end of the file:

```kotlin
// Prints "<versionCode> <versionName> <minSdk>" for scripts/publish-android-release.sh.
tasks.register("printVersionInfo") {
    val code = android.defaultConfig.versionCode
    val name = android.defaultConfig.versionName
    val minSdk = android.defaultConfig.minSdk
    doLast {
        println("$code $name $minSdk")
    }
}
```

Make the serialization JSON library available to main — in `dependencies`, add a line next to the other `implementation`s (keep the existing `testImplementation` line):

```kotlin
    implementation(libs.kotlinx.serialization.json)
```

- [ ] **Step 4: Verify the version task and that the app still builds**

Run (from `android-app/`):

```bash
./gradlew -q :app:printVersionInfo
```

Expected: `1 0.1.0 24` (a single line).

Then:

```bash
./gradlew :app:testDebugUnitTest
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
git add android-app/gradle/libs.versions.toml android-app/build.gradle.kts android-app/app/build.gradle.kts
git commit -m "build(android): enable serialization and BuildConfig, add version task"
```

---

## Task 3: `UpdateManifest` model and parser

**Files:**
- Create: `android-app/app/src/main/kotlin/com/homeflix/app/data/UpdateManifest.kt`
- Test: `android-app/app/src/test/kotlin/com/homeflix/app/UpdateManifestTest.kt`

**Interfaces:**
- Consumes: `kotlinx-serialization-json`.
- Produces:
  - `data class UpdateManifest(schemaVersion: Int, versionCode: Int, versionName: String, minSdk: Int, apkUrl: String, sha256: String, size: Long, releaseNotes: String?)`
  - `class UpdateManifestException(message: String) : Exception`
  - `object UpdateManifestJson { const val SUPPORTED_SCHEMA_VERSION = 1; fun parse(text: String): UpdateManifest }`

- [ ] **Step 1: Write the failing tests**

Create `android-app/app/src/test/kotlin/com/homeflix/app/UpdateManifestTest.kt`:

```kotlin
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
```

- [ ] **Step 2: Run the tests to verify they fail**

Run (from `android-app/`): `./gradlew :app:testDebugUnitTest --tests "com.homeflix.app.UpdateManifestTest"`
Expected: FAIL — `UpdateManifestJson` is unresolved.

- [ ] **Step 3: Implement the model and parser**

Create `android-app/app/src/main/kotlin/com/homeflix/app/data/UpdateManifest.kt`:

```kotlin
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
```

- [ ] **Step 4: Run the tests to verify they pass**

Run (from `android-app/`): `./gradlew :app:testDebugUnitTest --tests "com.homeflix.app.UpdateManifestTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add android-app/app/src/main/kotlin/com/homeflix/app/data/UpdateManifest.kt android-app/app/src/test/kotlin/com/homeflix/app/UpdateManifestTest.kt
git commit -m "feat(android): add update manifest model and parser"
```

---

## Task 4: `updateOrigin` and `joinUrl` helpers

**Files:**
- Create: `android-app/app/src/main/kotlin/com/homeflix/app/data/UpdateOrigin.kt`
- Test: `android-app/app/src/test/kotlin/com/homeflix/app/UpdateOriginTest.kt`

**Interfaces:**
- Consumes: nothing.
- Produces:
  - `fun updateOrigin(serverUrl: String): String`
  - `fun joinUrl(origin: String, path: String): String`

- [ ] **Step 1: Write the failing tests**

Create `android-app/app/src/test/kotlin/com/homeflix/app/UpdateOriginTest.kt`:

```kotlin
package com.homeflix.app

import com.homeflix.app.data.joinUrl
import com.homeflix.app.data.updateOrigin
import org.junit.Assert.assertEquals
import org.junit.Test

class UpdateOriginTest {

    @Test
    fun updateOrigin_stripsApiSuffix() {
        assertEquals("http://orangepi3b.local", updateOrigin("http://orangepi3b.local/api"))
    }

    @Test
    fun updateOrigin_stripsApiSuffixAndTrailingSlash() {
        assertEquals("http://host", updateOrigin("http://host/api/"))
    }

    @Test
    fun updateOrigin_keepsPort() {
        assertEquals("http://host:8080", updateOrigin("http://host:8080/api"))
    }

    @Test
    fun updateOrigin_leavesBareUrlAlone() {
        assertEquals("http://host", updateOrigin("http://host"))
    }

    @Test
    fun joinUrl_addsOneSlash() {
        assertEquals(
            "http://host/updates/android/manifest.json",
            joinUrl("http://host", "/updates/android/manifest.json")
        )
    }

    @Test
    fun joinUrl_toleratesTrailingSlashOnOrigin() {
        assertEquals(
            "http://host/updates/android/x.apk",
            joinUrl("http://host/", "updates/android/x.apk")
        )
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run (from `android-app/`): `./gradlew :app:testDebugUnitTest --tests "com.homeflix.app.UpdateOriginTest"`
Expected: FAIL — `updateOrigin` / `joinUrl` are unresolved.

- [ ] **Step 3: Implement the helpers**

Create `android-app/app/src/main/kotlin/com/homeflix/app/data/UpdateOrigin.kt`:

```kotlin
package com.homeflix.app.data

/**
 * The origin (scheme://host[:port]) behind the configured server URL. The app
 * stores the Jellyfin base URL, which always ends in `/api`
 * (see `ServerUrlViewModel.normalize`), so strip that suffix.
 */
fun updateOrigin(serverUrl: String): String {
    val trimmed = serverUrl.trim()
    val withoutSlash = trimmed.trimEnd('/')
    return if (withoutSlash.endsWith("/api")) {
        withoutSlash.removeSuffix("/api").trimEnd('/')
    } else {
        withoutSlash
    }
}

/** Joins [origin] and [path] with exactly one slash between them. */
fun joinUrl(origin: String, path: String): String {
    val base = origin.trimEnd('/')
    val suffix = if (path.startsWith("/")) path else "/$path"
    return base + suffix
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run (from `android-app/`): `./gradlew :app:testDebugUnitTest --tests "com.homeflix.app.UpdateOriginTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add android-app/app/src/main/kotlin/com/homeflix/app/data/UpdateOrigin.kt android-app/app/src/test/kotlin/com/homeflix/app/UpdateOriginTest.kt
git commit -m "feat(android): add update origin and URL join helpers"
```

---

## Task 5: `UpdateRepository` (manifest fetch + verified APK download)

**Files:**
- Create: `android-app/app/src/main/kotlin/com/homeflix/app/data/UpdateRepository.kt`
- Modify: `android-app/app/src/test/kotlin/com/homeflix/app/TestDoubles.kt`
- Test: `android-app/app/src/test/kotlin/com/homeflix/app/UpdateRepositoryTest.kt`

**Interfaces:**
- Consumes: `UpdateManifestJson.parse`, `joinUrl`.
- Produces:
  - `interface HttpTransport { fun getText(url: String): String; fun openStream(url: String): HttpBody }`
  - `data class HttpBody(val stream: InputStream, val contentLength: Long)`
  - `class HttpException(val code: Int) : Exception`
  - `class UrlConnectionHttpTransport(connectTimeoutMs: Int = 10_000, readTimeoutMs: Int = 30_000) : HttpTransport`
  - `interface UpdateRepository { suspend fun fetchManifest(origin: String): UpdateManifest; suspend fun download(apkUrl: String, size: Long, onProgress: (Long, Long) -> Unit, sink: OutputStream): String }`
  - `class HttpUpdateRepository(transport: HttpTransport = UrlConnectionHttpTransport(), manifestPath: String = DEFAULT_MANIFEST_PATH) : UpdateRepository` with `const val DEFAULT_MANIFEST_PATH = "/updates/android/manifest.json"`
  - Test doubles: `FakeHttpTransport`, `sha256Hex(bytes: ByteArray): String`

- [ ] **Step 1: Add the test doubles**

Append to `android-app/app/src/test/kotlin/com/homeflix/app/TestDoubles.kt` (add imports `com.homeflix.app.data.HttpBody`, `com.homeflix.app.data.HttpTransport`, `java.io.ByteArrayInputStream`, `java.security.MessageDigest`):

```kotlin
class FakeHttpTransport : HttpTransport {
    var text: String = ""
    var textError: Exception? = null
    var streamBytes: ByteArray = ByteArray(0)
    var streamError: Exception? = null
    val textUrls = mutableListOf<String>()
    val streamUrls = mutableListOf<String>()

    override fun getText(url: String): String {
        textUrls.add(url)
        textError?.let { throw it }
        return text
    }

    override fun openStream(url: String): HttpBody {
        streamUrls.add(url)
        streamError?.let { throw it }
        return HttpBody(ByteArrayInputStream(streamBytes), streamBytes.size.toLong())
    }
}

fun sha256Hex(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
```

- [ ] **Step 2: Write the failing tests**

Create `android-app/app/src/test/kotlin/com/homeflix/app/UpdateRepositoryTest.kt`:

```kotlin
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
```

- [ ] **Step 3: Run the tests to verify they fail**

Run (from `android-app/`): `./gradlew :app:testDebugUnitTest --tests "com.homeflix.app.UpdateRepositoryTest"`
Expected: FAIL — `HttpUpdateRepository` is unresolved.

- [ ] **Step 4: Implement the transport and repository**

Create `android-app/app/src/main/kotlin/com/homeflix/app/data/UpdateRepository.kt`:

```kotlin
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

    override fun getText(url: String): String = openConnection(url).use { connection ->
        val code = connection.responseCode
        if (code != HttpURLConnection.HTTP_OK) throw HttpException(code)
        connection.inputStream.bufferedReader().use { it.readText() }
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
```

- [ ] **Step 5: Run the tests to verify they pass**

Run (from `android-app/`): `./gradlew :app:testDebugUnitTest --tests "com.homeflix.app.UpdateRepositoryTest"`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add android-app/app/src/main/kotlin/com/homeflix/app/data/UpdateRepository.kt android-app/app/src/test/kotlin/com/homeflix/app/UpdateRepositoryTest.kt android-app/app/src/test/kotlin/com/homeflix/app/TestDoubles.kt
git commit -m "feat(android): add update repository with verified download"
```

---

## Task 6: `UpdateInstaller` and the package-installer implementation

**Files:**
- Create: `android-app/app/src/main/kotlin/com/homeflix/app/data/UpdateInstaller.kt`
- Create: `android-app/app/src/main/kotlin/com/homeflix/app/data/PackageInstallerUpdateInstaller.kt`
- Modify: `android-app/app/src/main/AndroidManifest.xml`
- Modify: `android-app/app/src/main/kotlin/com/homeflix/app/di/AppContainer.kt`
- Test: `android-app/app/src/test/kotlin/com/homeflix/app/TestDoubles.kt` (add the fake)

**Interfaces:**
- Consumes: nothing (uses Android `PackageInstaller`).
- Produces:
  - `sealed interface InstallResult { data object Success; data class Failed(reason: String); data object Cancelled }`
  - `interface UpdateInstaller { fun canInstallPackages(): Boolean; suspend fun install(write: suspend (OutputStream) -> Unit); val results: Flow<InstallResult> }`
  - `class PackageInstallerUpdateInstaller(context: Context) : UpdateInstaller`
  - `AppContainer.updateInstaller: UpdateInstaller`
  - Test double `FakeUpdateInstaller`

- [ ] **Step 1: Add the interface**

Create `android-app/app/src/main/kotlin/com/homeflix/app/data/UpdateInstaller.kt`:

```kotlin
package com.homeflix.app.data

import java.io.OutputStream
import kotlinx.coroutines.flow.Flow

sealed interface InstallResult {
    data object Success : InstallResult
    data class Failed(val reason: String) : InstallResult
    data object Cancelled : InstallResult
}

interface UpdateInstaller {
    /** Whether the app may currently install packages. */
    fun canInstallPackages(): Boolean

    /**
     * Open a package-installer session, run [write] to fill it, then commit.
     * If [write] throws, the session is abandoned and nothing is installed.
     * Outcomes are emitted on [results].
     */
    suspend fun install(write: suspend (OutputStream) -> Unit)

    val results: Flow<InstallResult>
}
```

- [ ] **Step 2: Add the fake**

Append to `android-app/app/src/test/kotlin/com/homeflix/app/TestDoubles.kt` (add imports `com.homeflix.app.data.InstallResult`, `com.homeflix.app.data.UpdateInstaller`, `java.io.ByteArrayOutputStream`, `java.io.OutputStream`, `kotlinx.coroutines.channels.Channel`, `kotlinx.coroutines.flow.Flow`, `kotlinx.coroutines.flow.receiveAsFlow`):

```kotlin
class FakeUpdateInstaller : UpdateInstaller {
    private val channel = Channel<InstallResult>(Channel.BUFFERED)
    override val results: Flow<InstallResult> = channel.receiveAsFlow()

    var canInstall: Boolean = true
    var result: InstallResult = InstallResult.Success
    var installCalls: Int = 0
    var committed: Boolean = false
    var writtenBytes: ByteArray? = null

    override fun canInstallPackages(): Boolean = canInstall

    override suspend fun install(write: suspend (OutputStream) -> Unit) {
        installCalls += 1
        val sink = ByteArrayOutputStream()
        // Mirrors the real installer: a throwing write aborts the session and
        // produces no result.
        write(sink)
        writtenBytes = sink.toByteArray()
        committed = true
        channel.send(result)
    }
}
```

- [ ] **Step 3: Add the permission and implement the installer**

In `android-app/app/src/main/AndroidManifest.xml`, add below the `<manifest ...>` opening tag:

```xml
    <!-- Required to hand a downloaded APK to the system installer. -->
    <uses-permission android:name="android.permission.REQUEST_INSTALL_PACKAGES" />
```

Create `android-app/app/src/main/kotlin/com/homeflix/app/data/PackageInstallerUpdateInstaller.kt`:

```kotlin
package com.homeflix.app.data

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.IntentSender
import android.content.pm.PackageInstaller
import android.os.Build
import androidx.core.content.ContextCompat
import java.io.OutputStream
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * Installs APKs through Android's [PackageInstaller]. The caller writes the APK
 * bytes into the session; the session is only committed when that write returns,
 * so a failed verification aborts the install. The user always confirms through
 * the system dialog ([PackageInstaller.STATUS_PENDING_USER_ACTION]), which this
 * class launches itself so no `Intent` leaks into the ViewModel.
 */
class PackageInstallerUpdateInstaller(context: Context) : UpdateInstaller {

    private val appContext = context.applicationContext

    private val channel = Channel<InstallResult>(Channel.BUFFERED)
    override val results: Flow<InstallResult> = channel.receiveAsFlow()

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
                PackageInstaller.STATUS_SUCCESS ->
                    channel.trySend(InstallResult.Success)

                PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                    @Suppress("DEPRECATION")
                    val confirm = intent.getParcelableExtra(PackageInstaller.EXTRA_INTENT) as? Intent
                    if (confirm != null) {
                        confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        runCatching { appContext.startActivity(confirm) }
                    }
                }

                PackageInstaller.STATUS_FAILURE_ABORTED ->
                    channel.trySend(InstallResult.Cancelled)

                else -> {
                    val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                    channel.trySend(InstallResult.Failed(message ?: "Install failed."))
                }
            }
        }
    }

    init {
        ContextCompat.registerReceiver(
            appContext,
            receiver,
            IntentFilter(ACTION_INSTALL_RESULT),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    override fun canInstallPackages(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            appContext.packageManager.canRequestPackageInstalls()
        } else {
            true
        }

    override suspend fun install(write: suspend (OutputStream) -> Unit) {
        val installer = appContext.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        val sessionId = installer.createSession(params)
        val session = installer.openSession(sessionId)
        try {
            val output = session.openWrite("base.apk", 0, -1)
            try {
                write(output)
                session.fsync(output)
            } finally {
                output.close()
            }
            session.commit(createIntentSender(sessionId))
        } catch (e: Exception) {
            runCatching { session.abandon() }
            throw e
        } finally {
            runCatching { session.close() }
        }
    }

    private fun createIntentSender(sessionId: Int): IntentSender {
        val intent = Intent(ACTION_INSTALL_RESULT).setPackage(appContext.packageName)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        val pending = PendingIntent.getBroadcast(appContext, sessionId, intent, flags)
        return pending.intentSender
    }

    private companion object {
        const val ACTION_INSTALL_RESULT = "com.homeflix.app.INSTALL_RESULT"
    }
}
```

- [ ] **Step 4: Wire it into `AppContainer`**

Replace `android-app/app/src/main/kotlin/com/homeflix/app/di/AppContainer.kt` with:

```kotlin
package com.homeflix.app.di

import android.content.Context
import com.homeflix.app.data.AuthService
import com.homeflix.app.data.DataStoreSessionRepository
import com.homeflix.app.data.DataStoreSettingsRepository
import com.homeflix.app.data.HttpUpdateRepository
import com.homeflix.app.data.JellyfinAuthService
import com.homeflix.app.data.JellyfinMovieRepository
import com.homeflix.app.data.JellyfinProvider
import com.homeflix.app.data.MovieRepository
import com.homeflix.app.data.PackageInstallerUpdateInstaller
import com.homeflix.app.data.SessionRepository
import com.homeflix.app.data.SettingsRepository
import com.homeflix.app.data.UpdateInstaller
import com.homeflix.app.data.UpdateRepository
import com.homeflix.app.data.dataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    val applicationScope: CoroutineScope by lazy {
        CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }

    val settingsRepository: SettingsRepository by lazy {
        DataStoreSettingsRepository(appContext.dataStore)
    }

    val sessionRepository: SessionRepository by lazy {
        DataStoreSessionRepository(appContext.dataStore)
    }

    val jellyfinProvider: JellyfinProvider by lazy {
        JellyfinProvider(appContext)
    }

    val authService: AuthService by lazy {
        JellyfinAuthService(jellyfinProvider)
    }

    val movieRepository: MovieRepository by lazy {
        JellyfinMovieRepository(jellyfinProvider, settingsRepository, sessionRepository)
    }

    val updateRepository: UpdateRepository by lazy {
        HttpUpdateRepository()
    }

    val updateInstaller: UpdateInstaller by lazy {
        PackageInstallerUpdateInstaller(appContext)
    }
}
```

> Note: `ContextCompat.registerReceiver` comes from `androidx.core:core-ktx`, already a dependency (the `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` in the merged manifest confirms it is present).

- [ ] **Step 5: Verify it compiles and unit tests still pass**

Run (from `android-app/`): `./gradlew :app:testDebugUnitTest`
Expected: `BUILD SUCCESSFUL` (the new installer has no JVM test; the fake is exercised by Task 8).

- [ ] **Step 6: Commit**

```bash
git add android-app/app/src/main/kotlin/com/homeflix/app/data/UpdateInstaller.kt android-app/app/src/main/kotlin/com/homeflix/app/data/PackageInstallerUpdateInstaller.kt android-app/app/src/main/AndroidManifest.xml android-app/app/src/main/kotlin/com/homeflix/app/di/AppContainer.kt android-app/app/src/test/kotlin/com/homeflix/app/TestDoubles.kt
git commit -m "feat(android): add package-installer update installer"
```

---

## Task 7: `skippedVersionCode` in `SettingsRepository`

**Files:**
- Modify: `android-app/app/src/main/kotlin/com/homeflix/app/data/SettingsRepository.kt`
- Modify: `android-app/app/src/test/kotlin/com/homeflix/app/TestDoubles.kt`
- Modify: `android-app/app/src/androidTest/kotlin/com/homeflix/app/TestDoubles.kt`

**Interfaces:**
- Consumes: nothing.
- Produces: `SettingsRepository.skippedVersionCode: Flow<Int?>` and `suspend fun setSkippedVersionCode(code: Int)`; `FakeSettingsRepository(initialUrl: String? = null, initialSkippedVersion: Int? = null)`.

- [ ] **Step 1: Extend the interface and the DataStore implementation**

Replace `android-app/app/src/main/kotlin/com/homeflix/app/data/SettingsRepository.kt` with:

```kotlin
package com.homeflix.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "home-flix-settings")

interface SettingsRepository {
    val serverUrl: Flow<String?>

    suspend fun setServerUrl(url: String)

    /** The most recent release the user declined to install, if any. */
    val skippedVersionCode: Flow<Int?>

    suspend fun setSkippedVersionCode(code: Int)
}

class DataStoreSettingsRepository(private val dataStore: DataStore<Preferences>) : SettingsRepository {

    override val serverUrl: Flow<String?> = dataStore.data.map { preferences -> preferences[SERVER_URL] }

    override suspend fun setServerUrl(url: String) {
        dataStore.edit { preferences -> preferences[SERVER_URL] = url }
    }

    override val skippedVersionCode: Flow<Int?> =
        dataStore.data.map { preferences -> preferences[SKIPPED_VERSION_CODE] }

    override suspend fun setSkippedVersionCode(code: Int) {
        dataStore.edit { preferences -> preferences[SKIPPED_VERSION_CODE] = code }
    }

    companion object {
        private val SERVER_URL = stringPreferencesKey("server_url")
        private val SKIPPED_VERSION_CODE = intPreferencesKey("skipped_version_code")
    }
}
```

- [ ] **Step 2: Update the JVM test fake**

In `android-app/app/src/test/kotlin/com/homeflix/app/TestDoubles.kt`, replace `FakeSettingsRepository` with:

```kotlin
class FakeSettingsRepository(
    initialUrl: String? = null,
    initialSkippedVersion: Int? = null
) : SettingsRepository {
    private val _serverUrl = MutableStateFlow(initialUrl)
    override val serverUrl: Flow<String?> = _serverUrl

    override suspend fun setServerUrl(url: String) {
        _serverUrl.value = url
    }

    private val _skippedVersionCode = MutableStateFlow(initialSkippedVersion)
    override val skippedVersionCode: Flow<Int?> = _skippedVersionCode

    override suspend fun setSkippedVersionCode(code: Int) {
        _skippedVersionCode.value = code
    }
}
```

- [ ] **Step 3: Update the instrumented test fake**

In `android-app/app/src/androidTest/kotlin/com/homeflix/app/TestDoubles.kt`, replace `FakeSettingsRepository` with:

```kotlin
class FakeSettingsRepository(
    initialUrl: String? = "http://host/api",
    initialSkippedVersion: Int? = null
) : SettingsRepository {
    private val _serverUrl = MutableStateFlow(initialUrl)
    override val serverUrl: Flow<String?> = _serverUrl
    override suspend fun setServerUrl(url: String) {
        _serverUrl.value = url
    }

    private val _skippedVersionCode = MutableStateFlow(initialSkippedVersion)
    override val skippedVersionCode: Flow<Int?> = _skippedVersionCode
    override suspend fun setSkippedVersionCode(code: Int) {
        _skippedVersionCode.value = code
    }
}
```

- [ ] **Step 4: Verify all existing tests still pass**

Run (from `android-app/`): `./gradlew :app:testDebugUnitTest`
Expected: `BUILD SUCCESSFUL` — the default `initialUrl = null` preserves `ServerUrlViewModelTest` behavior.

- [ ] **Step 5: Commit**

```bash
git add android-app/app/src/main/kotlin/com/homeflix/app/data/SettingsRepository.kt android-app/app/src/test/kotlin/com/homeflix/app/TestDoubles.kt android-app/app/src/androidTest/kotlin/com/homeflix/app/TestDoubles.kt
git commit -m "feat(android): remember declined update version"
```

---

## Task 8: `UpdateViewModel`

**Files:**
- Create: `android-app/app/src/main/kotlin/com/homeflix/app/ui/screens/UpdateViewModel.kt`
- Modify: `android-app/app/src/test/kotlin/com/homeflix/app/TestDoubles.kt`
- Test: `android-app/app/src/test/kotlin/com/homeflix/app/UpdateViewModelTest.kt`

**Interfaces:**
- Consumes: `SettingsRepository` (Task 7), `UpdateRepository` (Task 5), `UpdateInstaller` / `InstallResult` (Task 6), `UpdateManifest` (Task 3), `updateOrigin` / `joinUrl` (Task 4), `BuildConfig`.
- Produces:
  - `class UpdateViewModel(settingsRepository, updateRepository, updateInstaller, currentVersionCode: Int, currentVersionName: String, sdkInt: Int)`
  - `UpdateViewModel.CheckState` = `Idle | Checking | UpToDate | Available(manifest) | Error(message)`
  - `UpdateViewModel.InstallState` = `Idle | Downloading(downloaded, total) | Verifying | Installing | Installed | Failed(message)`
  - `checkState`, `installState`, `promptEvents`, `canInstallPackages`, `currentVersionLabel`
  - `check()`, `checkOnLaunch()`, `update(manifest)`, `dismiss(manifest)`, `dismissInstallResult()`, `refreshInstallPermission()`
  - `UpdateViewModel.shouldPrompt(manifest, skippedVersionCode): Boolean`, `UpdateViewModel.Factory`

- [ ] **Step 1: Add the repository fake**

Append to `android-app/app/src/test/kotlin/com/homeflix/app/TestDoubles.kt` (add imports `com.homeflix.app.data.UpdateManifest`, `com.homeflix.app.data.UpdateRepository`, `java.io.OutputStream`):

```kotlin
fun updateManifest(
    versionCode: Int = 2,
    versionName: String = "0.2.0",
    minSdk: Int = 24,
    apkUrl: String = "/updates/android/home-flix-$versionName.apk",
    sha256: String = "unused",
    size: Long = 0L,
    releaseNotes: String? = null
): UpdateManifest = UpdateManifest(
    schemaVersion = 1,
    versionCode = versionCode,
    versionName = versionName,
    minSdk = minSdk,
    apkUrl = apkUrl,
    sha256 = sha256,
    size = size,
    releaseNotes = releaseNotes
)

class FakeUpdateRepository : UpdateRepository {
    var manifest: UpdateManifest = updateManifest()
    var manifestError: Exception? = null
    var downloadError: Exception? = null
    var downloadedBytes: ByteArray = ByteArray(0)
    var manifestCalls: Int = 0
    var manifestOrigins = mutableListOf<String>()
    var downloadUrls = mutableListOf<String>()

    override suspend fun fetchManifest(origin: String): UpdateManifest {
        manifestCalls += 1
        manifestOrigins.add(origin)
        manifestError?.let { throw it }
        return manifest
    }

    override suspend fun download(
        apkUrl: String,
        size: Long,
        onProgress: (downloaded: Long, total: Long) -> Unit,
        sink: OutputStream
    ): String {
        downloadUrls.add(apkUrl)
        downloadError?.let { throw it }
        sink.write(downloadedBytes)
        onProgress(downloadedBytes.size.toLong(), size)
        return sha256Hex(downloadedBytes)
    }
}
```

- [ ] **Step 2: Write the failing tests**

Create `android-app/app/src/test/kotlin/com/homeflix/app/UpdateViewModelTest.kt`:

```kotlin
@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package com.homeflix.app

import com.homeflix.app.data.InstallResult
import com.homeflix.app.ui.screens.UpdateViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class UpdateViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun viewModel(
        settings: FakeSettingsRepository = FakeSettingsRepository(initialUrl = "http://host/api"),
        repo: FakeUpdateRepository = FakeUpdateRepository(),
        installer: FakeUpdateInstaller = FakeUpdateInstaller(),
        versionCode: Int = 1,
        sdkInt: Int = 33
    ) = UpdateViewModel(settings, repo, installer, versionCode, "0.1.0", sdkInt)

    @Test
    fun check_newerVersion_setsAvailable() = runTest(mainDispatcherRule.testDispatcher) {
        val repo = FakeUpdateRepository().apply { manifest = updateManifest(versionCode = 2) }
        val vm = viewModel(repo = repo)

        vm.check()
        advanceUntilIdle()

        assertEquals(UpdateViewModel.CheckState.Available(repo.manifest), vm.checkState.value)
        assertEquals("http://host", repo.manifestOrigins.single())
    }

    @Test
    fun check_sameVersion_setsUpToDate() = runTest(mainDispatcherRule.testDispatcher) {
        val repo = FakeUpdateRepository().apply { manifest = updateManifest(versionCode = 1) }
        val vm = viewModel(repo = repo)

        vm.check()
        advanceUntilIdle()

        assertEquals(UpdateViewModel.CheckState.UpToDate, vm.checkState.value)
    }

    @Test
    fun check_olderVersion_setsUpToDate() = runTest(mainDispatcherRule.testDispatcher) {
        val repo = FakeUpdateRepository().apply { manifest = updateManifest(versionCode = 1) }
        val vm = viewModel(repo = repo, versionCode = 5)

        vm.check()
        advanceUntilIdle()

        assertEquals(UpdateViewModel.CheckState.UpToDate, vm.checkState.value)
    }

    @Test
    fun check_belowMinSdk_setsError() = runTest(mainDispatcherRule.testDispatcher) {
        val repo = FakeUpdateRepository().apply { manifest = updateManifest(versionCode = 2, minSdk = 30) }
        val vm = viewModel(repo = repo, sdkInt = 24)

        vm.check()
        advanceUntilIdle()

        assertTrue(vm.checkState.value is UpdateViewModel.CheckState.Error)
    }

    @Test
    fun check_manualFailure_setsError() = runTest(mainDispatcherRule.testDispatcher) {
        val repo = FakeUpdateRepository().apply { manifestError = RuntimeException("boom") }
        val vm = viewModel(repo = repo)

        vm.check()
        advanceUntilIdle()

        assertTrue(vm.checkState.value is UpdateViewModel.CheckState.Error)
    }

    @Test
    fun checkOnLaunch_failure_staysIdle() = runTest(mainDispatcherRule.testDispatcher) {
        val repo = FakeUpdateRepository().apply { manifestError = RuntimeException("boom") }
        val vm = viewModel(repo = repo)

        vm.checkOnLaunch()
        advanceUntilIdle()

        assertEquals(UpdateViewModel.CheckState.Idle, vm.checkState.value)
    }

    @Test
    fun checkOnLaunch_newerVersion_emitsPrompt() = runTest(mainDispatcherRule.testDispatcher) {
        val repo = FakeUpdateRepository().apply {
            manifest = updateManifest(versionCode = 2, releaseNotes = "Notes")
        }
        val vm = viewModel(repo = repo)

        vm.checkOnLaunch()
        advanceUntilIdle()

        assertEquals(repo.manifest, vm.promptEvents.first())
    }

    @Test
    fun check_noServerUrl_manualSetsError() = runTest(mainDispatcherRule.testDispatcher) {
        val vm = viewModel(settings = FakeSettingsRepository(initialUrl = null))

        vm.check()
        advanceUntilIdle()

        val state = vm.checkState.value
        assertTrue(state is UpdateViewModel.CheckState.Error)
        assertTrue((state as UpdateViewModel.CheckState.Error).message.contains("server"))
    }

    @Test
    fun shouldPrompt_isFalseForTheSkippedVersion() {
        assertFalse(UpdateViewModel.shouldPrompt(updateManifest(versionCode = 3), skippedVersionCode = 3))
        assertTrue(UpdateViewModel.shouldPrompt(updateManifest(versionCode = 4), skippedVersionCode = 3))
        assertTrue(UpdateViewModel.shouldPrompt(updateManifest(versionCode = 3), skippedVersionCode = null))
    }

    @Test
    fun update_success_finishesInstalled() = runTest(mainDispatcherRule.testDispatcher) {
        val bytes = "hello".toByteArray()
        val repo = FakeUpdateRepository().apply {
            manifest = updateManifest(versionCode = 2, size = bytes.size.toLong(), sha256 = sha256Hex(bytes))
            downloadedBytes = bytes
        }
        val installer = FakeUpdateInstaller()
        val vm = viewModel(repo = repo, installer = installer)

        vm.update(repo.manifest)
        advanceUntilIdle()

        assertEquals(UpdateViewModel.InstallState.Installed, vm.installState.value)
        assertTrue(installer.committed)
        assertEquals("http://host/updates/android/home-flix-0.2.0.apk", repo.downloadUrls.single())
    }

    @Test
    fun update_checksumMismatch_failsWithoutCommitting() = runTest(mainDispatcherRule.testDispatcher) {
        val bytes = "hello".toByteArray()
        val repo = FakeUpdateRepository().apply {
            manifest = updateManifest(versionCode = 2, size = bytes.size.toLong(), sha256 = "deadbeef")
            downloadedBytes = bytes
        }
        val installer = FakeUpdateInstaller()
        val vm = viewModel(repo = repo, installer = installer)

        // The advertised digest does not match the downloaded bytes.
        vm.update(repo.manifest)
        advanceUntilIdle()

        assertTrue(vm.installState.value is UpdateViewModel.InstallState.Failed)
        assertFalse(installer.committed)
    }

    @Test
    fun update_downloadError_setsFailed() = runTest(mainDispatcherRule.testDispatcher) {
        val repo = FakeUpdateRepository().apply { downloadError = RuntimeException("boom") }
        val installer = FakeUpdateInstaller()
        val vm = viewModel(repo = repo, installer = installer)

        vm.update(repo.manifest)
        advanceUntilIdle()

        assertTrue(vm.installState.value is UpdateViewModel.InstallState.Failed)
        assertFalse(installer.committed)
    }

    @Test
    fun update_cancelled_returnsToIdle() = runTest(mainDispatcherRule.testDispatcher) {
        val bytes = "hello".toByteArray()
        val repo = FakeUpdateRepository().apply {
            manifest = updateManifest(versionCode = 2, size = bytes.size.toLong(), sha256 = sha256Hex(bytes))
            downloadedBytes = bytes
        }
        val installer = FakeUpdateInstaller().apply { result = InstallResult.Cancelled }
        val vm = viewModel(repo = repo, installer = installer)

        vm.update(repo.manifest)
        advanceUntilIdle()

        assertEquals(UpdateViewModel.InstallState.Idle, vm.installState.value)
    }

    @Test
    fun dismiss_remembersTheVersion() = runTest(mainDispatcherRule.testDispatcher) {
        val settings = FakeSettingsRepository(initialUrl = "http://host/api")
        val vm = viewModel(settings = settings)

        vm.dismiss(updateManifest(versionCode = 7))
        advanceUntilIdle()

        assertEquals(7, settings.skippedVersionCode.first())
    }
}
```

- [ ] **Step 3: Run the tests to verify they fail**

Run (from `android-app/`): `./gradlew :app:testDebugUnitTest --tests "com.homeflix.app.UpdateViewModelTest"`
Expected: FAIL — `UpdateViewModel` is unresolved.

- [ ] **Step 4: Implement the ViewModel**

Create `android-app/app/src/main/kotlin/com/homeflix/app/ui/screens/UpdateViewModel.kt`:

```kotlin
package com.homeflix.app.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.homeflix.app.BuildConfig
import com.homeflix.app.HomeFlixApplication
import com.homeflix.app.data.InstallResult
import com.homeflix.app.data.SettingsRepository
import com.homeflix.app.data.UpdateInstaller
import com.homeflix.app.data.UpdateManifest
import com.homeflix.app.data.UpdateManifestException
import com.homeflix.app.data.UpdateRepository
import com.homeflix.app.data.joinUrl
import com.homeflix.app.data.updateOrigin
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/** Raises when a downloaded APK does not match the manifest's digest. */
private class UpdateVerificationException : Exception("digest mismatch")

class UpdateViewModel(
    private val settingsRepository: SettingsRepository,
    private val updateRepository: UpdateRepository,
    private val updateInstaller: UpdateInstaller,
    private val currentVersionCode: Int,
    currentVersionName: String,
    private val sdkInt: Int
) : ViewModel() {

    sealed interface CheckState {
        data object Idle : CheckState
        data object Checking : CheckState
        data object UpToDate : CheckState
        data class Available(val manifest: UpdateManifest) : CheckState
        data class Error(val message: String) : CheckState
    }

    sealed interface InstallState {
        data object Idle : InstallState
        data class Downloading(val downloaded: Long, val total: Long) : InstallState
        data object Verifying : InstallState
        data object Installing : InstallState
        data object Installed : InstallState
        data class Failed(val message: String) : InstallState
    }

    val currentVersionLabel: String = "$currentVersionName ($currentVersionCode)"

    private val _checkState = MutableStateFlow<CheckState>(CheckState.Idle)
    val checkState: StateFlow<CheckState> = _checkState.asStateFlow()

    private val _installState = MutableStateFlow<InstallState>(InstallState.Idle)
    val installState: StateFlow<InstallState> = _installState.asStateFlow()

    private val _canInstallPackages = MutableStateFlow(updateInstaller.canInstallPackages())
    val canInstallPackages: StateFlow<Boolean> = _canInstallPackages.asStateFlow()

    private val _promptEvents = Channel<UpdateManifest>(Channel.BUFFERED)
    val promptEvents: Flow<UpdateManifest> = _promptEvents.receiveAsFlow()

    private var checking = false

    init {
        viewModelScope.launch {
            updateInstaller.results.collect { result ->
                _installState.value = when (result) {
                    InstallResult.Success -> InstallState.Installed
                    InstallResult.Cancelled -> InstallState.Idle
                    is InstallResult.Failed -> InstallState.Failed(result.reason)
                }
            }
        }
    }

    /** Manual check: errors are surfaced. */
    fun check() = startCheck(silent = false, prompt = false)

    /** Launch-time check: silent on failure; prompts when a newer release exists. */
    fun checkOnLaunch() = startCheck(silent = true, prompt = true)

    fun refreshInstallPermission() {
        _canInstallPackages.value = updateInstaller.canInstallPackages()
    }

    fun dismiss(manifest: UpdateManifest) {
        viewModelScope.launch { settingsRepository.setSkippedVersionCode(manifest.versionCode) }
    }

    fun dismissInstallResult() {
        _installState.value = InstallState.Idle
    }

    fun update(manifest: UpdateManifest) {
        val current = _installState.value
        if (current is InstallState.Downloading || current is InstallState.Installing) return
        viewModelScope.launch {
            _installState.value = InstallState.Downloading(0, manifest.size)
            try {
                val serverUrl = settingsRepository.serverUrl.first().orEmpty()
                val apkUrl = joinUrl(updateOrigin(serverUrl), manifest.apkUrl)
                updateInstaller.install { sink ->
                    val digest = updateRepository.download(apkUrl, manifest.size, { done, total ->
                        _installState.value = InstallState.Downloading(done, total)
                    }, sink)
                    _installState.value = InstallState.Verifying
                    if (!digest.equals(manifest.sha256, ignoreCase = true)) {
                        throw UpdateVerificationException()
                    }
                    _installState.value = InstallState.Installing
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: UpdateVerificationException) {
                _installState.value = InstallState.Failed("The download was corrupted. Try again.")
            } catch (e: Exception) {
                _installState.value = InstallState.Failed("Could not download the update.")
            }
        }
    }

    private fun startCheck(silent: Boolean, prompt: Boolean) {
        if (checking) return
        checking = true
        viewModelScope.launch {
            if (!silent) _checkState.value = CheckState.Checking
            try {
                val serverUrl = settingsRepository.serverUrl.first()
                if (serverUrl.isNullOrBlank()) {
                    if (!silent) _checkState.value = CheckState.Error("Set a server URL first.")
                    return@launch
                }
                val manifest = updateRepository.fetchManifest(updateOrigin(serverUrl))
                when {
                    manifest.versionCode <= currentVersionCode -> {
                        if (!silent) _checkState.value = CheckState.UpToDate
                    }

                    manifest.minSdk > sdkInt -> {
                        if (!silent) {
                            _checkState.value =
                                CheckState.Error("This update needs a newer Android version.")
                        }
                    }

                    else -> {
                        _checkState.value = CheckState.Available(manifest)
                        val skipped = settingsRepository.skippedVersionCode.first()
                        if (prompt && shouldPrompt(manifest, skipped)) {
                            _promptEvents.send(manifest)
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: UpdateManifestException) {
                if (!silent) _checkState.value = CheckState.Error("Could not check for updates.")
            } catch (e: Exception) {
                if (!silent) _checkState.value = CheckState.Error("Could not check for updates.")
            } finally {
                checking = false
            }
        }
    }

    companion object {
        /** A version is worth prompting for unless the user already declined it. */
        fun shouldPrompt(manifest: UpdateManifest, skippedVersionCode: Int?): Boolean =
            manifest.versionCode != skippedVersionCode

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application =
                    this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as HomeFlixApplication
                UpdateViewModel(
                    settingsRepository = application.container.settingsRepository,
                    updateRepository = application.container.updateRepository,
                    updateInstaller = application.container.updateInstaller,
                    currentVersionCode = BuildConfig.VERSION_CODE,
                    currentVersionName = BuildConfig.VERSION_NAME,
                    sdkInt = android.os.Build.VERSION.SDK_INT
                )
            }
        }
    }
}
```

- [ ] **Step 5: Run the tests to verify they pass**

Run (from `android-app/`): `./gradlew :app:testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit**

```bash
git add android-app/app/src/main/kotlin/com/homeflix/app/ui/screens/UpdateViewModel.kt android-app/app/src/test/kotlin/com/homeflix/app/UpdateViewModelTest.kt android-app/app/src/test/kotlin/com/homeflix/app/TestDoubles.kt
git commit -m "feat(android): add update view model"
```

---

## Task 9: Settings screen, navigation, dialogs, and top-bar gears

**Files:**
- Create: `android-app/app/src/main/kotlin/com/homeflix/app/ui/screens/SettingsScreen.kt`
- Create: `android-app/app/src/main/kotlin/com/homeflix/app/ui/screens/UpdateDialogs.kt`
- Modify: `android-app/app/src/main/kotlin/com/homeflix/app/navigation/Routes.kt`
- Modify: `android-app/app/src/main/kotlin/com/homeflix/app/navigation/AppNavHost.kt`
- Modify: `android-app/app/src/main/kotlin/com/homeflix/app/ui/screens/HomeScreen.kt`
- Modify: `android-app/app/src/main/kotlin/com/homeflix/app/ui/screens/MoviesScreen.kt`
- Modify: `android-app/app/src/main/kotlin/com/homeflix/app/ui/screens/TvSeriesScreen.kt`
- Modify: `android-app/app/src/main/kotlin/com/homeflix/app/ui/screens/SearchScreen.kt`
- Modify: `android-app/app/src/main/kotlin/com/homeflix/app/MainActivity.kt`
- Modify: `android-app/app/src/androidTest/kotlin/com/homeflix/app/TestDoubles.kt`
- Test: `android-app/app/src/androidTest/kotlin/com/homeflix/app/SettingsScreenTest.kt`

**Interfaces:**
- Consumes: `UpdateViewModel` and its states (Task 8).
- Produces:
  - `Routes.SETTINGS = "settings"`
  - `@Composable fun SettingsScreen(onBack: () -> Unit, onLogout: () -> Unit, updateViewModel: UpdateViewModel, modifier: Modifier = Modifier)`
  - `@Composable fun UpdateAvailableDialog(manifest: UpdateManifest, onUpdate: () -> Unit, onLater: () -> Unit)`
  - `@Composable fun InstallProgressDialog(state: UpdateViewModel.InstallState, onDismiss: () -> Unit)`
  - `AppNavHost(..., updateViewModel: UpdateViewModel? = null)`
  - Screen signature change: `HomeScreen/MoviesScreen/TvSeriesScreen/SearchScreen` gain `onSettings: () -> Unit`.

> **Spec note:** the design mentions a `SettingsViewModel`, but the version label and all state come from the app-scoped `UpdateViewModel`, so a separate Settings ViewModel would only forward calls. This plan uses `UpdateViewModel` directly.

- [ ] **Step 1: Add the route**

In `android-app/app/src/main/kotlin/com/homeflix/app/navigation/Routes.kt`, add `SETTINGS`:

```kotlin
object Routes {
    const val SERVER = "server"
    const val LOGIN = "login"
    const val HOME = "home"
    const val MOVIES = "movies"
    const val MOVIE_DETAIL = "movie/{movieId}"
    const val PLAYER = "player/{movieId}"
    const val TV_SERIES = "tv"
    const val SERIES_DETAIL = "series/{seriesId}"
    const val SEARCH = "search"
    const val SETTINGS = "settings"

    const val ARG_MOVIE_ID = "movieId"
    const val ARG_SERIES_ID = "seriesId"

    fun movieDetail(movieId: String) = "movie/$movieId"

    fun player(movieId: String) = "player/$movieId"

    fun seriesDetail(seriesId: String) = "series/$seriesId"
}
```

- [ ] **Step 2: Write the Settings screen**

Create `android-app/app/src/main/kotlin/com/homeflix/app/ui/screens/SettingsScreen.kt`:

```kotlin
package com.homeflix.app.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onLogout: () -> Unit,
    updateViewModel: UpdateViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val checkState by updateViewModel.checkState.collectAsState()
    val canInstall by updateViewModel.canInstallPackages.collectAsState()

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        updateViewModel.refreshInstallPermission()
    }

    LaunchedEffect(Unit) {
        updateViewModel.refreshInstallPermission()
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                windowInsets = WindowInsets(0.dp),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            ListItem(
                headlineContent = { Text("Version") },
                supportingContent = { Text(updateViewModel.currentVersionLabel) }
            )
            HorizontalDivider()

            when (val state = checkState) {
                is UpdateViewModel.CheckState.Idle -> ListItem(
                    headlineContent = { Text("Check for updates") },
                    modifier = Modifier
                        .testTag("checkForUpdates")
                        .clickable { updateViewModel.check() }
                )

                is UpdateViewModel.CheckState.Checking -> ListItem(
                    headlineContent = { Text("Checking…") }
                )

                is UpdateViewModel.CheckState.UpToDate -> ListItem(
                    headlineContent = { Text("Up to date") },
                    trailingContent = {
                        TextButton(onClick = { updateViewModel.check() }) { Text("Check again") }
                    }
                )

                is UpdateViewModel.CheckState.Available -> ListItem(
                    headlineContent = { Text("Update available: ${state.manifest.versionName}") },
                    supportingContent = if (state.manifest.releaseNotes.isNullOrBlank()) {
                        null
                    } else {
                        { Text(state.manifest.releaseNotes) }
                    },
                    trailingContent = {
                        TextButton(onClick = { updateViewModel.update(state.manifest) }) {
                            Text("Update")
                        }
                    }
                )

                is UpdateViewModel.CheckState.Error -> ListItem(
                    headlineContent = { Text(state.message) },
                    trailingContent = {
                        TextButton(onClick = { updateViewModel.check() }) { Text("Retry") }
                    }
                )
            }

            if (!canInstall) {
                HorizontalDivider()
                ListItem(
                    headlineContent = { Text("Allow app installs") },
                    supportingContent = { Text("Needed to install updates from this app.") },
                    trailingContent = {
                        TextButton(onClick = {
                            permissionLauncher.launch(
                                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                                    .setData(Uri.parse("package:${context.packageName}"))
                            )
                        }) { Text("Allow") }
                    }
                )
            }

            HorizontalDivider()
            ListItem(
                headlineContent = { Text("Log out") },
                modifier = Modifier
                    .testTag("logout")
                    .clickable { onLogout() }
            )
        }
    }
}
```

- [ ] **Step 3: Write the dialogs**

Create `android-app/app/src/main/kotlin/com/homeflix/app/ui/screens/UpdateDialogs.kt`:

```kotlin
package com.homeflix.app.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.homeflix.app.data.UpdateManifest

@Composable
fun UpdateAvailableDialog(
    manifest: UpdateManifest,
    onUpdate: () -> Unit,
    onLater: () -> Unit
) {
    AlertDialog(
        modifier = Modifier.testTag("updateDialog"),
        onDismissRequest = onLater,
        title = { Text("Update available") },
        text = {
            Column {
                Text("Version ${manifest.versionName}")
                manifest.releaseNotes?.takeIf { it.isNotBlank() }?.let {
                    Text(it, modifier = Modifier.padding(top = 8.dp))
                }
            }
        },
        confirmButton = { TextButton(onClick = onUpdate) { Text("Update") } },
        dismissButton = { TextButton(onClick = onLater) { Text("Later") } }
    )
}

@Composable
fun InstallProgressDialog(
    state: UpdateViewModel.InstallState,
    onDismiss: () -> Unit
) {
    when (state) {
        is UpdateViewModel.InstallState.Idle -> Unit

        is UpdateViewModel.InstallState.Downloading -> AlertDialog(
            modifier = Modifier.testTag("installDialog"),
            onDismissRequest = {},
            title = { Text("Downloading update") },
            text = {
                Column {
                    Text("${state.downloaded / 1024} / ${state.total / 1024} KB")
                    if (state.total > 0) {
                        LinearProgressIndicator(
                            progress = { (state.downloaded.toFloat() / state.total).coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                        )
                    }
                }
            },
            confirmButton = {}
        )

        is UpdateViewModel.InstallState.Verifying,
        is UpdateViewModel.InstallState.Installing -> AlertDialog(
            modifier = Modifier.testTag("installDialog"),
            onDismissRequest = {},
            title = { Text("Installing update") },
            text = { Text("Confirm the install in the system dialog.") },
            confirmButton = {}
        )

        is UpdateViewModel.InstallState.Installed -> AlertDialog(
            modifier = Modifier.testTag("installDialog"),
            onDismissRequest = onDismiss,
            title = { Text("Update installed") },
            text = { Text("Reopen the app to use the new version.") },
            confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } }
        )

        is UpdateViewModel.InstallState.Failed -> AlertDialog(
            modifier = Modifier.testTag("installDialog"),
            onDismissRequest = onDismiss,
            title = { Text("Update failed") },
            text = { Text(state.message) },
            confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
        )
    }
}
```

- [ ] **Step 4: Replace the "Log out" text button with a gear on the four tab screens**

In each of `HomeScreen.kt`, `MoviesScreen.kt`, `TvSeriesScreen.kt`, and `SearchScreen.kt`, add `onSettings: () -> Unit` to the composable's parameters (right after `onLogout`), and replace the `actions` block inside `TopAppBar`. For example, in `HomeScreen.kt` the signature becomes:

```kotlin
fun HomeScreen(
    onItemClick: (String) -> Unit,
    onLogout: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = viewModel(factory = HomeViewModel.Factory)
)
```

and the `actions` block becomes:

```kotlin
                actions = {
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }
                }
```

Apply the same `actions` replacement in `MoviesScreen.kt`, `TvSeriesScreen.kt`, and `SearchScreen.kt` (each currently has `actions = { TextButton(onClick = onLogout) { Text("Log out") } }`), and add `onSettings: () -> Unit` to each signature after `onLogout`. Add imports as needed:

```kotlin
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
```

(`TextButton` may become unused in a file — remove its import if the compiler warns.)

- [ ] **Step 5: Wire navigation and dialogs in `AppNavHost`**

Replace `android-app/app/src/main/kotlin/com/homeflix/app/navigation/AppNavHost.kt` with:

```kotlin
package com.homeflix.app.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.homeflix.app.data.UpdateManifest
import com.homeflix.app.ui.SessionViewModel
import com.homeflix.app.ui.screens.HomeScreen
import com.homeflix.app.ui.screens.InstallProgressDialog
import com.homeflix.app.ui.screens.LoginScreen
import com.homeflix.app.ui.screens.LoginViewModel
import com.homeflix.app.ui.screens.MovieDetailScreen
import com.homeflix.app.ui.screens.MoviesScreen
import com.homeflix.app.ui.screens.PlayerScreen
import com.homeflix.app.ui.screens.SearchScreen
import com.homeflix.app.ui.screens.SeriesDetailScreen
import com.homeflix.app.ui.screens.ServerUrlScreen
import com.homeflix.app.ui.screens.ServerUrlViewModel
import com.homeflix.app.ui.screens.SettingsScreen
import com.homeflix.app.ui.screens.TvSeriesScreen
import com.homeflix.app.ui.screens.UpdateAvailableDialog
import com.homeflix.app.ui.screens.UpdateViewModel

@Composable
fun AppNavHost(
    sessionViewModel: SessionViewModel,
    modifier: Modifier = Modifier,
    serverUrlViewModel: ServerUrlViewModel? = null,
    loginViewModel: LoginViewModel? = null,
    updateViewModel: UpdateViewModel? = null
) {
    val navController = rememberNavController()

    LaunchedEffect(Unit) {
        sessionViewModel.logoutEvents.collect {
            navController.navigate(Routes.LOGIN) {
                popUpTo(Routes.HOME) { inclusive = true }
            }
        }
    }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    Column(modifier = modifier) {
        Box(modifier = Modifier.weight(1f)) {
            NavHost(
                navController = navController,
                startDestination = Routes.SERVER,
                modifier = Modifier.fillMaxSize()
            ) {
                composable(Routes.SERVER) {
                    val onSaved = {
                        navController.navigate(Routes.LOGIN) {
                            popUpTo(Routes.SERVER) { inclusive = true }
                        }
                    }
                    val vm = serverUrlViewModel
                    if (vm != null) {
                        ServerUrlScreen(onSaved = onSaved, viewModel = vm)
                    } else {
                        ServerUrlScreen(onSaved = onSaved)
                    }
                }

                composable(Routes.LOGIN) {
                    val onLogin = {
                        navController.navigate(Routes.HOME) {
                            popUpTo(Routes.LOGIN) { inclusive = true }
                        }
                    }
                    val vm = loginViewModel
                    if (vm != null) {
                        LoginScreen(onLogin = onLogin, viewModel = vm)
                    } else {
                        LoginScreen(onLogin = onLogin)
                    }
                }

                composable(Routes.HOME) {
                    HomeScreen(
                        onItemClick = { itemId -> navController.navigate(Routes.player(itemId)) },
                        onLogout = sessionViewModel::logout,
                        onSettings = { navController.navigate(Routes.SETTINGS) }
                    )
                }

                composable(Routes.MOVIES) {
                    MoviesScreen(
                        onMovieClick = { movieId -> navController.navigate(Routes.movieDetail(movieId)) },
                        onLogout = sessionViewModel::logout,
                        onSettings = { navController.navigate(Routes.SETTINGS) }
                    )
                }

                composable(Routes.TV_SERIES) {
                    TvSeriesScreen(
                        onSeriesClick = { seriesId -> navController.navigate(Routes.seriesDetail(seriesId)) },
                        onLogout = sessionViewModel::logout,
                        onSettings = { navController.navigate(Routes.SETTINGS) }
                    )
                }

                composable(Routes.SEARCH) {
                    SearchScreen(
                        onItemClick = { item ->
                            if (item.isSeries) {
                                navController.navigate(Routes.seriesDetail(item.id))
                            } else {
                                navController.navigate(Routes.movieDetail(item.id))
                            }
                        },
                        onLogout = sessionViewModel::logout,
                        onSettings = { navController.navigate(Routes.SETTINGS) }
                    )
                }

                composable(Routes.MOVIE_DETAIL) { backStackEntry ->
                    val movieId = backStackEntry.arguments?.getString(Routes.ARG_MOVIE_ID).orEmpty()
                    MovieDetailScreen(
                        onPlay = { navController.navigate(Routes.player(movieId)) },
                        onBack = { navController.popBackStack() },
                        onLogout = sessionViewModel::logout
                    )
                }

                composable(Routes.SERIES_DETAIL) { backStackEntry ->
                    val seriesId = backStackEntry.arguments?.getString(Routes.ARG_SERIES_ID).orEmpty()
                    SeriesDetailScreen(
                        onPlayEpisode = { episodeId -> navController.navigate(Routes.player(episodeId)) },
                        onBack = { navController.popBackStack() },
                        onLogout = sessionViewModel::logout
                    )
                }

                composable(
                    route = Routes.PLAYER,
                    arguments = listOf(navArgument(Routes.ARG_MOVIE_ID) { type = NavType.StringType })
                ) {
                    PlayerScreen(
                        onBack = { navController.popBackStack() },
                        onPlayEpisode = { episodeId ->
                            navController.navigate(Routes.player(episodeId)) {
                                popUpTo(Routes.PLAYER) { inclusive = true }
                            }
                        },
                        onLogout = sessionViewModel::logout
                    )
                }

                if (updateViewModel != null) {
                    composable(Routes.SETTINGS) {
                        SettingsScreen(
                            onBack = { navController.popBackStack() },
                            onLogout = sessionViewModel::logout,
                            updateViewModel = updateViewModel
                        )
                    }
                }
            }
        }

        if (currentRoute == Routes.HOME || currentRoute == Routes.MOVIES || currentRoute == Routes.TV_SERIES || currentRoute == Routes.SEARCH) {
            NavigationBar(windowInsets = WindowInsets(0.dp)) {
                NavigationBarItem(
                    selected = currentRoute == Routes.HOME,
                    onClick = {
                        navController.navigate(Routes.HOME) {
                            popUpTo(Routes.HOME) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    icon = { Icon(Icons.Filled.Home, contentDescription = "Home") },
                    label = { Text("Home") }
                )
                NavigationBarItem(
                    selected = currentRoute == Routes.MOVIES,
                    onClick = {
                        navController.navigate(Routes.MOVIES) {
                            popUpTo(Routes.HOME) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    icon = { Icon(Icons.Filled.Movie, contentDescription = "Movies") },
                    label = { Text("Movies") }
                )
                NavigationBarItem(
                    selected = currentRoute == Routes.TV_SERIES,
                    onClick = {
                        navController.navigate(Routes.TV_SERIES) {
                            popUpTo(Routes.HOME) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    icon = { Icon(Icons.Filled.Tv, contentDescription = "TV Shows") },
                    label = { Text("TV Shows") }
                )
                NavigationBarItem(
                    selected = currentRoute == Routes.SEARCH,
                    onClick = {
                        navController.navigate(Routes.SEARCH) {
                            popUpTo(Routes.HOME) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    icon = { Icon(Icons.Filled.Search, contentDescription = "Search") },
                    label = { Text("Search") }
                )
            }
        }
    }

    if (updateViewModel != null) {
        var prompt by remember { mutableStateOf<UpdateManifest?>(null) }
        LaunchedEffect(updateViewModel) {
            updateViewModel.checkOnLaunch()
        }
        LaunchedEffect(updateViewModel) {
            updateViewModel.promptEvents.collect { prompt = it }
        }
        val installState by updateViewModel.installState.collectAsState()

        prompt?.let { manifest ->
            UpdateAvailableDialog(
                manifest = manifest,
                onUpdate = {
                    prompt = null
                    updateViewModel.update(manifest)
                },
                onLater = {
                    prompt = null
                    updateViewModel.dismiss(manifest)
                }
            )
        }
        InstallProgressDialog(
            state = installState,
            onDismiss = { updateViewModel.dismissInstallResult() }
        )
    }
}
```

- [ ] **Step 6: Pass the ViewModel from `MainActivity`**

Replace `android-app/app/src/main/kotlin/com/homeflix/app/MainActivity.kt` with:

```kotlin
package com.homeflix.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.homeflix.app.navigation.AppNavHost
import com.homeflix.app.ui.SessionViewModel
import com.homeflix.app.ui.screens.UpdateViewModel
import com.homeflix.app.ui.theme.HomeFlixTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HomeFlixTheme {
                val sessionViewModel: SessionViewModel = viewModel(factory = SessionViewModel.Factory)
                val updateViewModel: UpdateViewModel = viewModel(factory = UpdateViewModel.Factory)
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    AppNavHost(
                        sessionViewModel = sessionViewModel,
                        updateViewModel = updateViewModel,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}
```

- [ ] **Step 7: Add the instrumented test doubles**

`test/` and `androidTest/` do not share sources, so append the fakes the Settings test needs to `android-app/app/src/androidTest/kotlin/com/homeflix/app/TestDoubles.kt` (add imports `com.homeflix.app.data.InstallResult`, `com.homeflix.app.data.UpdateInstaller`, `com.homeflix.app.data.UpdateManifest`, `com.homeflix.app.data.UpdateRepository`, `java.io.OutputStream`, `kotlinx.coroutines.channels.Channel`, `kotlinx.coroutines.flow.receiveAsFlow`):

```kotlin
fun updateManifest(
    versionCode: Int = 2,
    versionName: String = "0.2.0",
    releaseNotes: String? = null
): UpdateManifest = UpdateManifest(
    schemaVersion = 1,
    versionCode = versionCode,
    versionName = versionName,
    minSdk = 24,
    apkUrl = "/updates/android/home-flix-$versionName.apk",
    sha256 = "unused",
    size = 0L,
    releaseNotes = releaseNotes
)

class FakeUpdateRepository : UpdateRepository {
    var manifest: UpdateManifest = updateManifest()
    var manifestCalls: Int = 0

    override suspend fun fetchManifest(origin: String): UpdateManifest {
        manifestCalls += 1
        return manifest
    }

    override suspend fun download(
        apkUrl: String,
        size: Long,
        onProgress: (downloaded: Long, total: Long) -> Unit,
        sink: OutputStream
    ): String = "unused"
}

class FakeUpdateInstaller : UpdateInstaller {
    private val channel = Channel<InstallResult>(Channel.BUFFERED)
    override val results = channel.receiveAsFlow()
    var canInstall: Boolean = true

    override fun canInstallPackages(): Boolean = canInstall

    override suspend fun install(write: suspend (OutputStream) -> Unit) = Unit
}
```

- [ ] **Step 8: Write the instrumented Settings test**

Create `android-app/app/src/androidTest/kotlin/com/homeflix/app/SettingsScreenTest.kt`:

```kotlin
package com.homeflix.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.homeflix.app.ui.screens.SettingsScreen
import com.homeflix.app.ui.screens.UpdateViewModel
import com.homeflix.app.ui.theme.HomeFlixTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SettingsScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun viewModel(
        repo: FakeUpdateRepository = FakeUpdateRepository(),
        installer: FakeUpdateInstaller = FakeUpdateInstaller()
    ) = UpdateViewModel(
        settingsRepository = FakeSettingsRepository(initialUrl = "http://host/api"),
        updateRepository = repo,
        updateInstaller = installer,
        currentVersionCode = 1,
        currentVersionName = "0.1.0",
        sdkInt = 33
    )

    @Test
    fun showsVersionAndChecksForUpdates() {
        val repo = FakeUpdateRepository().apply { manifest = updateManifest(versionCode = 2, versionName = "0.2.0") }
        val vm = viewModel(repo = repo)

        composeRule.setContent {
            HomeFlixTheme { SettingsScreen(onBack = {}, onLogout = {}, updateViewModel = vm) }
        }

        composeRule.onNodeWithText("Version").assertIsDisplayed()
        composeRule.onNodeWithText("0.1.0 (1)").assertIsDisplayed()
        composeRule.onNodeWithTag("checkForUpdates").performClick()
        composeRule.waitUntil(timeoutMillis = 5000) {
            repo.manifestCalls > 0
        }
        composeRule.onNodeWithText("Update available: 0.2.0").assertIsDisplayed()
    }

    @Test
    fun showsPermissionRowWhenInstallsAreNotAllowed() {
        val vm = viewModel(installer = FakeUpdateInstaller().apply { canInstall = false })

        composeRule.setContent {
            HomeFlixTheme { SettingsScreen(onBack = {}, onLogout = {}, updateViewModel = vm) }
        }

        composeRule.onNodeWithText("Allow app installs").assertIsDisplayed()
    }

    @Test
    fun logoutIsInvoked() {
        var loggedOut = false
        val vm = viewModel()

        composeRule.setContent {
            HomeFlixTheme { SettingsScreen(onBack = {}, onLogout = { loggedOut = true }, updateViewModel = vm) }
        }

        composeRule.onNodeWithTag("logout").performClick()
        assertEquals(true, loggedOut)
    }
}
```

- [ ] **Step 9: Verify it compiles and unit tests pass**

Run (from `android-app/`): `./gradlew :app:testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`.

Run (from `android-app/`): `./gradlew :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`.

Instrumented tests need a device/emulator: **deferred** to the Windows PC (see Task 11 manual verification).

- [ ] **Step 10: Commit**

```bash
git add android-app/app/src/main/kotlin/com/homeflix/app/ui/screens/SettingsScreen.kt android-app/app/src/main/kotlin/com/homeflix/app/ui/screens/UpdateDialogs.kt android-app/app/src/main/kotlin/com/homeflix/app/navigation/Routes.kt android-app/app/src/main/kotlin/com/homeflix/app/navigation/AppNavHost.kt android-app/app/src/main/kotlin/com/homeflix/app/MainActivity.kt android-app/app/src/main/kotlin/com/homeflix/app/ui/screens/HomeScreen.kt android-app/app/src/main/kotlin/com/homeflix/app/ui/screens/MoviesScreen.kt android-app/app/src/main/kotlin/com/homeflix/app/ui/screens/TvSeriesScreen.kt android-app/app/src/main/kotlin/com/homeflix/app/ui/screens/SearchScreen.kt android-app/app/src/androidTest/kotlin/com/homeflix/app/TestDoubles.kt android-app/app/src/androidTest/kotlin/com/homeflix/app/SettingsScreenTest.kt
git commit -m "feat(android): settings screen, update dialogs, and gear navigation"
```

---

## Task 10: Publish script, release signing, and secret hygiene

**Files:**
- Create: `scripts/publish-android-release.sh`
- Create: `scripts/test-publish-android-release.sh`
- Modify: `.gitignore`
- Modify: `android-app/app/build.gradle.kts`

**Interfaces:**
- Consumes: `:app:printVersionInfo` (Task 2).
- Produces: a signed release APK plus `updates/android/manifest.json` on the Orange Pi.

- [ ] **Step 1: Add release signing to the app build**

In `android-app/app/build.gradle.kts`, add the import at the very top of the file:

```kotlin
import java.util.Properties
```

Add this above the `android {` block:

```kotlin
// Release signing. Secrets come from android-app/keystore.properties (gitignored)
// or environment variables for headless/CI builds. Absent => unsigned release.
val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
fun secret(key: String): String? =
    keystoreProperties.getProperty(key) ?: System.getenv(key)
```

Add a `signingConfigs` block at the start of `android {` (before `defaultConfig`):

```kotlin
    signingConfigs {
        create("release") {
            secret("storeFile")?.let { storeFile = rootProject.file(it) }
            storePassword = secret("storePassword")
            keyAlias = secret("keyAlias")
            keyPassword = secret("keyPassword")
        }
    }
```

Change the `release` build type to use it:

```kotlin
    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
```

- [ ] **Step 2: Ignore the secrets**

Append to `.gitignore`:

```
# Android release signing secrets
keystore.properties
*.jks
*.keystore

# Local staging dir used by scripts/publish-android-release.sh
.update-stage/
```

- [ ] **Step 3: Generate a release keystore (once, not committed)**

Run (from the repo root):

```bash
keytool -genkeypair -v \
  -keystore android-app/release.jks \
  -alias homeflix \
  -keyalg RSA -keysize 2048 -validity 10000
```

When prompted, set a store password; use the same value for the key password. Then create `android-app/keystore.properties` (gitignored):

```properties
storeFile=release.jks
storePassword=<the password you chose>
keyAlias=homeflix
keyPassword=<the password you chose>
```

**Back this file and `release.jks` up somewhere safe.** Losing them means future releases cannot update installed apps.

- [ ] **Step 4: Verify the signed release builds**

Run (from `android-app/`):

```bash
./gradlew clean :app:assembleRelease
```

Expected: `BUILD SUCCESSFUL`, with `app/build/outputs/apk/release/app-release.apk` produced.

If the Android SDK's `build-tools` are on `PATH` you can also confirm the signature:

```bash
"$ANDROID_HOME"/build-tools/*/apksigner verify --print-certs app/build/outputs/apk/release/app-release.apk
```

Expected: `Verified using v2 scheme` or equivalent. If `apksigner` is unavailable, defer this check.

- [ ] **Step 5: Write the publish script**

Create `scripts/publish-android-release.sh`:

```bash
#!/usr/bin/env bash
set -euo pipefail

# Publish a signed Android release to the Orange Pi so installed apps can
# update themselves. Builds the release APK here, writes
# updates/android/manifest.json, then rsyncs both into the directory Caddy
# serves. No Caddy reload is needed (bind mount).
#
# Usage:
#   scripts/publish-android-release.sh
#   NOTES=release-notes.md scripts/publish-android-release.sh
#   DRY_RUN=1 scripts/publish-android-release.sh
#
# Env vars:
#   SERVER       Orange Pi hostname (default orangepi3b.local)
#   SSH_USER     SSH user on the board (default dzhang)
#   REMOTE_DIR   Directory Caddy serves releases from
#                (default /mnt/ssd/web-frontend/updates/android)
#   ANDROID_DIR  Gradle project dir (default <repo>/android-app)
#   NOTES        Optional release-notes file
#   SKIP_BUILD   set to 1 to reuse the existing release APK
#   STAGE_DIR    Staging dir (default <repo>/.update-stage)
#   DRY_RUN      set to 1 to skip remote writes

SERVER="${SERVER:-orangepi3b.local}"
SSH_USER="${SSH_USER:-dzhang}"
REMOTE_DIR="${REMOTE_DIR:-/mnt/ssd/web-frontend/updates/android}"
DRY_RUN="${DRY_RUN:-0}"
SKIP_BUILD="${SKIP_BUILD:-0}"

usage() {
	cat <<'EOF'
Publish a signed Android release to the Orange Pi.

Env vars:
  SERVER       Orange Pi hostname (default orangepi3b.local)
  SSH_USER     SSH user on the board (default dzhang)
  REMOTE_DIR   Directory Caddy serves releases from
               (default /mnt/ssd/web-frontend/updates/android)
  ANDROID_DIR  Gradle project dir (default <repo>/android-app)
  NOTES        Optional release-notes file
  SKIP_BUILD   set to 1 to reuse the existing release APK
  STAGE_DIR    Staging dir (default <repo>/.update-stage)
  DRY_RUN      set to 1 to skip remote writes
EOF
}

case "${1:-}" in
-h | --help)
	usage
	exit 0
	;;
"") ;;
*)
	echo "ERROR: unknown argument '$1' (try --help)." >&2
	exit 2
	;;
esac

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ANDROID_DIR="${ANDROID_DIR:-$REPO_ROOT/android-app}"
STAGE_DIR="${STAGE_DIR:-$REPO_ROOT/.update-stage}"
APK_PATH="$ANDROID_DIR/app/build/outputs/apk/release/app-release.apk"
STAGE="$STAGE_DIR"
DEST="$SSH_USER@$SERVER:$REMOTE_DIR/"

for cmd in ssh rsync sha256sum python3; do
	command -v "$cmd" >/dev/null 2>&1 || {
		echo "ERROR: '$cmd' is not installed." >&2
		exit 1
	}
done

if [[ "$REMOTE_DIR" != /* ]]; then
	echo "ERROR: REMOTE_DIR must be an absolute path (got '$REMOTE_DIR')." >&2
	exit 1
fi
case "$REMOTE_DIR" in
"" | "/" | "/mnt" | "/mnt/ssd" | "/home" | "/root" | "/etc" | "/var" | "/usr")
	echo "ERROR: refusing to write into unsafe REMOTE_DIR '$REMOTE_DIR'." >&2
	exit 1
	;;
esac

if [[ "$SKIP_BUILD" != "1" ]]; then
	echo "== Building signed release APK =="
	(cd "$ANDROID_DIR" && ./gradlew clean :app:assembleRelease)
fi

[[ -f "$APK_PATH" ]] || {
	echo "ERROR: no APK at $APK_PATH (build failed, or SKIP_BUILD=1 too early)." >&2
	exit 1
}

version_info="$(cd "$ANDROID_DIR" && ./gradlew -q :app:printVersionInfo)"
read -r version_code version_name min_sdk <<<"$version_info"
[[ -n "$version_code" && -n "$version_name" && -n "$min_sdk" ]] || {
	echo "ERROR: could not read version info (got '$version_info')." >&2
	exit 1
}

apk_name="home-flix-$version_name.apk"
apk_url="/updates/android/$apk_name"

rm -rf "$STAGE"
mkdir -p "$STAGE"
cp "$APK_PATH" "$STAGE/$apk_name"
size="$(stat -c %s "$STAGE/$apk_name")"
sha="$(sha256sum "$STAGE/$apk_name" | awk '{print $1}')"

VERSION_CODE="$version_code" VERSION_NAME="$version_name" MIN_SDK="$min_sdk" \
	APK_URL="$apk_url" SHA256="$sha" SIZE="$size" NOTES="${NOTES:-}" \
	python3 - "$STAGE/manifest.json" <<'PY'
import json, os, sys

notes_path = os.environ.get("NOTES") or ""
notes = ""
if notes_path and os.path.exists(notes_path):
    with open(notes_path, encoding="utf-8") as handle:
        notes = handle.read().strip()

manifest = {
    "schemaVersion": 1,
    "versionCode": int(os.environ["VERSION_CODE"]),
    "versionName": os.environ["VERSION_NAME"],
    "minSdk": int(os.environ["MIN_SDK"]),
    "apkUrl": os.environ["APK_URL"],
    "sha256": os.environ["SHA256"],
    "size": int(os.environ["SIZE"]),
}
if notes:
    manifest["releaseNotes"] = notes

with open(sys.argv[1], "w", encoding="utf-8") as handle:
    json.dump(manifest, handle, indent=2)
    handle.write("\n")
PY

echo
echo "== Release $version_name ($version_code), minSdk $min_sdk =="
echo "   APK:      $apk_name ($size bytes)"
echo "   SHA-256:  $sha"

RSYNC_ARGS=(-avz)
if [[ "$DRY_RUN" == "1" ]]; then
	RSYNC_ARGS+=(--dry-run)
	echo "== Dry run: not writing to $DEST =="
fi

rsync "${RSYNC_ARGS[@]}" "$STAGE/" "$DEST"

if [[ "$DRY_RUN" == "1" ]]; then
	echo
	echo "Dry run complete. Re-run without DRY_RUN=1 to publish."
else
	echo
	echo "Published. Check for updates in the app, or fetch:"
	echo "  http://$SERVER/updates/android/manifest.json"
fi
```

- [ ] **Step 6: Write the script smoke test**

Create `scripts/test-publish-android-release.sh`:

```bash
#!/usr/bin/env bash
set -euo pipefail

# Smoke test for publish-android-release.sh. Skips the Gradle build (SKIP_BUILD=1),
# uses a stub ./gradlew for version info, and replaces rsync with a shim that
# copies locally instead of connecting to the Pi. Run from anywhere:
#   scripts/test-publish-android-release.sh

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
publish="$script_dir/publish-android-release.sh"

work="$(mktemp -d "${TMPDIR:-/tmp}/publish-android-test.XXXXXX")"
trap 'rm -rf "$work"' EXIT

fails=0
expect_rc() {
	local desc="$1" want="$2" got="$3"
	if [[ "$want" == "$got" ]]; then
		echo "ok   - $desc"
	else
		echo "FAIL - $desc (want rc=$want, got rc=$got)"
		fails=$((fails + 1))
	fi
}
expect_contains() {
	local desc="$1" needle="$2" haystack="$3"
	if grep -qF "$needle" <<<"$haystack"; then
		echo "ok   - $desc"
	else
		echo "FAIL - $desc (missing '$needle')"
		fails=$((fails + 1))
	fi
}

# --- Guard rails (checked before any build, so these are fast) -------------
rc=0
"$publish" --bogus >/dev/null 2>&1 || rc=$?
expect_rc "unknown argument exits 2" 2 "$rc"

rc=0
REMOTE_DIR=relative "$publish" >/dev/null 2>&1 || rc=$?
expect_rc "relative REMOTE_DIR exits 1" 1 "$rc"

rc=0
REMOTE_DIR=/mnt/ssd "$publish" >/dev/null 2>&1 || rc=$?
expect_rc "unsafe REMOTE_DIR exits 1" 1 "$rc"

# --- Manifest generation with a fixture APK, stub gradlew, fake rsync ------
android_dir="$work/android-app"
mkdir -p "$android_dir/app/build/outputs/apk/release"
printf 'not-a-real-apk-but-fine-for-hashing' >"$android_dir/app/build/outputs/apk/release/app-release.apk"

cat >"$android_dir/gradlew" <<'EOF'
#!/usr/bin/env bash
for arg in "$@"; do
	if [[ "$arg" == ":app:printVersionInfo" ]]; then
		echo "2 0.2.0 24"
		exit 0
	fi
done
exit 0
EOF
chmod +x "$android_dir/gradlew"

shim="$work/bin"
mkdir -p "$shim"
cat >"$shim/rsync" <<'EOF'
#!/usr/bin/env bash
# Fake rsync: copy the staged files into the path after the colon.
args=("$@")
dest="${args[-1]}"
src="${args[-2]}"
local="${dest#*:}"
mkdir -p "$local"
cp -r "$src"* "$local/"
EOF
chmod +x "$shim/rsync"

stage="$work/stage"
remote="$work/remote"
mkdir -p "$remote"

rc=0
PATH="$shim:$PATH" SKIP_BUILD=1 ANDROID_DIR="$android_dir" STAGE_DIR="$stage" \
	SERVER=localhost SSH_USER=test REMOTE_DIR="$remote" \
	"$publish" >"$work/out.log" 2>&1 || rc=$?
expect_rc "publish with SKIP_BUILD exits 0" 0 "$rc"

manifest="$stage/manifest.json"
if [[ -f "$manifest" ]]; then
	echo "ok   - manifest.json written"
	body="$(cat "$manifest")"
	expect_contains "manifest has versionCode 2" '"versionCode": 2' "$body"
	expect_contains "manifest has versionName 0.2.0" '"versionName": "0.2.0"' "$body"
	expect_contains "manifest has minSdk 24" '"minSdk": 24' "$body"
	expect_contains "manifest points at the versioned APK" 'home-flix-0.2.0.apk' "$body"
	expect_contains "manifest carries a sha256" '"sha256"' "$body"
else
	echo "FAIL - manifest.json was not written to $stage"
	fails=$((fails + 1))
fi

if [[ -f "$remote/home-flix-0.2.0.apk" && -f "$remote/manifest.json" ]]; then
	echo "ok   - APK and manifest were synced"
else
	echo "FAIL - APK and manifest were not synced to $remote"
	fails=$((fails + 1))
fi

echo
if [[ "$fails" -eq 0 ]]; then
	echo "All checks passed."
else
	echo "$fails check(s) failed."
	exit 1
fi
```

> The default staging directory is `<repo>/.update-stage`; Step 2 adds it to `.gitignore`. The test overrides it with `STAGE_DIR`.

- [ ] **Step 7: Run the script test**

Run (from the repo root):

```bash
chmod +x scripts/publish-android-release.sh scripts/test-publish-android-release.sh
scripts/test-publish-android-release.sh
```

Expected: every line `ok - …`, ending with `All checks passed.`

- [ ] **Step 8: Commit**

```bash
git add scripts/publish-android-release.sh scripts/test-publish-android-release.sh .gitignore android-app/app/build.gradle.kts
git commit -m "feat: publish script and release signing for Android updates"
```

---

## Task 11: Documentation

**Files:**
- Modify: `docs/architecture.md`
- Modify: `docs/development.md`
- Modify: `docs/user-guide.md`
- Modify: `docs/project-plan.md`
- Modify: `README.md`

**Interfaces:**
- Consumes: everything above.
- Produces: no code.

- [ ] **Step 1: Document the subsystem in `docs/architecture.md`**

In §11 (API Endpoints) or §14 (Deployment Workflow), add a subsection, and add a row to the §2 component table. Use this text (place it under §14 as `### 14.1 Android in-app updates`):

```markdown
### 14.1 Android in-app updates

The Android app can check for and install its own updates over the LAN:

- **Host:** the Caddy container serves release artifacts from the same `/srv`
  mount as the web app, at `/updates/android/`. The route is explicit so the
  SPA fallback never rewrites it (see `docker/Caddyfile`).
- **Manifest:** `GET /updates/android/manifest.json` — `schemaVersion`,
  `versionCode`, `versionName`, `minSdk`, `apkUrl` (relative to the origin),
  `sha256`, `size`, and optional `releaseNotes`. The manifest revalidates;
  version-named APKs are cached immutably.
- **Client:** on launch (silently) and on demand from Settings, the app derives
  the origin from the stored server URL (stripping the `/api` base path),
  compares `versionCode`, and — with the user's confirmation — streams the APK
  into an Android `PackageInstaller` session, verifying size and SHA-256 before
  committing it to the system installer.
- **Publishing:** `scripts/publish-android-release.sh` builds a signed release,
  writes the manifest, and rsyncs both to `/mnt/ssd/web-frontend/updates/android/`.
```

- [ ] **Step 2: Document signing and publishing in `docs/development.md`**

Extend §6 (Android App Development) with:

```markdown
### 6.5 Release signing and publishing

Release APKs are signed with a single keystore so that in-app updates can
install over an existing app. Secrets live in `android-app/keystore.properties`
(gitignored); headless builds may instead set `storeFile`, `storePassword`,
`keyAlias`, and `keyPassword` as environment variables.

Create the keystore once (from the repo root):

```bash
keytool -genkeypair -v -keystore android-app/release.jks \
  -alias homeflix -keyalg RSA -keysize 2048 -validity 10000
```

Write `android-app/keystore.properties`:

```properties
storeFile=release.jks
storePassword=...
keyAlias=homeflix
keyPassword=...
```

Back up both files. Losing them means future releases can never update an
installed app.

Publish a release (bumps `versionCode`/`versionName` in `app/build.gradle.kts`
first):

```bash
scripts/publish-android-release.sh
NOTES=release-notes.md scripts/publish-android-release.sh
```

**One-time transition:** the app installed before this feature is debug-signed,
and Android refuses to install an update with a different signature. Once,
uninstall the debug build and install the first release-signed APK
(`adb install app/build/outputs/apk/release/app-release.apk`). Every later
release installs in-app.
```

- [ ] **Step 3: Document the user flow in `docs/user-guide.md`**

Add a section:

```markdown
## Updating the Android app

Open **Settings** (the gear in the top bar) and tap **Check for updates**.

- If a newer version exists, tap **Update**. The app downloads it, verifies it,
  then Android asks you to confirm the install.
- The first time, Android may ask you to allow installs from this app; the
  Settings screen has an **Allow** button that takes you there.
- Tap **Later** to skip a version; you will be offered the next newer one.
```

- [ ] **Step 4: Add a project-plan epic**

In `docs/project-plan.md`, under the Android module, add:

```markdown
### Epic — Android In-App Updates

- [ ] — Publish the manifest and APKs through Caddy (`/updates/android/`). *(Recommended, M)*
- [ ] — Check for updates on launch and from Settings. *(Recommended, M)*
- [ ] — Download, verify, and install via `PackageInstaller`. *(Recommended, L)*
- [ ] — Sign releases with a stable keystore and publish via script. *(Recommended, M)*
```

- [ ] **Step 5: Mention the script in `README.md`**

Under the components list or a short "Publishing" line, add:

```markdown
- Release the Android app: `scripts/publish-android-release.sh` (see [Development](docs/development.md)).
```

- [ ] **Step 6: Verify the full Android test suite and a debug build**

Run (from `android-app/`):

```bash
./gradlew :app:testDebugUnitTest :app:assembleDebug
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 7: Commit**

```bash
git add docs/architecture.md docs/development.md docs/user-guide.md docs/project-plan.md README.md
git commit -m "docs: document the Android in-app update flow"
```

---

## Self-Review Notes

- **Spec coverage:**
  - R1 update source / R9 release signing / R10 minimal footprint → Tasks 1, 2, 10.
  - R2 version detection (versionCode, `minSdk`) → Task 8 (`startCheck`).
  - R3 detection timing (launch + manual) → Task 8 (`check` / `checkOnLaunch`), Task 9 (Settings row, `checkOnLaunch` in `AppNavHost`).
  - R4 optional update + decline memory → Task 7 (`skippedVersionCode`), Task 8 (`shouldPrompt`, `dismiss`).
  - R5 verified download → Task 5 (digest during write), Task 8 (compare before commit).
  - R6 in-app install → Task 6 (`PackageInstallerUpdateInstaller`).
  - R7 permission handling → Task 6 (`canInstallPackages`), Task 9 (permission row + activity-result launcher).
  - R8 progress and outcomes → Task 8 (`InstallState`), Task 9 (`InstallProgressDialog`), Task 6 (status mapping incl. `Cancelled`).
  - §5.1 Caddy route → Task 1. §5.2 manifest → Tasks 3, 10. §5.3 publish script → Task 10. §6 data layer → Tasks 3–7. §7 UI → Task 9. §8 signing transition → Task 10. §9 error handling → Tasks 5, 6, 8. §10 testing → Tasks 3–5, 8, 9, 10. §11 docs → Task 11.
- **Placeholder scan:** no "TODO"/"TBD"/"similar to Task N"; every code step carries complete code.
- **Type consistency:** `UpdateManifest`, `UpdateManifestJson.parse`, `updateOrigin`, `joinUrl`, `HttpTransport`/`HttpBody`/`HttpException`, `UpdateRepository.download(...) -> String`, `InstallResult`, `UpdateInstaller.results`, `UpdateViewModel.CheckState`/`InstallState`, `SettingsRepository.skippedVersionCode`, and the fake field names (`manifestOrigins`, `downloadUrls`, `committed`, `writtenBytes`) are used consistently across tasks.

## Manual verification (deferred to the Windows PC)

1. `scripts/publish-android-release.sh` with `NOTES` → confirm `http://orangepi3b.local/updates/android/manifest.json` matches the built APK's `versionCode`.
2. Install an older release on the device, launch the app → the update prompt appears.
3. Tap **Update** → progress, then Android's install confirmation; confirm and verify the new version.
4. Tap **Later** → relaunch shows no prompt for that version; publish a higher `versionCode` → it prompts again.
5. Tamper with `sha256` in the published manifest → the app reports a corrupted download and installs nothing.
6. Revoke the "install unknown apps" permission → the Settings permission row appears and the deep link opens the right screen.
