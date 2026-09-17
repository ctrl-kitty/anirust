package com.anirust.app.data.repository

import com.anirust.app.data.local.*
import com.anirust.app.data.remote.shikimori.*
import com.anirust.app.domain.model.Anime
import java.io.IOException
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.HttpUrl.Companion.toHttpUrl
import retrofit2.HttpException

private class AccountValidationException(message: String) : Exception(message)

// Only our own messages are safe to surface; library errors can contain credentials or payloads.
private inline fun requireAccount(value: Boolean, message: () -> String) {
    if (!value) throw AccountValidationException(message())
}

data class ShikimoriAccountState(
    val user: ShikimoriUser? = null,
    val rates: List<ShikimoriRate> = emptyList(),
    val lastSync: Long = 0,
    val busy: Boolean = false,
    val initializing: Boolean = true,
    val needsLogin: Boolean = false,
    val error: String? = null,
)

class ShikimoriAccountRepository(
    private val api: ShikimoriAccountApi,
    private val store: ShikimoriSessionStore,
    private val messages: UiMessages,
    scope: CoroutineScope,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val now: () -> Long = System::currentTimeMillis,
    private val pause: suspend () -> Unit = { delay(750) },
) {
    private val mutex = Mutex()
    private var session: ShikimoriSession? = null
    private val mutableState = MutableStateFlow(ShikimoriAccountState())
    val state = mutableState.asStateFlow()
    private val initialized =
        scope.launch(dispatcher) {
            try {
                session = store.read()
                publish()
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                mutableState.value =
                    ShikimoriAccountState(
                        initializing = false,
                        error =
                            "Не удалось прочитать защищённый аккаунт. Подключи Shikimori заново.",
                    )
            }
        }

    suspend fun signIn(input: ShikimoriOAuthConfig, code: String): Boolean = operation {
        val config =
            ShikimoriOAuthConfig(
                input.clientId.trim(),
                input.clientSecret.trim(),
                input.appName.trim(),
            )
        requireAccount(
            config.clientId.isNotBlank() && config.clientSecret.isNotBlank() && code.isNotBlank()
        ) {
            "Заполни Client ID, Client Secret и код авторизации"
        }
        validateName(config.appName)
        val tokens =
            api.token(
                config.appName,
                mapOf(
                    "grant_type" to "authorization_code",
                    "client_id" to config.clientId.trim(),
                    "client_secret" to config.clientSecret.trim(),
                    "code" to code.trim(),
                    "redirect_uri" to REDIRECT_URI,
                ),
            )
        validateTokens(tokens)
        pause()
        val user = api.whoami("Bearer " + tokens.accessToken, config.appName)
        requireAccount(user.id > 0) { "Shikimori не вернул аккаунт" }
        // Save the new token before fetching a potentially large list.
        persist(
            ShikimoriSession(
                config,
                tokens.accessToken,
                tokens.refreshToken,
                expiresAt(tokens),
                user,
            )
        )
        messages.show("Shikimori: выполнен вход в " + user.nickname)
        downloadRates()
        messages.show("Списки Shikimori синхронизированы")
    }

    suspend fun sync(): Boolean = operation {
        downloadRates()
        messages.show("Списки Shikimori синхронизированы")
    }

    suspend fun signOut(): Boolean = operation {
        store.clear()
        session = null
        publish()
        messages.show("Аккаунт Shikimori отключён. Локальные списки сохранены")
    }

    suspend fun saveRate(anime: Anime, status: String, score: Int, episodes: Int): Boolean =
        operation {
            requireAccount(ShikimoriListStatus.entries.any { it.apiValue == status }) {
                "Выбери статус списка"
            }
            requireAccount(score in 0..10) { "Оценка должна быть от 0 до 10" }
            requireAccount(
                episodes >= 0 && (anime.episodesCount == null || episodes <= anime.episodesCount)
            ) {
                "Проверь число просмотренных серий"
            }
            val shikiId =
                anime.shikimoriId?.takeIf { it > 0 }
                    ?: anime.id.takeIf { it < 0 && it != Long.MIN_VALUE }?.let { -it }
                    ?: throw AccountValidationException(
                        "Для этого аниме нет подтверждённого ID Shikimori"
                    )
            val account = requireSession()
            val existing = account.rates.firstOrNull { it.anime.id == shikiId }
            val changes = linkedMapOf<String, String>()
            if (existing == null || status != existing.status) changes["status"] = status
            if (existing == null || score != existing.score) changes["score"] = score.toString()
            if (existing == null || episodes != existing.episodes)
                changes["episodes"] = episodes.toString()
            if (changes.isEmpty()) {
                messages.show("В списке ничего не изменилось")
                return@operation
            }
            if (existing == null) {
                changes["user_id"] = account.user.id.toString()
                changes["target_id"] = shikiId.toString()
                changes["target_type"] = "Anime"
            }
            val response = authorized { current, header ->
                if (existing == null)
                    api.createRate(header, current.config.appName, RateRequest(changes))
                else
                    api.updateRate(
                        header,
                        current.config.appName,
                        existing.id,
                        RateRequest(changes),
                    )
            }
            requireAccount(
                response.id > 0 &&
                    (!changes.containsKey("status") || response.status == status) &&
                    (!changes.containsKey("score") || response.score == score) &&
                    (!changes.containsKey("episodes") || response.episodes == episodes)
            ) {
                "Shikimori не подтвердил изменение. Обнови список и проверь результат"
            }
            val saved =
                ShikimoriRate(
                    response.id,
                    response.status,
                    response.score,
                    response.episodes,
                    existing?.anime
                        ?: ShikimoriListAnime(
                            shikiId,
                            anime.originalTitle,
                            anime.title,
                            anime.posterUrl?.let(::ShikimoriImage),
                            anime.episodesCount ?: 0,
                        ),
                )
            val current = requireSession()
            persistRemoteChange(
                current.copy(
                    rates =
                        (current.rates.filterNot { it.anime.id == shikiId } + saved).sortedBy {
                            it.anime.toAnime().displayTitle
                        }
                )
            )
            messages.show(
                "Shikimori: «" + anime.displayTitle + "» — " + ShikimoriListStatus.titleOf(status)
            )
        }

    suspend fun deleteRate(rate: ShikimoriRate): Boolean = operation {
        val current = requireSession()
        // Don't let a stale dialog act on another account or an already removed entry.
        requireAccount(current.rates.any { it.id == rate.id && it.anime.id == rate.anime.id }) {
            "Запись изменилась. Обнови список"
        }
        try {
            authorized { account, header ->
                api.deleteRate(header, account.config.appName, rate.id)
            }
        } catch (error: HttpException) {
            if (error.code() != 404) throw error
        }
        persistRemoteChange(
            requireSession().copy(rates = requireSession().rates.filterNot { it.id == rate.id })
        )
        messages.show("Shikimori: «" + rate.anime.toAnime().displayTitle + "» удалено из списка")
    }

    private suspend fun downloadRates() {
        val all = linkedMapOf<Long, ShikimoriRate>()
        var page = 1
        while (true) {
            val response = authorized { account, header ->
                api.rates(
                    header,
                    account.config.appName,
                    ShikimoriGraphQlRequest(
                        LIST_QUERY,
                        mapOf(
                            "user" to account.user.id.toString(),
                            "page" to page,
                            "limit" to PAGE_SIZE,
                        ),
                    ),
                )
            }
            if (!response.errors.isNullOrEmpty()) throw IOException("Incomplete GraphQL response")
            val batch = response.data?.userRates ?: throw IOException("Missing GraphQL userRates")
            requireAccount(batch.all { it.id > 0 && it.anime.id > 0 }) {
                "Shikimori вернул некорректный список"
            }
            val before = all.size
            batch.forEach { all[it.id] = it }
            // Replace the cache only after every page succeeds.
            if (batch.size < PAGE_SIZE) break
            if (all.size == before || page >= 1000) throw IOException("Pagination did not advance")
            page++
        }
        val current = requireSession()
        persist(
            current.copy(
                rates = all.values.sortedBy { it.anime.toAnime().displayTitle },
                lastSync = now(),
            )
        )
    }

    private suspend fun <T> authorized(block: suspend (ShikimoriSession, String) -> T): T {
        pause()
        var current = requireSession()
        if (current.expiresAt <= now() + 60_000) current = refresh()
        return try {
            block(current, "Bearer " + current.accessToken)
        } catch (error: HttpException) {
            if (error.code() != 401) throw error
            current = refresh()
            block(current, "Bearer " + current.accessToken)
        }
    }

    private suspend fun refresh(): ShikimoriSession {
        val current = requireSession()
        val tokens =
            try {
                api.token(
                    current.config.appName,
                    mapOf(
                        "grant_type" to "refresh_token",
                        "client_id" to current.config.clientId,
                        "client_secret" to current.config.clientSecret,
                        "refresh_token" to current.refreshToken,
                    ),
                )
            } catch (error: HttpException) {
                if (error.code() in listOf(400, 401))
                    mutableState.update { it.copy(needsLogin = true) }
                throw error
            }
        validateTokens(tokens)
        val renewed =
            current.copy(
                accessToken = tokens.accessToken,
                refreshToken = tokens.refreshToken,
                expiresAt = expiresAt(tokens),
            )
        persist(renewed)
        pause()
        return renewed
    }

    private fun validateTokens(tokens: OAuthTokens) {
        requireAccount(
            tokens.accessToken.isNotBlank() &&
                tokens.refreshToken.isNotBlank() &&
                tokens.expiresIn in 1..31_536_000 &&
                tokens.tokenType.equals("bearer", ignoreCase = true)
        ) {
            "Не удалось подтвердить авторизацию Shikimori"
        }
    }

    private fun expiresAt(tokens: OAuthTokens) = now() + tokens.expiresIn * 1000

    private fun requireSession() =
        session ?: throw AccountValidationException("Сначала подключи аккаунт Shikimori")

    private fun persist(value: ShikimoriSession) {
        session = value
        publish()
        store.save(value)
    }

    private fun persistRemoteChange(value: ShikimoriSession) {
        // Remote write has succeeded: do not present a disk-cache failure as a failed server write.
        session = value
        publish()
        try {
            store.save(value)
        } catch (error: Exception) {
            messages.show(
                "Shikimori обновлён, но копию на устройстве сохранить не удалось. Обнови списки после перезапуска"
            )
        }
    }

    private fun publish() {
        val current = session
        mutableState.value =
            ShikimoriAccountState(
                user = current?.user,
                rates = current?.rates.orEmpty(),
                lastSync = current?.lastSync ?: 0,
                busy = mutableState.value.busy,
                initializing = false,
            )
    }

    private suspend fun operation(block: suspend () -> Unit): Boolean =
        withContext(dispatcher) {
            initialized.join()
            mutex.withLock {
                mutableState.update { it.copy(busy = true, error = null) }
                try {
                    block()
                    true
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    val message =
                        when (error) {
                            is HttpException ->
                                when (error.code()) {
                                    400,
                                    401 ->
                                        "Авторизация Shikimori истекла или код неверен. Подключи аккаунт заново"
                                    403 ->
                                        "Shikimori не разрешил доступ. Проверь разрешение user_rates"
                                    404 -> "Запись Shikimori не найдена. Обнови списки"
                                    422 ->
                                        "Shikimori отклонил данные. Проверь статус, оценку и число серий"
                                    429 -> "Слишком много запросов к Shikimori. Подожди и повтори"
                                    else -> "Shikimori временно недоступен. Повтори позже"
                                }
                            is AccountValidationException ->
                                error.message ?: "Проверь введённые данные"
                            else ->
                                "Не удалось связаться с Shikimori или сохранить данные. Список не синхронизирован"
                        }
                    if (error is HttpException && error.code() == 401)
                        mutableState.update { it.copy(needsLogin = true) }
                    mutableState.update { it.copy(error = message) }
                    messages.show(message)
                    false
                } finally {
                    mutableState.update { it.copy(busy = false) }
                }
            }
        }

    companion object {
        const val REDIRECT_URI = "urn:ietf:wg:oauth:2.0:oob"
        private const val PAGE_SIZE = 50
        const val LIST_QUERY =
            """
            query MyAnimeLists(${'$'}user: ID!, ${'$'}page: PositiveInt!, ${'$'}limit: PositiveInt!) {
              userRates(userId: ${'$'}user, targetType: Anime, page: ${'$'}page, limit: ${'$'}limit) {
                id status score episodes
                anime { id name russian episodes image: poster { original: originalUrl } }
              }
            }
        """

        fun authorizationUrl(clientId: String): String {
            require(clientId.isNotBlank()) { "Укажи Client ID OAuth-приложения" }
            return "https://shikimori.io/oauth/authorize"
                .toHttpUrl()
                .newBuilder()
                .addQueryParameter("client_id", clientId.trim())
                .addQueryParameter("redirect_uri", REDIRECT_URI)
                .addQueryParameter("response_type", "code")
                .addQueryParameter("scope", "user_rates")
                .build()
                .toString()
        }

        private fun validateName(name: String) {
            requireAccount(name.isNotBlank() && name.all { it.code in 32..126 }) {
                "Имя OAuth-приложения должно содержать только латиницу"
            }
        }
    }
}
