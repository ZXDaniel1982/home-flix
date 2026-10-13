# Design — Android In-App Update ("Check for updates")

**Date:** 2026-10-11
**Status:** Accepted
**Scope:** New update subsystem: a release pipeline on the Orange Pi (Caddy) and an in-app
check → download → install flow in the Android app. No changes to the web frontend or Jellyfin.

## 1. Context

The Android app (`com.homeflix.app`) is sideloaded; today a new version reaches a device only by
building the APK on the Arch laptop, `scp`-ing it to the Windows PC, and running `adb install`
(`docs/development.md` §6.4, `docs/architecture.md` §14). The app has no "Check for updates"
capability at all.

Existing building blocks this design leans on:

- **Caddy** on the Orange Pi serves static files from `/srv` (bind-mounted from
  `/mnt/ssd/web-frontend`) with SPA fallback (`docker/Caddyfile`). It is already a LAN-only static
  file host.
- The app stores the Jellyfin base URL in DataStore, normalized to always end in `/api`
  (`ServerUrlViewModel.normalize`), e.g. `http://orangepi3b.local/api`.
- The app already permits cleartext HTTP (`android:usesCleartextTraffic="true"`).
- `scripts/deploy-web.sh` establishes the repo's deploy-script conventions (env overrides,
  `DRY_RUN`, unsafe-target guards).
- Release builds are currently **unsigned**; there is no signing config (`app/build.gradle.kts`).

Constraints from `AGENTS.md`: local home network only, no public IP, all server services in Docker,
no hardcoded addresses (use `orangepi3b.local` / `devserver.local`).

Android platform constraint: an update can only be installed over an existing app if both are
signed with the **same key**. Fully silent installation is not possible for a sideloaded,
non-system app — the user always confirms through the system installer.

## 2. Requirements

- **R1 — Update source.** The app fetches update metadata from the Orange Pi through the existing
  Caddy, at the origin derived from the configured server URL. No public internet, no Play Store.
- **R2 — Version detection.** The app knows its own version and only offers an update when the
  published `versionCode` is greater than the installed one and the device satisfies `minSdk`.
- **R3 — Detection timing.** The app checks silently on launch, and the user can also check
  manually from a Settings screen. Launch-time failures are silent; manual-check failures are
  shown with a retry.
- **R4 — Optional update.** The update is always declinable. Declining remembers the version so it
  is not re-prompted on every launch; a later newer release still prompts.
- **R5 — Verified download.** The app downloads the APK, verifying its size and SHA-256 before
  installing. A verification failure aborts the install.
- **R6 — In-app install.** The app hands the APK to Android's package installer from within the
  app (no browser, no external file manager). The user confirms through the system dialog.
- **R7 — Permission handling.** If the app may not install packages, the user is guided to grant
  "install unknown apps" for this app; no crash and no dead end.
- **R8 — Progress and outcomes.** Download progress, a verifying state, an installing state, and a
  clear success/failure outcome are shown; cancellation is not an error.
- **R9 — Release signing.** Distributed builds are release builds signed with a single, stable
  key kept out of version control; the manifest and APK are published by a repeatable script.
- **R10 — Minimal footprint.** No new third-party runtime dependencies; the publish step reuses the
  existing Caddy service and static-file mount.

