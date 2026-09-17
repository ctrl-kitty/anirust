package com.anirust.app.data.remote.yummy

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class YummyListResponse(
    @param:Json(name = "response") val response: List<YummyAnimeItem>? = null
)

@JsonClass(generateAdapter = true)
data class YummyDetailResponse(
    @param:Json(name = "response") val response: YummyAnimeDetail? = null
)

@JsonClass(generateAdapter = true)
data class YummyVideosResponse(
    @param:Json(name = "response") val response: List<YummyVideoItem>? = null
)

@JsonClass(generateAdapter = true)
data class YummyAnimeItem(
    @param:Json(name = "anime_id") val animeId: Long? = null,
    @param:Json(name = "id") val id: Long? = null,
    @param:Json(name = "title") val title: String? = null,
    @param:Json(name = "other_titles") val otherTitles: List<String>? = null,
    @param:Json(name = "description") val description: String? = null,
    @param:Json(name = "poster") val poster: YummyPoster? = null,
    @param:Json(name = "remote_ids") val remoteIds: YummyRemoteIds? = null,
    @param:Json(name = "rating") val rating: YummyRating? = null,
    @param:Json(name = "genres") val genres: List<YummyGenre>? = null,
    @param:Json(name = "year") val year: Int? = null,
    @param:Json(name = "episodes") val episodes: YummyEpisodes? = null,
) {
    val effectiveId: Long
        get() = animeId ?: id ?: 0L
}

@JsonClass(generateAdapter = true)
data class YummyAnimeDetail(
    @param:Json(name = "anime_id") val animeId: Long? = null,
    @param:Json(name = "id") val id: Long? = null,
    @param:Json(name = "title") val title: String? = null,
    @param:Json(name = "description") val description: String? = null,
    @param:Json(name = "poster") val poster: YummyPoster? = null,
    @param:Json(name = "remote_ids") val remoteIds: YummyRemoteIds? = null,
    @param:Json(name = "rating") val rating: YummyRating? = null,
    @param:Json(name = "genres") val genres: List<YummyGenre>? = null,
    @param:Json(name = "episodes") val episodes: YummyEpisodes? = null,
    @param:Json(name = "other_titles") val otherTitles: List<String>? = null,
    @param:Json(name = "year") val year: Int? = null,
    @param:Json(name = "viewing_order") val viewingOrder: List<YummyViewingOrder>? = null,
) {
    val effectiveId: Long
        get() = animeId ?: id ?: 0L
}

@JsonClass(generateAdapter = true)
data class YummyViewingOrder(
    @param:Json(name = "anime_id") val animeId: Long? = null,
    @param:Json(name = "title") val title: String? = null,
    @param:Json(name = "data") val data: YummyViewingData? = null,
)

@JsonClass(generateAdapter = true)
data class YummyViewingData(
    @param:Json(name = "text") val text: String? = null,
    @param:Json(name = "index") val index: Int? = null,
)

@JsonClass(generateAdapter = true)
data class YummyPoster(
    @param:Json(name = "fullsize") val fullsize: String? = null,
    @param:Json(name = "huge") val huge: String? = null,
    @param:Json(name = "big") val big: String? = null,
    @param:Json(name = "medium") val medium: String? = null,
    @param:Json(name = "small") val small: String? = null,
) {
    val bestUrl: String?
        get() {
            val raw = listOf(fullsize, big, medium, huge, small).firstOrNull { !it.isNullOrBlank() }
            return raw?.let { prefixHttps(it) }
        }

    private fun prefixHttps(url: String): String {
        return when {
            url.startsWith("http://") || url.startsWith("https://") -> url
            url.startsWith("//") -> "https:$url"
            else -> "https://$url"
        }
    }
}

@JsonClass(generateAdapter = true)
data class YummyRemoteIds(
    @param:Json(name = "shikimori_id") val shikimoriId: Long? = null,
    @param:Json(name = "myanimelist_id") val myAnimeListId: Long? = null,
    @param:Json(name = "kp_id") val kpId: Long? = null,
)

@JsonClass(generateAdapter = true)
data class YummyVideoItem(
    @param:Json(name = "video_id") val videoId: Long? = null,
    @param:Json(name = "title") val title: String? = null,
    @param:Json(name = "number") val number: String? = null,
    @param:Json(name = "iframe_url") val iframeUrl: String? = null,
    @param:Json(name = "data") val data: YummyVideoData? = null,
)

@JsonClass(generateAdapter = true)
data class YummyRating(@param:Json(name = "average") val average: Double? = null)

@JsonClass(generateAdapter = true)
data class YummyGenre(@param:Json(name = "title") val title: String? = null)

@JsonClass(generateAdapter = true)
data class YummyEpisodes(
    @param:Json(name = "count") val count: Int? = null,
    @param:Json(name = "aired") val aired: Int? = null,
)

@JsonClass(generateAdapter = true)
data class YummyVideoData(
    @param:Json(name = "player") val player: String? = null,
    @param:Json(name = "dubbing") val dubbing: String? = null,
    @param:Json(name = "title") val title: String? = null,
    @param:Json(name = "name") val name: String? = null,
)
