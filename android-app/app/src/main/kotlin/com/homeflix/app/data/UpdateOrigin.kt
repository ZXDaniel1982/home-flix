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