## 3. Decisions

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Update host | Caddy on the Orange Pi, under the existing `/srv` mount | No new service; reuses the address the user already configured |
| Update origin | `serverUrl` with the trailing `/api` stripped | The app stores the Jellyfin base, not the plain origin |
| Release path | `/mnt/ssd/web-frontend/updates/android/`, served at `/updates/android/` | Inside the existing mount; explicitly routed so it bypasses SPA fallback |
| Manifest | `manifest.json` with `schemaVersion`, `versionCode`, `versionName`, `minSdk`, `apkUrl`, `sha256`, `size`, `releaseNotes` | Enough to decide, verify, and describe an update |
| `apkUrl` | Relative to the origin | Works for hostname or raw-IP entry |
| Manifest caching | `no-cache`; APKs named with version name **and** version code, cached immutably | New releases are seen immediately; every release URL is unique, so an immutable cache can never serve two different builds from one URL |
| Signing | One release keystore via gitignored `keystore.properties` (+ env overrides) | Required for update-over-install; headless-friendly |
| Self version | `BuildConfig.VERSION_CODE`/`VERSION_NAME` (`buildConfig = true`) | Clean, testable; no `PackageManager` reflection |
| Install handoff | `PackageInstaller.Session` committed after verification | Reports the real outcome; no APK persists to disk |
| Pending-user-action | Installer launches the system dialog itself | Keeps `Intent` out of the ViewModel; app is foreground |
| Update state | One app-scoped `UpdateViewModel` from `AppContainer` | Shared by launch-time and manual checks |
| Entry point | New Settings screen from a top-bar gear; logout moves there | Conventional, and gives Settings a reason beyond one row |
| Declined updates | `skippedVersionCode` in DataStore | Avoids nagging while still prompting for newer releases |
| HTTP client | `java.net.HttpURLConnection` | Zero new dependencies |

## 4. Behavior contract (shared)

1. On launch, once a server URL is configured, the app checks for an update silently. Any failure
   (network, parse, HTTP) produces no UI.
2. A manifest is ignored (treated as "no update") when parsing fails, `schemaVersion` is
   unsupported, required fields are missing, `versionCode <= installed`, or the device `sdk <
   minSdk`.
3. When a valid newer release exists and it is not the remembered `skippedVersionCode`, the app
   shows a prompt with the version and release notes.
4. Prompt actions: **Update** starts the download/install; **Later** dismisses and remembers the
   version.
5. Download: stream to the installer session, report progress, compute SHA-256 in the same pass.
   On completion, compare size and digest; on mismatch, abandon the session and report failure.
6. Install: only after verification, commit the session. The user confirms in the system dialog.
   `FAILURE_ABORTED` is treated as cancellation; other failures are reported.
7. If the app may not install packages, show the permission state with a deep link to
   `Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES`.
8. Manual checks surface errors with Retry; launch-time checks do not.
9. A check or install already in flight ignores further requests.

## 5. Server & release pipeline

### 5.1 Caddy route — `docker/Caddyfile`

Add an explicit route before the SPA fallback so `/updates/*` never serves `index.html`:

```caddyfile
handle /updates/* {
	root * /srv
	header /updates/android/manifest.json Cache-Control "no-cache"
	header /updates/android/*.apk Cache-Control "public, max-age=31536000, immutable"
	file_server
}
```

No new volume or container: `/srv` already maps to `/mnt/ssd/web-frontend`.

### 5.2 Manifest contract — `GET {origin}/updates/android/manifest.json`

```json
{
  "schemaVersion": 1,
  "versionCode": 2,
  "versionName": "0.2.0",
  "minSdk": 24,
  "apkUrl": "/updates/android/home-flix-0.2.0-2.apk",
  "sha256": "e3b0c44298fc1c149afbf4c8996fb924…",
  "size": 12345678,
  "releaseNotes": "Adds the episode browser."
}
```

`releaseNotes` is optional (absent ⇒ empty). `schemaVersion` starts at `1`; the app rejects
versions it does not understand.

### 5.3 Publish script — `scripts/publish-android-release.sh`

Follows `scripts/deploy-web.sh` conventions:

- Env: `SERVER` (default `orangepi3b.local`), `SSH_USER` (default `dzhang`), `REMOTE_DIR`
  (default `/mnt/ssd/web-frontend/updates/android`), `NOTES` (optional release-notes file),
  `DRY_RUN`, plus keystore env overrides for headless signing.
- Steps: run `./gradlew clean assembleRelease`; read `versionCode`/`versionName` from a new
  `./gradlew -q printVersionInfo` task; copy the APK to `home-flix-<versionName>-<versionCode>.apk`
  (both parts, so every release URL is unique under immutable caching); compute `sha256` and `size`;
  write `manifest.json`; `rsync` the APK and manifest to `REMOTE_DIR`.
