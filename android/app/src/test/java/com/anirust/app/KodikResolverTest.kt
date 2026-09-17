package com.anirust.app

import com.anirust.app.data.remote.resolver.KodikResolver
import org.junit.Assert.assertEquals
import org.junit.Test

class KodikResolverTest {

    @Test
    fun testRot18Decryption() {
        // Rot18 is a shift of 18 characters (equivalent to -8 or +18 mod 26)
        val original = "Hello World"
        val encrypted = KodikResolver.decryptRot(original)
        // Two rounds of 18 shift gives 36 mod 26 = 10 shift.
        // Let's verify specific characters:
        // 'A' -> 'S' (0 + 18 = 18 = 'S')
        assertEquals("S", KodikResolver.decryptRot("A"))
        assertEquals("s", KodikResolver.decryptRot("a"))
        assertEquals("R", KodikResolver.decryptRot("Z"))
        assertEquals("r", KodikResolver.decryptRot("z"))
    }

    @Test
    fun testPrefixHttps() {
        assertEquals("https://kodik.info/test", KodikResolver.prefixHttps("//kodik.info/test"))
        assertEquals(
            "https://kodik.info/test",
            KodikResolver.prefixHttps("https://kodik.info/test"),
        )
        assertEquals("http://kodik.info/test", KodikResolver.prefixHttps("http://kodik.info/test"))
        assertEquals("https://kodik.info/test", KodikResolver.prefixHttps("/kodik.info/test"))
    }

    @Test
    fun testDecodeDirectM3u8() {
        val direct = "//cloud.kodik.info/video.m3u8"
        val decoded = KodikResolver.decodeKodikUrl(direct, "720")
        assertEquals("https://cloud.kodik.info/video.m3u8", decoded)
    }

    @Test
    fun directSignedUrlsAndInvalidEncodedData() {
        assertEquals(
            "https://cdn.test/video.m3u8?token=abc",
            KodikResolver.decodeKodikUrl("//cdn.test/video.m3u8?token=abc", "1080"),
        )
        assertEquals("", KodikResolver.decodeKodikUrl("!!!", "720"))
    }
}
