package com.homeflix.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.api.client.extensions.authenticateUserByName
import org.jellyfin.sdk.api.client.extensions.userApi
import org.jellyfin.sdk.model.api.AuthenticationResult

interface AuthService {
    suspend fun authenticate(baseUrl: String, username: String, password: String): AuthenticationResult
}

class JellyfinAuthService(private val jellyfinProvider: JellyfinProvider) : AuthService {

    override suspend fun authenticate(
        baseUrl: String,
        username: String,
        password: String
    ): AuthenticationResult {
        val api = jellyfinProvider.createApi(baseUrl = baseUrl)
        // The SDK reads the response body on the caller's dispatcher; keep it off the main thread.
        return withContext(Dispatchers.IO) {
            api.userApi.authenticateUserByName(username = username, password = password).content
        }
    }
}
