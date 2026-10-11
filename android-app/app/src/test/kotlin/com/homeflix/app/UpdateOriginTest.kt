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
