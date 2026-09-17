package com.anirust.app

import android.app.Application
import com.anirust.app.data.remote.resolver.KodikResolver
import com.anirust.app.data.remote.shikimori.ShikimoriApi
import com.anirust.app.data.remote.yummy.YummyApi
import com.anirust.app.data.repository.AnimeRepository
import com.squareup.moshi.Moshi
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

/** Opt-in only: requires Yummy, Shikimori, Kodik and the media CDN to be reachable. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class LiveProviderTest {
    @Test
    fun searchDetailsEpisodesAndKodikResolveEndToEnd() = runBlocking {
        assumeTrue(System.getenv("ANIRUST_LIVE_TESTS") == "1")
        val client = OkHttpClient.Builder().callTimeout(30, TimeUnit.SECONDS).build()
        fun retrofit(url: String) =
            Retrofit.Builder()
                .baseUrl(url)
                .client(client)
                .addConverterFactory(MoshiConverterFactory.create(Moshi.Builder().build()))
                .build()
        val repository =
            AnimeRepository(
                retrofit("https://api.yani.tv/").create(YummyApi::class.java),
                retrofit("https://shikimori.io/").create(ShikimoriApi::class.java),
                KodikResolver(client),
            )
        val results = repository.search("Naruto").getOrThrow()
        assertTrue(results.any { it.id == 111L })
        assertEquals(111L, repository.getAnimeDetails(111).getOrThrow().yummyId)
        val episodes = repository.getEpisodes(111).getOrThrow()
        val episode = episodes.first { it.number == 1 }
        val dubbing =
            episode.voiceVariants.firstOrNull { it.label.contains("2x2") }
                ?: episode.voiceVariants.first()
        val stream = repository.resolveStream(111, 1, dubbing.label).getOrThrow()
        assertTrue(stream.streamUrl.startsWith("https://"))
        val request = Request.Builder().url(stream.streamUrl)
        stream.headers.forEach { (name, value) -> request.header(name, value) }
        client.newCall(request.build()).execute().use {
            assertTrue("Media CDN returned HTTP " + it.code, it.isSuccessful)
            assertTrue(it.body!!.source().readUtf8Line()!!.startsWith("#EXTM3U"))
        }
    }
}
