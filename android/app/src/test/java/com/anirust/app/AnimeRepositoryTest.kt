package com.anirust.app

import com.anirust.app.data.remote.resolver.KodikResolver
import com.anirust.app.data.remote.shikimori.*
import com.anirust.app.data.remote.yummy.*
import com.anirust.app.data.repository.AnimeRepository
import com.squareup.moshi.Moshi
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AnimeRepositoryTest {
    private val yummy = FakeYummyApi()
    private val shiki = FakeShikimoriApi()
    private val resolver = KodikResolver(OkHttpClient())

    @Test
    fun concurrentDetailsAndSeriesRequestOnlyOneCatalogDetail() = runTest {
        var requests = 0
        yummy.details = { id ->
            requests++
            kotlinx.coroutines.delay(100)
            YummyDetailResponse(YummyAnimeDetail(animeId = id, title = "Title"))
        }
        val repo = AnimeRepository(yummy, shiki, resolver, StandardTestDispatcher(testScheduler))
        kotlinx.coroutines.coroutineScope {
            val details = async { repo.getAnimeDetails(111) }
            val series = async { repo.getSeries(111) }
            assertTrue(details.await().isSuccess)
            assertTrue(series.await().isSuccess)
        }
        assertEquals(1, requests)
    }

    @Test
    fun catalogPreviewArrivesWithoutWaitingForSlowMetadata() = runTest {
        yummy.search = {
            kotlinx.coroutines.delay(100)
            YummyListResponse(
                listOf(
                    YummyAnimeItem(
                        animeId = 111,
                        title = "Naruto",
                        remoteIds = YummyRemoteIds(shikimoriId = 20),
                    )
                )
            )
        }
        shiki.response = {
            kotlinx.coroutines.delay(5000)
            ShikimoriGraphQlResponse(
                ShikimoriData(listOf(ShikimoriAnime("20", russian = "Наруто")))
            )
        }
        val repo = AnimeRepository(yummy, shiki, resolver, StandardTestDispatcher(testScheduler))
        var previewAt = -1L
        val result =
            repo.search("Naruto") { preview ->
                previewAt = testScheduler.currentTime
                assertEquals(111L, preview.single().id)
                assertEquals("Naruto", preview.single().title)
            }
        assertEquals(100L, previewAt)
        assertEquals(5000L, testScheduler.currentTime)
        assertEquals("Наруто", result.getOrThrow().single().title)
        assertEquals(111L, result.getOrThrow().single().id)
    }

    @Test
    fun realCatalogObjectFieldsDeserializeWithGeneratedAdapters() {
        val moshi = Moshi.Builder().build()
        val json =
            """{"response":[{"anime_id":111,"title":"Наруто","rating":{"average":8.68,"counters":6151},"genres":[{"title":"Сёнэн","id":8}],"remote_ids":{"shikimori_id":20},"episodes":{"count":220,"aired":220},"poster":{"fullsize":"//static.yani.tv/poster.jpg"}}]}"""
        val item = moshi.adapter(YummyListResponse::class.java).fromJson(json)!!.response!!.single()
        assertEquals(8.68, item.rating!!.average!!, 0.01)
        assertEquals("Сёнэн", item.genres!!.single().title)
        assertEquals(220, item.episodes!!.count)
        assertEquals("https://static.yani.tv/poster.jpg", item.poster!!.bestUrl)
        val detail =
            moshi
                .adapter(YummyDetailResponse::class.java)
                .fromJson(
                    """{"response":{"anime_id":111,"viewing_order":[{"anime_id":119,"title":"Ураганные хроники","data":{"text":"продолжение","index":1}}]}}"""
                )!!
                .response!!
        assertEquals("Ураганные хроники", detail.viewingOrder!!.single().title)
    }

    @Test
    fun catalogAndMetadataIdsNeverCollide() = runTest {
        val repo = AnimeRepository(yummy, shiki, resolver, StandardTestDispatcher(testScheduler))
        yummy.search = {
            YummyListResponse(
                listOf(
                    YummyAnimeItem(
                        animeId = 111,
                        title = "Naruto",
                        remoteIds = YummyRemoteIds(shikimoriId = 20),
                    ),
                    YummyAnimeItem(animeId = 20, title = "Another title"),
                )
            )
        }
        shiki.response = {
            ShikimoriGraphQlResponse(
                ShikimoriData(listOf(ShikimoriAnime("20", russian = "Наруто")))
            )
        }
        assertEquals(listOf(111L, 20L), repo.search("Наруто").getOrThrow().map { it.id })
        assertEquals("Title 20", repo.getAnimeDetails(20).getOrThrow().title)
        repo.getEpisodes(20).getOrThrow()
        assertEquals(listOf(20L), yummy.requestedVideoIds)
    }

    @Test
    fun metadataFallbackUsesDistinctIdsAndRequiresAnExactProviderIdMatch() = runTest {
        val repo = AnimeRepository(yummy, shiki, resolver, StandardTestDispatcher(testScheduler))
        shiki.response = {
            ShikimoriGraphQlResponse(ShikimoriData(listOf(ShikimoriAnime("20", name = "Naruto"))))
        }
        assertEquals(-20L, repo.search("Naruto").getOrThrow().single().id)
        yummy.search = {
            YummyListResponse(listOf(YummyAnimeItem(animeId = 999, title = "Naruto remake")))
        }
        assertTrue(repo.getEpisodes(-20).isFailure)
        assertTrue(yummy.requestedVideoIds.isEmpty())
        yummy.search = {
            YummyListResponse(
                listOf(YummyAnimeItem(animeId = 111, remoteIds = YummyRemoteIds(shikimoriId = 20)))
            )
        }
        repo.getEpisodes(-20).getOrThrow()
        assertEquals(listOf(111L), yummy.requestedVideoIds)
    }

    @Test
    fun networkFailureIsNotReportedAsAnEmptySuccessfulSearch() = runTest {
        val repo = AnimeRepository(yummy, shiki, resolver, StandardTestDispatcher(testScheduler))
        yummy.search = { throw IOException("offline") }
        shiki.response = { throw IOException("offline") }
        assertTrue(repo.search("Naruto").isFailure)
        yummy.details = { throw IOException("offline") }
        assertTrue(repo.getAnimeDetails(111).isFailure)
        yummy.videos = { throw IOException("offline") }
        assertTrue(repo.getEpisodes(111).isFailure)
    }

    @Test
    fun cancellationPropagatesInsteadOfBecomingAnErrorResult() = runTest {
        val repo = AnimeRepository(yummy, shiki, resolver, StandardTestDispatcher(testScheduler))
        yummy.videos = { throw CancellationException("replaced request") }
        try {
            repo.getEpisodes(111)
            fail("Cancellation must propagate")
        } catch (_: CancellationException) {}
    }

    @Test
    fun onlyPlayableVariantsAppearAndExplicitDubbingNeverSilentlyChanges() = runTest {
        val repo = AnimeRepository(yummy, shiki, resolver, StandardTestDispatcher(testScheduler))
        yummy.videos = {
            YummyVideosResponse(
                listOf(
                    YummyVideoItem(
                        videoId = 1,
                        number = "1",
                        iframeUrl = "//alloha.tv/1",
                        data = YummyVideoData(dubbing = "Studio A"),
                    ),
                    YummyVideoItem(
                        videoId = 2,
                        number = "1",
                        iframeUrl = "https://cdn.test/episode.mp4?token=abc",
                        data = YummyVideoData(dubbing = "Studio B"),
                    ),
                    YummyVideoItem(
                        videoId = 3,
                        number = "special",
                        iframeUrl = "https://cdn.test/special.mp4",
                    ),
                    YummyVideoItem(videoId = 4, number = "2", iframeUrl = "https://cdn.test/2.m3u8"),
                )
            )
        }
        val episodes = repo.getEpisodes(111).getOrThrow()
        assertEquals(listOf(1, 2), episodes.map { it.number })
        assertEquals(listOf("Studio B"), episodes.first().voiceVariants.map { it.label })
        assertTrue(repo.resolveStream(111, 1, "Studio A").isFailure)
        assertEquals("Studio B", repo.resolveStream(111, 1, null).getOrThrow().dubbing)
        assertTrue(repo.resolveStream(111, 2, "Оригинал").isSuccess)
    }

    @Test
    fun emptyViewingOrderStillIncludesCurrentTitle() = runTest {
        val repo = AnimeRepository(yummy, shiki, resolver, StandardTestDispatcher(testScheduler))
        yummy.details = {
            YummyDetailResponse(
                YummyAnimeDetail(animeId = it, title = "Current", viewingOrder = emptyList())
            )
        }
        assertEquals("Current", repo.getSeries(111).getOrThrow().single().title)
    }

    @Test
    fun shikimoriListEntryResolvesOriginalTitleThenPlaysExactAnime() = runTest {
        val repo = AnimeRepository(yummy, shiki, resolver, StandardTestDispatcher(testScheduler))
        shiki.response = {
            ShikimoriGraphQlResponse(
                ShikimoriData(listOf(ShikimoriAnime("20", name = "Naruto", russian = "Наруто")))
            )
        }
        yummy.search = { query ->
            YummyListResponse(
                if (query == "Naruto")
                    listOf(
                        YummyAnimeItem(animeId = 20, title = "Naruto remake"),
                        YummyAnimeItem(
                            animeId = 111,
                            title = "Naruto",
                            remoteIds = YummyRemoteIds(shikimoriId = 20),
                        ),
                    )
                else emptyList()
            )
        }
        yummy.videos = {
            YummyVideosResponse(
                listOf(
                    YummyVideoItem(
                        number = "4",
                        iframeUrl = "https://cdn.test/naruto-4.mp4",
                        data = YummyVideoData(dubbing = "Studio A"),
                    )
                )
            )
        }
        assertEquals(4, repo.getEpisodes(-20).getOrThrow().single().number)
        assertEquals(
            "https://cdn.test/naruto-4.mp4",
            repo.resolveStream(-20, 4, "Studio A").getOrThrow().streamUrl,
        )
        assertTrue(yummy.requestedVideoIds.all { it == 111L })
    }
}
