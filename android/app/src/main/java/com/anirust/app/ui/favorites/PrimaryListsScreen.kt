package com.anirust.app.ui.favorites

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.anirust.app.domain.usecase.FavoritesUseCase
import com.anirust.app.ui.account.ShikimoriListsScreen
import com.anirust.app.ui.account.ShikimoriViewModel
import com.anirust.app.ui.components.LoadingView
import kotlinx.coroutines.launch

@Composable
fun PrimaryListsScreen(
    localViewModel: FavoritesViewModel,
    accountViewModel: ShikimoriViewModel?,
    favorites: FavoritesUseCase,
    onAccount: () -> Unit,
    onOpenAnime: (Long) -> Unit,
) {
    if (accountViewModel == null) {
        FavoritesScreen(localViewModel, onOpenAnime, onOpenShikimori = onAccount)
        return
    }
    val account by accountViewModel.state.collectAsStateWithLifecycle()
    val localFlow = remember(favorites) { favorites.getAllFavorites() }
    val local by localFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    var warningDismissed by rememberSaveable(account.user?.id) { mutableStateOf(false) }
    var replacing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    when {
        account.initializing -> LoadingView()
        account.user == null ->
            FavoritesScreen(localViewModel, onOpenAnime, onOpenShikimori = onAccount)
        else -> {
            ShikimoriListsScreen(accountViewModel, {}, onAccount, onOpenAnime, showBack = false)
            if (local.isNotEmpty() && !warningDismissed)
                AlertDialog(
                    onDismissRequest = { if (!replacing) warningDismissed = true },
                    title = { Text("Заменить локальные списки?") },
                    text = {
                        Text(
                            "Списки Shikimori станут основными и перезапишут локальные списки. " +
                                "Будет удалено локальных закладок: ${local.size}. Они не будут отправлены в Shikimori. " +
                                "Вместо них приложение использует списки аккаунта и их сохранённую копию. История просмотра и озвучки сохранятся."
                        )
                    },
                    confirmButton = {
                        TextButton(
                            enabled =
                                !replacing &&
                                    !account.busy &&
                                    account.lastSync > 0 &&
                                    account.error == null,
                            onClick = {
                                replacing = true
                                scope.launch {
                                    try {
                                        if (favorites.replaceWithShikimori())
                                            warningDismissed = true
                                    } finally {
                                        replacing = false
                                    }
                                }
                            },
                        ) {
                            Text("Заменить локальные")
                        }
                    },
                    dismissButton = {
                        TextButton(enabled = !replacing, onClick = { warningDismissed = true }) {
                            Text("Пока сохранить")
                        }
                    },
                )
        }
    }
}