- Safety: require an absolute, non-suspicious `REMOTE_DIR` (as `deploy-web.sh` does). Do **not**
  use `rsync --delete`, so previously published APKs remain available.
- **Coexistence with web deploys:** the release directory lives inside the web root that
  `scripts/deploy-web.sh` syncs, so that script must exclude `/updates/` from its `--delete`;
  otherwise a routine frontend deploy would remove the manifest and APKs.

## 6. Android data layer

New files under `app/src/main/kotlin/com/homeflix/app/data/`:

- `UpdateManifest.kt` — `@Serializable data class UpdateManifest(schemaVersion: Int, versionCode:
  Int, versionName: String, minSdk: Int, apkUrl: String, sha256: String, size: Long, releaseNotes:
  String?)`.
- `UpdateRepository.kt`:
  ```kotlin
  interface UpdateRepository {
      suspend fun fetchManifest(origin: String): UpdateManifest
      suspend fun download(
          apkUrl: String,
          size: Long,
          onProgress: (downloaded: Long, total: Long) -> Unit,
          sink: OutputStream
      ): String // SHA-256 hex of the bytes written
  }
  ```
  `HttpUpdateRepository` uses `HttpURLConnection` with connect/read timeouts, requires HTTP 200,
  streams with a buffer, and digests as it writes.
- `UpdateInstaller.kt`:
  ```kotlin
  sealed interface InstallResult {
      data object Success : InstallResult
      data class Failed(val reason: String) : InstallResult
      data object Cancelled : InstallResult
  }
  interface UpdateInstaller {
      fun canInstallPackages(): Boolean
      suspend fun install(write: suspend (OutputStream) -> Unit): Unit
      val results: Flow<InstallResult>
  }
  ```
  `PackageInstallerUpdateInstaller(context)` creates a `MODE_FULL_INSTALL` session, runs `write`
  against `session.openWrite(...)`, and commits only if `write` returns; if `write` throws it
  abandons the session. A dynamically registered `BroadcastReceiver` maps `STATUS_*` codes to
  `InstallResult` and launches the `STATUS_PENDING_USER_ACTION` intent via the application context.
- `UpdateOrigin.kt` — `fun updateOrigin(serverUrl: String): String` strips a trailing `/api`.

Wiring and build changes:

- `AppContainer` exposes `updateRepository` and `updateInstaller`.
- `app/build.gradle.kts`: `buildConfig = true`.
- `libs.versions.toml` / dependencies: `kotlinx-serialization-json` becomes `implementation`
  (already present for tests). No new library versions.

## 7. Android UI

- **Routes:** add `Routes.SETTINGS = "settings"`.
- **Top bars:** replace the "Log out" `TextButton` with a gear `IconButton` that navigates to
  Settings (in `HomeScreen`, `MoviesScreen`, `TvSeriesScreen`, `SearchScreen`).
- **`SettingsScreen` + `SettingsViewModel`:** shows "Version {versionName} ({versionCode})"; a
  "Check for updates" row rendering the check state (idle/checking/up-to-date/available with an
  Update action/error with retry); and the "Log out" action.
- **`UpdateViewModel`** (app-scoped, created in `MainActivity`, passed into `AppNavHost`):
  - `checkState: StateFlow<CheckState>` — `Idle | Checking | UpToDate | Available(UpdateManifest) |
    Error(message)`;
  - `installState: StateFlow<InstallState>` — `Idle | Downloading(done, total) | Verifying |
    Installing | Failed(message)`;
  - `promptEvents: Flow<UpdateManifest>` for the launch-time prompt;
  - `check()`, `update(manifest)`, `dismiss(manifest)`, `dismissError()`.
  - The ViewModel is Intent-free (unit-testable on the JVM).
- **Update dialog:** Material3 `AlertDialog` with version, release notes, **Update** / **Later**.
- **Install dialog:** progress indicator through downloading → verifying → installing, then success
  or failure (+ Retry).
- **Permission state:** when `!installer.canInstallPackages()`, an explanatory row with a button
  that launches `Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES` for this package (built in Compose
  with the real `Context`).

## 8. Signing and the one-time transition

