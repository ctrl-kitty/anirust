package com.anirust.app.data.remote.shikimori

import retrofit2.http.Body
import retrofit2.http.Headers
import retrofit2.http.POST

interface ShikimoriApi {

    @POST("api/graphql")
    @Headers("User-Agent: AnirustAndroid/1.0", "Content-Type: application/json")
    suspend fun search(
        @Body request: ShikimoriGraphQlRequest
    ): ShikimoriGraphQlResponse

    companion object {
        const val SEARCH_QUERY = """
            query SearchAnime(${'$'}search: String!, ${'$'}limit: Int!) {
              animes(search: ${'$'}search, limit: ${'$'}limit) {
                id
                malId
                name
                russian
                english
                description
                score
                episodes
                poster {
                  originalUrl
                  mainUrl
                  previewUrl
                }
                genres {
                  name
                  russian
                }
              }
            }
        """

        const val GET_BY_ID_QUERY = """
            query GetAnimeById(${'$'}id: String!) {
              animes(ids: ${'$'}id, limit: 1) {
                id
                malId
                name
                russian
                english
                description
                score
                episodes
                poster {
                  originalUrl
                  mainUrl
                  previewUrl
                }
                genres {
                  name
                  russian
                }
              }
            }
        """
    }
}
