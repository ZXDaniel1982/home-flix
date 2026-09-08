package com.homeflix.app.data

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
        return api.userApi.authenticateUserByName(username = username, password = password).content
    }
}
