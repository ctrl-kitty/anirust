package com.anirust.app

import com.anirust.app.domain.model.PlayerKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerKindTest {

    @Test
    fun testPlayerKindDetection() {
        val kodikUrl1 =
            "https://kodikplayer.com/serial/6646/4698b2bea53c04aa757d3d1ff42fea53/720p?season=1&only_episode=true&episode=1"
        val kodikUrl2 = "//kodik.info/video/12345"
        val directUrl1 = "https://example.com/stream.m3u8"
        val directUrl2 = "https://example.com/video.mp4"
        val allohaUrl = "//alloha.yani.tv/?token_movie=123"

        assertEquals(PlayerKind.Kodik, PlayerKind.fromUrl(kodikUrl1))
        assertEquals(PlayerKind.Kodik, PlayerKind.fromUrl(kodikUrl2))
        assertEquals(PlayerKind.Direct, PlayerKind.fromUrl(directUrl1))
        assertEquals(PlayerKind.Direct, PlayerKind.fromUrl(directUrl2))
        assertEquals(PlayerKind.Alloha, PlayerKind.fromUrl(allohaUrl))

        assertTrue(PlayerKind.Kodik.isPlayable)
        assertTrue(PlayerKind.Direct.isPlayable)
        assertFalse(PlayerKind.Alloha.isPlayable)
    }

    @Test
    fun signedMediaUrlsAreDetectedByTheirPath() {
        assertEquals(
            PlayerKind.Direct,
            PlayerKind.fromUrl("https://cdn.test/video.mp4?token=abc#part"),
        )
        assertEquals(
            PlayerKind.Direct,
            PlayerKind.fromUrl("https://cloud.kodik.info/720.m3u8?token=abc"),
        )
        assertEquals(
            PlayerKind.Unknown,
            PlayerKind.fromUrl("https://example.com/watch?next=kodik.info"),
        )
        assertEquals(PlayerKind.Unknown, PlayerKind.fromUrl("javascript:video.mp4"))
    }
}
