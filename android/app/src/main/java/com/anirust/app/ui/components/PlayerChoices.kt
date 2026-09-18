package com.anirust.app.ui.components

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.anirust.app.data.repository.SettingsRepository
import com.anirust.app.ui.player.ExternalPlayerHelper

private data class PlayerChoice(
    val pkg: String,
    val title: String,
    val description: String,
    val url: String?,
)

private val players =
    listOf(
        PlayerChoice("", "Встроенный плеер", "Готов к работе · сохраняет прогресс", null),
        PlayerChoice(
            SettingsRepository.PACKAGE_MPVEX,
            "mpvEx",
            "Рекомендуем · сохраняет прогресс",
            "https://github.com/marlboro-advance/mpvEx/releases/latest",
        ),
        PlayerChoice(
            SettingsRepository.PACKAGE_MPV,
            "MPV Android",
            "Настройки видео и звука · прогресс может не вернуться",
            "https://github.com/mpv-android/mpv-android/releases/latest",
        ),
        PlayerChoice(
            SettingsRepository.PACKAGE_VLC,
            "VLC for Android",
            "Субтитры и аудиодорожки · прогресс может не вернуться",
            "https://play.google.com/store/apps/details?id=org.videolan.vlc",
        ),
        PlayerChoice(
            SettingsRepository.PACKAGE_CHOOSER,
            "Спрашивать каждый раз",
            "Выбор приложения при запуске серии",
            null,
        ),
    )

@Composable
fun PlayerChoices(
    useExternal: Boolean,
    selectedPackage: String,
    onSelect: (String?) -> Unit,
    onError: (String) -> Unit,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var installed by remember { mutableStateOf(emptySet<String>()) }
    DisposableEffect(context, lifecycle) {
        fun refresh() {
            installed =
                players
                    .filter {
                        it.url == null || ExternalPlayerHelper.isPackageInstalled(context, it.pkg)
                    }
                    .map { it.pkg }
                    .toSet()
        }
        refresh()
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh()
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    Column(
        Modifier.fillMaxWidth().selectableGroup(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        players.forEach { choice ->
            val available = choice.pkg in installed
            val selected =
                if (choice.pkg.isEmpty()) !useExternal
                else useExternal && selectedPackage == choice.pkg
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Row(
                        Modifier.fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .selectable(
                                selected,
                                enabled = available,
                                role = Role.RadioButton,
                                onClick = { onSelect(choice.pkg.takeIf { it.isNotEmpty() }) },
                            ),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        RadioButton(selected, onClick = null, enabled = available)
                        Column(
                            Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(choice.title, style = MaterialTheme.typography.titleSmall)
                            Text(
                                choice.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (choice.url != null)
                                Text(
                                    if (available) "Установлен" else "Не установлен",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                        }
                    }
                    if (!available && choice.url != null)
                        TextButton(
                            onClick = {
                                try {
                                    context.startActivity(
                                        Intent(Intent.ACTION_VIEW, choice.url.toUri())
                                    )
                                } catch (_: Exception) {
                                    onError("Не удалось открыть страницу установки")
                                }
                            }
                        ) {
                            Text("Установить ${choice.title}")
                        }
                }
            }
        }
    }
}
