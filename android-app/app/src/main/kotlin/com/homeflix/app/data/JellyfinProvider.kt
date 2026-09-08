package com.homeflix.app.data

import android.content.Context
import android.provider.Settings
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.createJellyfin
import org.jellyfin.sdk.model.ClientInfo
import org.jellyfin.sdk.model.DeviceInfo

class JellyfinProvider(appContext: Context) {

    private val jellyfin = createJellyfin {
        clientInfo = ClientInfo(name = "Home Flix", version = "0.1.0")
        deviceInfo = DeviceInfo(id = deviceId(appContext), name = "Android")
        context = appContext
    }

    fun createApi(baseUrl: String, accessToken: String? = null): ApiClient =
        jellyfin.createApi(baseUrl = baseUrl, accessToken = accessToken)

    private fun deviceId(context: Context): String =
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            ?: "unknown-device"
}
