package com.anirust.app.di

import android.content.Context
import com.anirust.app.BuildConfig
import com.anirust.app.data.local.AnirustDatabase
import com.anirust.app.data.local.EncryptedShikimoriSessionStore
import com.anirust.app.data.remote.resolver.KodikResolver
import com.anirust.app.data.remote.shikimori.ShikimoriAccountApi
import com.anirust.app.data.remote.shikimori.ShikimoriApi
import com.anirust.app.data.remote.yummy.YummyApi
import com.anirust.app.data.repository.AnimeRepository
import com.anirust.app.data.repository.FavoritesRepository
import com.anirust.app.data.repository.SettingsRepository
import com.anirust.app.data.repository.ShikimoriAccountRepository
import com.anirust.app.data.repository.UiMessages
import com.anirust.app.data.repository.WatchHistoryRepository
import com.anirust.app.domain.usecase.FavoritesUseCase
import com.anirust.app.domain.usecase.GetAnimeDetailsUseCase
import com.anirust.app.domain.usecase.GetEpisodesUseCase
import com.anirust.app.domain.usecase.ResolveStreamUseCase
import com.anirust.app.domain.usecase.SearchAnimeUseCase
import com.anirust.app.domain.usecase.WatchHistoryUseCase
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

interface AppContainer {
    val messages: UiMessages
    val shikimoriAccountRepository: ShikimoriAccountRepository?
        get() = null

    val animeRepository: AnimeRepository
    val watchHistoryRepository: WatchHistoryRepository
    val favoritesRepository: FavoritesRepository
    val settingsRepository: SettingsRepository

    val searchAnimeUseCase: SearchAnimeUseCase
    val getAnimeDetailsUseCase: GetAnimeDetailsUseCase
    val getEpisodesUseCase: GetEpisodesUseCase
    val resolveStreamUseCase: ResolveStreamUseCase
    val watchHistoryUseCase: WatchHistoryUseCase
    val favoritesUseCase: FavoritesUseCase
}

class DefaultAppContainer(private val context: Context) : AppContainer {
    override val messages = UiMessages()
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val moshi: Moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()

    private val okHttpClient: OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .addInterceptor(
                HttpLoggingInterceptor().apply {
                    level =
                        if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC
                        else HttpLoggingInterceptor.Level.NONE
                }
            )
            .build()

    private val yummyRetrofit: Retrofit =
        Retrofit.Builder()
            .baseUrl("https://api.yani.tv/")
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()

    private val shikimoriRetrofit: Retrofit =
        Retrofit.Builder()
            .baseUrl("https://shikimori.io/")
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()

    private val yummyApi: YummyApi = yummyRetrofit.create(YummyApi::class.java)
    private val shikimoriApi: ShikimoriApi = shikimoriRetrofit.create(ShikimoriApi::class.java)

    override val shikimoriAccountRepository by lazy {
        // Never log OAuth bodies/headers or follow redirects with account credentials.
        val client =
            OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .followRedirects(false)
                .followSslRedirects(false)
                .build()
        val api =
            Retrofit.Builder()
                .baseUrl("https://shikimori.io/")
                .client(client)
                .addConverterFactory(MoshiConverterFactory.create(moshi))
                .build()
                .create(ShikimoriAccountApi::class.java)
        ShikimoriAccountRepository(
            api,
            EncryptedShikimoriSessionStore(context, moshi),
            messages,
            applicationScope,
        )
    }

    private val kodikResolver: KodikResolver = KodikResolver(okHttpClient)

    private val database: AnirustDatabase = AnirustDatabase.getInstance(context)

    override val animeRepository: AnimeRepository by lazy {
        AnimeRepository(yummyApi, shikimoriApi, kodikResolver)
    }

    override val watchHistoryRepository: WatchHistoryRepository by lazy {
        WatchHistoryRepository(database.watchHistoryDao(), settingsRepository)
    }

    override val favoritesRepository: FavoritesRepository by lazy {
        FavoritesRepository(database.favoritesDao())
    }

    override val settingsRepository: SettingsRepository by lazy { SettingsRepository(context) }

    override val searchAnimeUseCase: SearchAnimeUseCase by lazy {
        SearchAnimeUseCase(animeRepository)
    }

    override val getAnimeDetailsUseCase: GetAnimeDetailsUseCase by lazy {
        GetAnimeDetailsUseCase(animeRepository)
    }

    override val getEpisodesUseCase: GetEpisodesUseCase by lazy {
        GetEpisodesUseCase(animeRepository)
    }

    override val resolveStreamUseCase: ResolveStreamUseCase by lazy {
        ResolveStreamUseCase(animeRepository)
    }

    override val watchHistoryUseCase: WatchHistoryUseCase by lazy {
        WatchHistoryUseCase(watchHistoryRepository, settingsRepository)
    }

    override val favoritesUseCase: FavoritesUseCase by lazy {
        FavoritesUseCase(favoritesRepository, messages)
    }
}
