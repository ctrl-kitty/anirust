package com.anirust.app.ui.navigation

import android.net.Uri
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String, val title: String, val icon: ImageVector? = null) {
    data object Home : Screen("home", "Главная", Icons.Default.Home)

    data object Search : Screen("search", "Поиск", Icons.Default.Search)

    data object Favorites : Screen("favorites", "Списки", Icons.Default.Bookmarks)

    data object History : Screen("history", "История", Icons.Default.History)

    data object Settings : Screen("settings", "Настройки", Icons.Default.Settings)

    data object ShikimoriAccount : Screen("shikimori/account", "Аккаунт Shikimori")

    data object ShikimoriLists : Screen("shikimori/lists", "Списки Shikimori")

    data object Details : Screen("details/{animeId}", "Информация") {
        fun createRoute(animeId: Long) = "details/$animeId"
    }

    data object Player : Screen("player/{animeId}/{episodeNumber}?dubbing={dubbing}", "Плеер") {
        fun createRoute(animeId: Long, episodeNumber: Int, dubbing: String? = null): String {
            val dub = dubbing?.let(Uri::encode) ?: ""
            return "player/$animeId/$episodeNumber?dubbing=$dub"
        }
    }
}

val BottomNavItems =
    listOf(Screen.Home, Screen.Search, Screen.Favorites, Screen.History, Screen.Settings)
