package com.anirust.app

import com.anirust.app.data.local.entity.FavoriteEntity
import com.anirust.app.data.local.entity.WatchHistoryEntity
import com.anirust.app.domain.model.Anime
import com.anirust.app.domain.model.FavoriteItem
import com.anirust.app.domain.model.FavoriteStatus
import com.anirust.app.domain.model.StreamMedia
import com.anirust.app.domain.model.WatchHistoryItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DomainModelTest {

    @Test
    fun testAnimeDisplayTitle() {
        val anime1 = Anime(id = 1, title = "Наруто", originalTitle = "Naruto")
        assertEquals("Наруто", anime1.displayTitle)

        val anime2 = Anime(id = 2, title = "", originalTitle = "Naruto")
        assertEquals("Naruto", anime2.displayTitle)
    }

    @Test
    fun testWatchHistoryProgress() {
        val item = WatchHistoryItem(
            animeId = 111,
            animeTitle = "Наруто",
            episodeId = "111_1",
            episodeNumber = 1,
            playbackPositionMs = 5000L,
            durationMs = 10000L
        )
        assertEquals(0.5f, item.progressFraction, 0.01f)
    }

    @Test
    fun testStreamMediaHeaders() {
        val stream = StreamMedia(
            streamUrl = "https://example.com/live.m3u8",
            headers = mapOf("Referer" to "https://kodik.info", "Origin" to "https://kodik.info")
        )
        val list = stream.toHeaderList()
        assertTrue(list.contains("Referer: https://kodik.info"))
        assertTrue(list.contains("Origin: https://kodik.info"))
    }

    @Test
    fun testEntityDomainMapping() {
        val item = WatchHistoryItem(
            animeId = 20,
            seriesId = "s1",
            animeTitle = "Naruto",
            animePoster = "http://poster.jpg",
            episodeId = "ep1",
            episodeNumber = 1,
            episodeTitle = "Enter Naruto",
            dubbing = "2x2",
            streamUrl = "http://stream.m3u8",
            iframeUrl = "http://iframe",
            playbackPositionMs = 1000L,
            durationMs = 2000L,
            lastWatchedTimestamp = 12345678L
        )

        val entity = WatchHistoryEntity.fromDomain(item)
        val restored = entity.toDomain()

        assertEquals(item.animeId, restored.animeId)
        assertEquals(item.episodeNumber, restored.episodeNumber)
        assertEquals(item.dubbing, restored.dubbing)
        assertEquals(item.playbackPositionMs, restored.playbackPositionMs)

        val fav = FavoriteItem(
            animeId = 20,
            title = "Naruto",
            status = FavoriteStatus.WATCHING
        )
        val favEntity = FavoriteEntity.fromDomain(fav)
        val restoredFav = favEntity.toDomain()
        assertEquals(FavoriteStatus.WATCHING, restoredFav.status)
    }
}
