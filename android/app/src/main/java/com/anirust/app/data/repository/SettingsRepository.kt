package com.anirust.app.data.repository

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.anirust.app.domain.model.Anime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AnimeDubbingPreference(val dubbing: String?)

class SettingsRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("anirust_preferences", Context.MODE_PRIVATE)

    private val _preferredDubbing =
        MutableStateFlow(prefs.getString(KEY_PREFERRED_DUBBING, "") ?: "")
    val preferredDubbing: StateFlow<String> = _preferredDubbing.asStateFlow()

    private val _useExternalPlayer =
        MutableStateFlow(prefs.getBoolean(KEY_USE_EXTERNAL_PLAYER, false))
    val useExternalPlayer: StateFlow<Boolean> = _useExternalPlayer.asStateFlow()

    private val _externalPlayerPackage =
        MutableStateFlow(prefs.getString(KEY_EXTERNAL_PLAYER_PKG, PACKAGE_MPVEX) ?: PACKAGE_MPVEX)
    val externalPlayerPackage: StateFlow<String> = _externalPlayerPackage.asStateFlow()

    private val _syncIntervalMinutes =
        MutableStateFlow(prefs.getInt(KEY_SYNC_INTERVAL, DEFAULT_SYNC_INTERVAL).coerceIn(5, 120))
    val syncIntervalMinutes = _syncIntervalMinutes.asStateFlow()
    private val _syncWatchedProgress =
        MutableStateFlow(prefs.getBoolean("sync_watched_progress", false))
    val syncWatchedProgress = _syncWatchedProgress.asStateFlow()

    fun setSyncWatchedProgress(enabled: Boolean) {
        prefs.edit { putBoolean("sync_watched_progress", enabled) }
        _syncWatchedProgress.value = enabled
    }

    private val _onboardingCompleted = MutableStateFlow(prefs.getBoolean(KEY_ONBOARDING, false))
    val onboardingCompleted = _onboardingCompleted.asStateFlow()

    private val _completionThresholds = MutableStateFlow(readCompletionThresholds())
    val completionThresholds = _completionThresholds.asStateFlow()

    private fun readCompletionThresholds(): Map<Long, Int> =
        prefs.all
            .mapNotNull { (key, value) ->
                key.removePrefix("completion_threshold_")
                    .takeIf { key.startsWith("completion_threshold_") }
                    ?.toLongOrNull()
                    ?.let { id -> (value as? Int)?.let { id to it.coerceIn(90, 95) } }
            }
            .toMap()

    fun registerAnime(anime: Anime) {
        val confirmed = anime.localIds
        val canonical =
            confirmed.firstOrNull { it < 0 }
                ?: confirmed.firstNotNullOfOrNull { id ->
                    prefs.getLong("completion_alias_$id", id).takeIf { it != id }
                }
                ?: confirmed.first()
        val ids = (confirmed + confirmed.flatMap(::animeAliases)).distinct()
        val threshold = ids.firstNotNullOfOrNull { _completionThresholds.value[it] }
        prefs.edit {
            ids.forEach { id ->
                putLong("completion_alias_$id", canonical)
                if (threshold != null) putInt("completion_threshold_$id", threshold)
            }
        }
        _completionThresholds.value = readCompletionThresholds()
    }

    fun animeAliases(animeId: Long): List<Long> {
        val canonical = prefs.getLong("completion_alias_$animeId", animeId)
        return (prefs.all.mapNotNull { (key, value) ->
                if (key.startsWith("completion_alias_") && value == canonical)
                    key.removePrefix("completion_alias_").toLongOrNull()
                else null
            } + animeId)
            .distinct()
    }

    fun setCompletionThreshold(animeId: Long, percent: Int?) {
        prefs.edit {
            animeAliases(animeId).forEach { id ->
                if (percent == null) remove("completion_threshold_$id")
                else putInt("completion_threshold_$id", percent.coerceIn(90, 95))
            }
        }
        _completionThresholds.value = readCompletionThresholds()
    }

    fun setSyncIntervalMinutes(minutes: Int) {
        val interval = minutes.coerceIn(5, 120)
        prefs.edit { putInt(KEY_SYNC_INTERVAL, interval) }
        _syncIntervalMinutes.value = interval
    }

    fun completeOnboarding() {
        prefs.edit { putBoolean(KEY_ONBOARDING, true) }
        _onboardingCompleted.value = true
    }

    fun setPreferredDubbing(dubbing: String) {
        val trimmed = dubbing.trim()
        prefs.edit { putString(KEY_PREFERRED_DUBBING, trimmed) }
        // Preserve spaces while editing multi-word studio names.
        _preferredDubbing.value = dubbing
    }

    fun setUseExternalPlayer(useExternal: Boolean) {
        prefs.edit { putBoolean(KEY_USE_EXTERNAL_PLAYER, useExternal) }
        _useExternalPlayer.value = useExternal
    }

    fun setExternalPlayerPackage(pkg: String) {
        prefs.edit { putString(KEY_EXTERNAL_PLAYER_PKG, pkg) }
        _externalPlayerPackage.value = pkg
    }

    fun animeDubbing(anime: Anime): AnimeDubbingPreference? =
        anime.localIds.firstNotNullOfOrNull { id ->
            val key = "anime_dubbing_$id"
            if (prefs.contains(key))
                AnimeDubbingPreference(prefs.getString(key, null)?.takeIf { it.isNotBlank() })
            else null
        }

    fun setAnimeDubbing(anime: Anime, dubbing: String?) {
        val normalized = dubbing?.trim().orEmpty()
        if (
            anime.localIds.all {
                prefs.contains("anime_dubbing_$it") &&
                    prefs.getString("anime_dubbing_$it", null) == normalized
            }
        )
            return
        prefs.edit { anime.localIds.forEach { putString("anime_dubbing_$it", normalized) } }
    }

    companion object {
        private const val KEY_PREFERRED_DUBBING = "preferred_dubbing"
        private const val KEY_USE_EXTERNAL_PLAYER = "use_external_player"
        private const val KEY_EXTERNAL_PLAYER_PKG = "external_player_pkg"
        private const val KEY_SYNC_INTERVAL = "shikimori_sync_interval_minutes"
        private const val KEY_ONBOARDING = "onboarding_completed"
        const val DEFAULT_SYNC_INTERVAL = 15

        const val PACKAGE_MPV = "is.xyz.mpv"
        const val PACKAGE_MPVEX = "app.marlboroadvance.mpvex"
        const val PACKAGE_VLC = "org.videolan.vlc"
        const val PACKAGE_CHOOSER = "system_chooser"
    }
}
