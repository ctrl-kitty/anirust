package com.anirust.app.ui.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.anirust.app.BuildConfig
import com.anirust.app.data.local.ShikimoriOAuthConfig
import com.anirust.app.data.remote.shikimori.ShikimoriRate
import com.anirust.app.data.repository.ShikimoriAccountRepository
import com.anirust.app.domain.model.Anime
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class ShikimoriViewModel(private val repository: ShikimoriAccountRepository) : ViewModel() {
    val state = repository.state
    private var actionJob: Job? = null

    private fun launchAction(action: suspend () -> Unit) {
        if (actionJob?.isActive == true || state.value.busy) return
        actionJob = viewModelScope.launch { action() }
    }

    init {
        viewModelScope.launch {
            state.first { !it.initializing }
            refreshIfStale()
        }
    }

    fun refreshIfStale() {
        val current = state.value
        if (
            current.user != null &&
                !current.needsLogin &&
                !current.busy &&
                System.currentTimeMillis() - current.lastSync > 60_000
        )
            sync()
    }

    fun sync() {
        launchAction { repository.sync() }
    }

    fun authorizationUrl(): String =
        ShikimoriAccountRepository.authorizationUrl(BuildConfig.SHIKIMORI_CLIENT_ID)

    fun signIn(code: String) {
        val config =
            ShikimoriOAuthConfig(
                BuildConfig.SHIKIMORI_CLIENT_ID,
                BuildConfig.SHIKIMORI_CLIENT_SECRET,
                BuildConfig.SHIKIMORI_APP_NAME,
            )
        launchAction { repository.signIn(config, code.trim()) }
    }

    fun signOut() {
        launchAction { repository.signOut() }
    }

    fun save(anime: Anime, status: String, score: Int, episodes: Int, onSuccess: () -> Unit) {
        launchAction { if (repository.saveRate(anime, status, score, episodes)) onSuccess() }
    }

    fun delete(rate: ShikimoriRate, onSuccess: () -> Unit) {
        launchAction { if (repository.deleteRate(rate)) onSuccess() }
    }

    class Factory(private val repository: ShikimoriAccountRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            ShikimoriViewModel(repository) as T
    }
}
