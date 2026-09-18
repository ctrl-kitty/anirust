package com.anirust.app.domain.usecase

import com.anirust.app.data.repository.FavoritesRepository
import com.anirust.app.data.repository.UiMessages
import com.anirust.app.domain.model.Anime
import com.anirust.app.domain.model.FavoriteItem
import com.anirust.app.domain.model.FavoriteStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class FavoritesUseCase(
    private val repository: FavoritesRepository,
    private val messages: UiMessages = UiMessages(),
) {
    fun getAllFavorites(): Flow<List<FavoriteItem>> = repository.getAllFavorites()

    fun getFavoritesByStatus(status: FavoriteStatus) = repository.getFavoritesByStatus(status)

    fun isFavorite(animeId: Long) = repository.isFavorite(animeId)

    fun getFavorite(animeId: Long) = repository.getFavorite(animeId)

    suspend fun updateStatus(animeId: Long, status: FavoriteStatus) =
        change("Локальный список изменён: " + status.title) {
            repository.updateStatus(animeId, status)
        }

    suspend fun addFavorite(anime: Anime, status: FavoriteStatus = FavoriteStatus.FAVORITE) =
        change("«" + anime.displayTitle + "» добавлено: " + status.title) {
            repository.addFavorite(anime, status)
        }

    suspend fun removeFavorite(animeId: Long): Boolean {
        return try {
            val previous = repository.getFavorite(animeId).first()
            repository.removeFavorite(animeId)
            messages.show(
                "Аниме удалено из локального списка",
                previous?.let { "Отменить" },
                previous?.let {
                    {
                        change("Закладка восстановлена") { repository.restoreFavorite(it) }
                        Unit
                    }
                },
            )
            true
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            messages.show("Не удалось удалить закладку", "Повторить") { removeFavorite(animeId) }
            false
        }
    }

    suspend fun clearFavorites() =
        change("Локальные списки очищены. Списки Shikimori не изменены") { repository.clearAll() }

    suspend fun replaceWithShikimori() =
        change("Локальные закладки заменены списками Shikimori. История сохранена") {
            repository.clearAll()
        }

    private suspend fun change(success: String, block: suspend () -> Unit): Boolean {
        return try {
            block()
            messages.show(success)
            true
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            messages.show("Не удалось изменить локальный список. Повтори попытку", "Повторить") {
                change(success, block)
            }
            false
        }
    }
}
