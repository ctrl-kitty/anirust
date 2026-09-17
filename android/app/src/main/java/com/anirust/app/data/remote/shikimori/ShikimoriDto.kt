package com.anirust.app.data.remote.shikimori

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class ShikimoriGraphQlRequest(
    @param:Json(name = "query") val query: String,
    @param:Json(name = "variables") val variables: Map<String, Any>,
)

@JsonClass(generateAdapter = true)
data class ShikimoriGraphQlResponse(
    @param:Json(name = "data") val data: ShikimoriData? = null,
    @param:Json(name = "errors") val errors: List<ShikimoriError>? = null,
)

@JsonClass(generateAdapter = true)
data class ShikimoriError(@param:Json(name = "message") val message: String? = null)

@JsonClass(generateAdapter = true)
data class ShikimoriData(@param:Json(name = "animes") val animes: List<ShikimoriAnime>? = null)

@JsonClass(generateAdapter = true)
data class ShikimoriAnime(
    @param:Json(name = "id") val id: String,
    @param:Json(name = "malId") val malId: String? = null,
    @param:Json(name = "name") val name: String? = null,
    @param:Json(name = "russian") val russian: String? = null,
    @param:Json(name = "english") val english: String? = null,
    @param:Json(name = "description") val description: String? = null,
    @param:Json(name = "score") val score: Double? = null,
    @param:Json(name = "episodes") val episodes: Int? = null,
    @param:Json(name = "poster") val poster: ShikimoriPoster? = null,
    @param:Json(name = "genres") val genres: List<ShikimoriGenre>? = null,
)

@JsonClass(generateAdapter = true)
data class ShikimoriPoster(
    @param:Json(name = "originalUrl") val originalUrl: String? = null,
    @param:Json(name = "mainUrl") val mainUrl: String? = null,
    @param:Json(name = "previewUrl") val previewUrl: String? = null,
) {
    val bestUrl: String?
        get() = originalUrl ?: mainUrl ?: previewUrl
}

@JsonClass(generateAdapter = true)
data class ShikimoriGenre(
    @param:Json(name = "name") val name: String? = null,
    @param:Json(name = "russian") val russian: String? = null,
)
