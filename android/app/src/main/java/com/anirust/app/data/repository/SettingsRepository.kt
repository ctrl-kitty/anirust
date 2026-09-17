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
        MutableStateFlow(prefs.getString(KEY_EXTERNAL_PLAYER_PKG, PACKAGE_MPV) ?: PACKAGE_MPV)
    val externalPlayerPackage: StateFlow<String> = _externalPlayerPackage.asStateFlow()

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

        const val PACKAGE_MPV = "is.xyz.mpv"
        const val PACKAGE_MPVEX = "app.marlboroadvance.mpvex"
        const val PACKAGE_VLC = "org.videolan.vlc"
        const val PACKAGE_CHOOSER = "system_chooser"
    }
}
