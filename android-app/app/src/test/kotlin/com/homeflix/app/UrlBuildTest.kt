package com.homeflix.app

import com.homeflix.app.data.buildImageUrl
import com.homeflix.app.data.buildStreamUrl
import org.jellyfin.sdk.model.api.ImageType
import org.junit.Assert.assertEquals
import org.junit.Test

class UrlBuildTest {

    @Test
    fun buildImageUrl_percentEncodesTagAndToken() {
        val url = buildImageUrl(
            baseUrl = "http://host/api",
            accessToken = "a b/c+",
            itemId = "item1",
            imageTag = "tag with space",
            imageType = ImageType.PRIMARY
        )
        assertEquals(
            "http://host/api/Items/item1/Images/Primary?tag=tag%20with%20space&api_key=a%20b%2Fc%2B",
            url
        )
    }

    @Test
    fun buildStreamUrl_trimsBaseUrlAndEncodesMediaSource() {
        val url = buildStreamUrl(
            baseUrl = "http://host/api/",
            accessToken = "token",
            itemId = "item1",
            mediaSourceId = "ms/1"
        )
        assertEquals(
            "http://host/api/Videos/item1/stream?static=true&MediaSourceId=ms%2F1&api_key=token",
            url
        )
    }
}
