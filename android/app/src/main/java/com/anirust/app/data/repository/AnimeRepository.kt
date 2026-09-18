package com.anirust.app.data.repository

import com.anirust.app.data.remote.resolver.KodikResolver
import com.anirust.app.data.remote.shikimori.ShikimoriAnime
import com.anirust.app.data.remote.shikimori.ShikimoriApi
import com.anirust.app.data.remote.shikimori.ShikimoriGraphQlRequest
import com.anirust.app.data.remote.yummy.YummyAnimeDetail
import com.anirust.app.data.remote.yummy.YummyAnimeItem
import com.anirust.app.data.remote.yummy.YummyApi
import com.anirust.app.data.remote.yummy.YummyVideoItem
import com.anirust.app.domain.model.Anime
import com.anirust.app.domain.model.Episode
import com.anirust.app.domain.model.PlayerKind
import com.anirust.app.domain.model.SeriesEntry
import com.anirust.app.domain.model.StreamMedia
import com.anirust.app.domain.model.VoiceVariant
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class AnimeRepository(
    private val yummyApi: YummyApi,
    private val shikimoriApi: ShikimoriApi,
    private val kodikResolver: KodikResolver,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    // Positive app IDs are Yummy IDs; negative IDs are metadata-only Shikimori IDs.
    // Provider IDs must never share a key space: Yummy 20 is not Shikimori 20.
    private val cachedVideos = ConcurrentHashMap<Long, List<YummyVideoItem>>()
    private val cachedDetails = ConcurrentHashMap<Long, YummyAnimeDetail>()
    private val detailLocks = Array(16) { Mutex() }
    private val shikimoriToYummy = ConcurrentHashMap<Long, Long>()

    suspend fun search(
        query: String,
        onCatalogReady: ((List<Anime>) -> Unit)? = null,
    ): Result<List<Anime>> =
        withContext(dispatcher) {
            repositoryResult {
                val search = query.trim()
                if (search.isEmpty()) return@repositoryResult emptyList()
                coroutineScope {
                    val yummy = async {
                        repositoryResult {
                            yummyApi.searchAnime(search).response
                                ?: throw IOException("Каталог не вернул результаты поиска")
                        }
                    }
                    val shiki = async {
                        repositoryResult {
                            shikimori(
                                ShikimoriApi.SEARCH_QUERY,
                                mapOf("search" to search, "limit" to 20),
                            )
                        }
                    }
                    val yummyResult = yummy.await()
                    val catalog =
                        yummyResult
                            .getOrDefault(emptyList())
                            .filter { it.effectiveId > 0 }
                            .distinctBy { it.effectiveId }
                    if (catalog.isNotEmpty() && onCatalogReady != null) {
                        onCatalogReady(sortSearchResults(catalog.map { merge(it, null) }, search))
                    }
                    val shikiResult = shiki.await()
                    val metadata = shikiResult.getOrDefault(emptyList())
                    if (yummyResult.isFailure && metadata.isEmpty()) {
                        throw IOException(
                            "Не удалось загрузить каталог. Проверьте подключение и повторите поиск.",
                            yummyResult.exceptionOrNull(),
                        )
                    }
                    val results =
                        if (catalog.isNotEmpty()) {
                            catalog.map { item ->
                                val match = metadata.firstOrNull { matches(item, it) }
                                merge(item, match)
                            }
                        } else {
                            metadata.mapNotNull { item ->
                                val id =
                                    item.id.toLongOrNull()?.takeIf { it > 0 }
                                        ?: return@mapNotNull null
                                merge(null, item, -id)
                            }
                        }
                    sortSearchResults(results, search)
                }
            }
        }

    private fun sortSearchResults(results: List<Anime>, query: String): List<Anime> =
        results
            .distinctBy { it.id }
            .sortedWith(
                compareBy<Anime> { relevance(it, query) }
                    .thenBy { it.displayTitle.lowercase() }
                    .thenBy { it.id }
            )

    suspend fun getAnimeDetails(animeId: Long): Result<Anime> =
        withContext(dispatcher) {
            repositoryResult {
                require(animeId != 0L && animeId != Long.MIN_VALUE) {
                    "Некорректный идентификатор аниме"
                }
                val yummyId = repositoryResult { resolveYummyId(animeId) }.getOrNull()
                val detail = yummyId?.let { repositoryResult { detail(it) }.getOrNull() }
                val shikiId = if (animeId < 0) -animeId else detail?.remoteIds?.shikimoriId
                val metadata =
                    shikiId
                        ?.takeIf { it > 0 }
                        ?.let { repositoryResult { shikimoriById(it) }.getOrNull() }
                if (detail == null && metadata == null) {
                    throw IOException(
                        "Не удалось загрузить информацию об аниме. Повторите попытку."
                    )
                }
                val item =
                    detail?.let {
                        YummyAnimeItem(
                            animeId = it.effectiveId,
                            title = it.title,
                            otherTitles = it.otherTitles,
                            description = it.description,
                            poster = it.poster,
                            remoteIds = it.remoteIds,
                            rating = it.rating,
                            genres = it.genres,
                            episodes = it.episodes,
                        )
                    }
                merge(item, metadata, animeId)
            }
        }

    suspend fun getSeries(animeId: Long): Result<List<SeriesEntry>> =
        withContext(dispatcher) {
            repositoryResult {
                val yummyId = resolveYummyId(animeId)
                val detail = detail(yummyId)
                val current =
                    SeriesEntry(
                        animeId.toString(),
                        detail.title.text() ?: "Основной сезон",
                        0,
                        detail.poster?.bestUrl,
                        detail.year,
                    )
                val entries =
                    detail.viewingOrder.orEmpty().mapNotNull {
                        val id = it.animeId?.takeIf { id -> id > 0 } ?: return@mapNotNull null
                        SeriesEntry(
                            if (id == yummyId) animeId.toString() else id.toString(),
                            it.title.text() ?: "Сезон / Часть",
                            it.data?.index,
                            it.poster?.bestUrl ?: if (id == yummyId) current.posterUrl else null,
                            it.year ?: if (id == yummyId) current.year else null,
                        )
                    }
                (if (entries.any { it.id == current.id }) entries else listOf(current) + entries)
                    .distinctBy { it.id }
                    .sortedWith(
                        compareBy<SeriesEntry> { it.order ?: Int.MAX_VALUE }.thenBy { it.id }
                    )
            }
        }

    suspend fun getEpisodes(animeId: Long): Result<List<Episode>> =
        withContext(dispatcher) {
            repositoryResult {
                val yummyId = resolveYummyId(animeId)
                val videos =
                    yummyApi.getAnimeVideos(yummyId).response
                        ?: throw IOException("Каталог не вернул список серий")
                cachedVideos[yummyId] = videos
                videos
                    .filter { it.episodeNumber != null && it.kind.isPlayable }
                    .groupBy { it.episodeNumber!! }
                    .toSortedMap()
                    .map { (number, variants) ->
                        val sorted = variants.sortedWith(videoOrder)
                        val primary = sorted.first()
                        Episode(
                            id = animeId.toString() + "_" + number,
                            number = number,
                            title =
                                primary.title.text()
                                    ?: primary.data?.title.text()
                                    ?: primary.data?.name.text(),
                            iframeUrl = KodikResolver.prefixHttps(primary.iframeUrl!!),
                            voiceVariants =
                                sorted
                                    .distinctBy { it.dubbing }
                                    .map {
                                        VoiceVariant(
                                            it.videoId?.toString() ?: it.dubbing,
                                            it.dubbing,
                                        )
                                    },
                            playerKind = primary.kind,
                        )
                    }
            }
        }

    suspend fun resolveStream(
        animeId: Long,
        episodeNumber: Int,
        dubbing: String?,
    ): Result<StreamMedia> =
        withContext(dispatcher) {
            repositoryResult {
                val yummyId = resolveYummyId(animeId)
                // Refresh when opening: provider links may expire during a long browsing session.
                val videos =
                    repositoryResult {
                            yummyApi.getAnimeVideos(yummyId).response
                                ?: throw IOException("Каталог не вернул список серий")
                        }
                        .getOrElse { error -> cachedVideos[yummyId] ?: throw error }
                cachedVideos[yummyId] = videos
                val candidates =
                    videos
                        .filter { it.episodeNumber == episodeNumber && it.kind.isPlayable }
                        .sortedWith(videoOrder)
                val selected =
                    if (dubbing.isNullOrBlank()) candidates.firstOrNull()
                    else
                        candidates.firstOrNull {
                            it.dubbing.equals(dubbing.trim(), ignoreCase = true)
                        }
                if (selected == null)
                    throw IOException(
                        if (dubbing.isNullOrBlank())
                            "Нет поддерживаемого видео для серии " + episodeNumber
                        else "Озвучка «" + dubbing + "» недоступна для этой серии. Выберите другую."
                    )
                val url = KodikResolver.prefixHttps(selected.iframeUrl!!)
                val stream =
                    when (selected.kind) {
                        PlayerKind.Kodik -> kodikResolver.resolve(url).getOrThrow()
                        PlayerKind.Direct ->
                            StreamMedia(url, mapOf("User-Agent" to KodikResolver.USER_AGENT))
                        else -> error("Unsupported player")
                    }
                stream.copy(dubbing = selected.dubbing, iframeUrl = url)
            }
        }

    private suspend fun detail(id: Long): YummyAnimeDetail =
        cachedDetails[id]
            ?: detailLocks[(id.hashCode() and Int.MAX_VALUE) % detailLocks.size].withLock {
                cachedDetails[id]
                    ?: (yummyApi.getAnimeDetail(id).response
                            ?: throw IOException("Аниме не найдено"))
                        .also { cachedDetails[id] = it }
            }

    private suspend fun resolveYummyId(appId: Long): Long {
        require(appId != 0L && appId != Long.MIN_VALUE) { "Некорректный идентификатор аниме" }
        if (appId > 0) return appId
        val shikiId = -appId
        shikimoriToYummy[shikiId]?.let {
            return it
        }
        val metadata = shikimoriById(shikiId) ?: throw IOException("Аниме не найдено")
        val titles =
            listOfNotNull(metadata.russian.text(), metadata.name.text(), metadata.english.text())
                .distinct()
        if (titles.isEmpty()) throw IOException("Название аниме недоступно")
        // Never choose the first title match: sequels and remakes often have similar names.
        for (title in titles) {
            val match =
                yummyApi.searchAnime(title).response.orEmpty().firstOrNull {
                    it.effectiveId > 0 && matches(it, metadata)
                }
            if (match != null) return match.effectiveId.also { shikimoriToYummy[shikiId] = it }
        }
        throw IOException("Это аниме пока недоступно в видеокаталоге")
    }

    private suspend fun shikimoriById(id: Long): ShikimoriAnime? =
        shikimori(ShikimoriApi.GET_BY_ID_QUERY, mapOf("id" to id.toString())).firstOrNull()

    private suspend fun shikimori(
        query: String,
        variables: Map<String, Any>,
    ): List<ShikimoriAnime> {
        val response = shikimoriApi.search(ShikimoriGraphQlRequest(query, variables))
        return response.data?.animes
            ?: throw IOException(response.errors?.firstOrNull()?.message ?: "Метаданные недоступны")
    }

    private fun matches(item: YummyAnimeItem, metadata: ShikimoriAnime): Boolean =
        (item.remoteIds?.shikimoriId?.takeIf { it > 0 }?.toString() == metadata.id) ||
            (item.remoteIds
                ?.myAnimeListId
                ?.takeIf { it > 0 }
                ?.toString()
                ?.let { it == metadata.malId } == true)

    private fun merge(
        item: YummyAnimeItem?,
        metadata: ShikimoriAnime?,
        id: Long = item!!.effectiveId,
    ): Anime =
        Anime(
            id = id,
            yummyId = item?.effectiveId,
            shikimoriId =
                item?.remoteIds?.shikimoriId?.takeIf { it > 0 } ?: metadata?.id?.toLongOrNull(),
            myAnimeListId =
                item?.remoteIds?.myAnimeListId?.takeIf { it > 0 }
                    ?: metadata?.malId?.toLongOrNull(),
            title =
                metadata?.russian.text()
                    ?: item?.title.text()
                    ?: metadata?.name.text()
                    ?: "Без названия",
            originalTitle = metadata?.name.text() ?: metadata?.english.text(),
            altTitles = item?.otherTitles.orEmpty(),
            synopsis = item?.description.text() ?: metadata?.description.text(),
            posterUrl = metadata?.poster?.bestUrl ?: item?.poster?.bestUrl,
            score = (metadata?.score ?: item?.rating?.average)?.toFloat()?.takeIf { it > 0 },
            episodesCount = (item?.episodes?.count ?: metadata?.episodes)?.takeIf { it > 0 },
            genres =
                metadata
                    ?.genres
                    ?.mapNotNull { it.russian.text() ?: it.name.text() }
                    ?.takeIf { it.isNotEmpty() }
                    ?: item?.genres.orEmpty().mapNotNull { it.title.text() },
            source = if (item != null) "yummy" else "shikimori",
        )

    private fun relevance(anime: Anime, query: String): Int {
        val titles =
            (listOf(anime.title, anime.originalTitle.orEmpty()) + anime.altTitles).map {
                it.trim().lowercase()
            }
        val needle = query.lowercase()
        return when {
            titles.any { it == needle } -> 0
            titles.any { it.startsWith(needle) } -> 1
            titles.any { it.contains(needle) } -> 2
            else -> 3
        }
    }

    private fun String?.text(): String? = this?.trim()?.takeIf { it.isNotEmpty() }

    private val YummyVideoItem.episodeNumber: Int?
        get() = number?.trim()?.toIntOrNull()?.takeIf { it > 0 }

    private val YummyVideoItem.dubbing: String
        get() = data?.dubbing.text() ?: "Оригинал"

    private val YummyVideoItem.kind: PlayerKind
        get() = PlayerKind.fromUrl(iframeUrl.orEmpty())

    private val videoOrder =
        compareBy<YummyVideoItem> { if (it.kind == PlayerKind.Kodik) 0 else 1 }
            .thenBy { it.dubbing }
            .thenBy { it.videoId ?: Long.MAX_VALUE }
}
