package com.anirust.app.data.remote.shikimori

import com.anirust.app.domain.model.Anime
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import retrofit2.http.*

interface ShikimoriAccountApi {
    @FormUrlEncoded
    @POST("oauth/token")
    suspend fun token(
        @Header("User-Agent") appName: String,
        @FieldMap fields: Map<String, String>,
    ): OAuthTokens

    @GET("api/users/whoami")
    suspend fun whoami(
        @Header("Authorization") authorization: String,
        @Header("User-Agent") appName: String,
    ): ShikimoriUser

    @POST("api/graphql")
    suspend fun rates(
        @Header("Authorization") authorization: String,
        @Header("User-Agent") appName: String,
        @Body request: ShikimoriGraphQlRequest,
    ): AccountGraphResponse

    @POST("api/v2/user_rates")
    suspend fun createRate(
        @Header("Authorization") authorization: String,
        @Header("User-Agent") appName: String,
        @Body request: RateRequest,
    ): RateResponse

    @PATCH("api/v2/user_rates/{id}")
    suspend fun updateRate(
        @Header("Authorization") authorization: String,
        @Header("User-Agent") appName: String,
        @Path("id") rateId: Long,
        @Body request: RateRequest,
    ): RateResponse

    @DELETE("api/v2/user_rates/{id}")
    suspend fun deleteRate(
        @Header("Authorization") authorization: String,
        @Header("User-Agent") appName: String,
        @Path("id") rateId: Long,
    )
}

@JsonClass(generateAdapter = true)
data class AccountGraphResponse(
    val data: AccountGraphData? = null,
    val errors: List<ShikimoriError>? = null,
)

@JsonClass(generateAdapter = true)
data class AccountGraphData(val userRates: List<ShikimoriRate>? = null)

@JsonClass(generateAdapter = true)
data class OAuthTokens(
    @param:Json(name = "access_token") val accessToken: String,
    @param:Json(name = "refresh_token") val refreshToken: String,
    @param:Json(name = "expires_in") val expiresIn: Long,
    @param:Json(name = "token_type") val tokenType: String = "Bearer",
) {
    override fun toString() = "OAuthTokens([redacted])"
}

@JsonClass(generateAdapter = true) data class ShikimoriUser(val id: Long, val nickname: String)

@JsonClass(generateAdapter = true) data class ShikimoriImage(val original: String? = null)

@JsonClass(generateAdapter = true)
data class ShikimoriListAnime(
    val id: Long,
    val name: String? = null,
    val russian: String? = null,
    val image: ShikimoriImage? = null,
    val episodes: Int = 0,
) {
    fun toAnime() =
        Anime(
            id = -id,
            shikimoriId = id,
            title = russian?.takeIf { it.isNotBlank() } ?: name ?: "Без названия",
            originalTitle = name,
            episodesCount = episodes.takeIf { it > 0 },
            posterUrl =
                image?.original?.let {
                    when {
                        it.startsWith("https://") -> it
                        it.startsWith("//") -> "https:" + it
                        it.startsWith("/") -> "https://shikimori.io" + it
                        else -> null
                    }
                },
            source = "shikimori",
        )
}

@JsonClass(generateAdapter = true)
data class ShikimoriRate(
    val id: Long,
    val status: String,
    val score: Int = 0,
    val episodes: Int = 0,
    val anime: ShikimoriListAnime,
)

@JsonClass(generateAdapter = true)
data class RateRequest(@param:Json(name = "user_rate") val userRate: Map<String, String>)

@JsonClass(generateAdapter = true)
data class RateResponse(
    val id: Long,
    val status: String,
    val score: Int = 0,
    val episodes: Int = 0,
)

enum class ShikimoriListStatus(val apiValue: String, val title: String) {
    WATCHING("watching", "Смотрю"),
    PLANNED("planned", "В планах"),
    COMPLETED("completed", "Просмотрено"),
    ON_HOLD("on_hold", "Отложено"),
    DROPPED("dropped", "Брошено"),
    REWATCHING("rewatching", "Пересматриваю");

    companion object {
        fun titleOf(value: String) = entries.firstOrNull { it.apiValue == value }?.title ?: value
    }
}