- Add a release `signingConfig` reading `keystore.properties` (gitignored) with
  `storeFile`/`storePassword`/`keyAlias`/`keyPassword`, falling back to environment variables for
  headless builds.
- Add `keystore.properties`, `*.jks`, and `*.keystore` to `.gitignore`.
- **Transition:** the currently installed app is debug-signed, so the first release-signed APK
  cannot install over it. As part of adopting this feature, uninstall the debug build and install
  the first release build once (via `adb install`); subsequent releases update in-app. This is
  documented in `docs/development.md`.

## 9. Error handling

| Situation | Behavior |
|-----------|----------|
| Launch-time fetch/parse/HTTP failure | Silent; no UI; startup unaffected |
| Manual-check failure | Error state with Retry |
| Bad JSON / unknown `schemaVersion` / missing required field | Ignore the manifest; never prompt |
| `versionCode <= installed` | "Up to date" |
| Device `sdk < minSdk` | "No compatible update"; no prompt |
| Download network error/timeout | Failed + Retry; restart is fresh (no resume) |
| SHA-256/size mismatch | Failed ("download corrupted"); session abandoned; nothing committed |
| Signature mismatch (`FAILURE_*` / incompatible update) | Failed, hinting at the one-time uninstall |
| User cancels the system dialog | `Cancelled`; not shown as an error |
| Missing install permission | Permission state with deep link; no crash |
| Duplicate check/install request while in flight | Ignored |

## 10. Testing

- **JVM unit tests** (`app/src/test/…`, using new `FakeUpdateRepository` / `FakeUpdateInstaller` in
  the `TestDoubles.kt` style):
  - `updateOrigin` strips `/api` (and handles the already-bare case).
  - Version comparison: newer ⇒ available; equal/older ⇒ up to date; `sdk < minSdk` ⇒ skipped.
  - Manifest parsing incl. absent `releaseNotes` and unknown `schemaVersion`.
  - `HttpUpdateRepository`-equivalent download logic against `ByteArrayOutputStream`: correct
    digest and progress.
  - Checksum mismatch ⇒ the installer session is not committed.
  - Installer result mapping (success/failure/cancel).
  - `skippedVersionCode` suppresses a declined version but not a newer one.
  - Launch-time failure keeps `CheckState` silent.
- **Instrumented UI tests** (`app/src/androidTest/…`): Settings shows the version and each check
  state; the prompt shows release notes; the permission state shows the deep-link action.
- **Script test:** `scripts/test-publish-android-release.sh` asserts the generated `manifest.json`
  fields and digest for a fixture APK (mirrors `scripts/test-backup-media.sh`).
- **Manual on device:** publish a signed release, install an older build, confirm launch-time
  detection, prompt, download, install, and resulting version; then tamper the manifest `sha256`
  and confirm the corruption path is rejected.

## 11. Documentation

- `docs/architecture.md` — update subsystem, Caddy `/updates/*` route, release directory.
- `docs/development.md` — release signing, `keystore.properties`, the publish script, the one-time
  uninstall caveat.
- `docs/user-guide.md` — how to check for and apply an update in the app.
- `docs/project-plan.md` — an epic/story for the update feature.
- `README.md` — mention the release script alongside `deploy-web.sh`.

## 12. Out of scope

- Play Store / F-Droid distribution and Play in-app updates.
- Delta/patch updates (full APK only).
- Mandatory, forced, staged, or A/B updates.
- Background installs without user confirmation.
- Downgrades.
- ABI-split APKs (a single universal APK).
- CI-driven publishing (the script is run manually; the repo has no CI).
- Version display/update for the web frontend.

## 13. Definition of done

- The Caddy `/updates/*` route is in place and serves a manifest + versioned APK.
- `scripts/publish-android-release.sh` builds a signed release and writes a correct manifest;
  its script test passes.
- The app detects, downloads, verifies, and installs an update end-to-end on a device, including
  the permission, cancellation, and corruption paths.
- Android unit tests pass; web type-check/lint/build remain green.
- Docs listed in §11 are updated.
- No new third-party runtime dependencies.
- A plan document accompanies this spec and the work is executed test-first.
