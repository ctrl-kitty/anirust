package com.anirust.app.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.anirust.app.BuildConfig
import com.anirust.app.data.repository.SettingsRepository
import com.anirust.app.ui.components.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    modifier: Modifier = Modifier,
    onOpenAccount: (() -> Unit)? = null,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val focus = LocalFocusManager.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var clearTarget by rememberSaveable { mutableStateOf<String?>(null) }
    Scaffold(modifier.fillMaxSize(), snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            item { ScreenHeading("Настройки", "Пусть всё будет по-твоему") }
            if (onOpenAccount != null)
                item {
                    Card(
                        onClick = onOpenAccount,
                        colors =
                            CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            ),
                    ) {
                        ListItem(
                            headlineContent = { Text("Аккаунт Shikimori") },
                            supportingContent = { Text("Подключение и синхронизация списков") },
                            leadingContent = { Icon(Icons.Outlined.CloudSync, null) },
                            trailingContent = { Icon(Icons.Outlined.ChevronRight, null) },
                            colors =
                                ListItemDefaults.colors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer
                                ),
                        )
                    }
                }
            item {
                SettingsGroup("Озвучка") {
                    Text("Предпочитаемая озвучка", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Выберем её автоматически, если она доступна у аниме.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        state.preferredDubbing,
                        viewModel::setPreferredDubbing,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Студия или перевод") },
                        placeholder = { Text("Например, Dream Cast") },
                        singleLine = true,
                        shape = MaterialTheme.shapes.medium,
                        trailingIcon = {
                            if (state.preferredDubbing.isNotEmpty())
                                IconButton(onClick = { viewModel.setPreferredDubbing("") }) {
                                    Icon(Icons.Outlined.Close, "Сбросить озвучку")
                                }
                        },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        listOf("AniLibria", "Dream Cast", "StudioBand", "2x2", "AniDUB", "Субтитры")
                            .forEach { name ->
                                FilterTabChip(
                                    name,
                                    state.preferredDubbing.equals(name, ignoreCase = true),
                                    {
                                        viewModel.setPreferredDubbing(name)
                                        focus.clearFocus()
                                    },
                                )
                            }
                    }
                }
            }
            item {
                SettingsGroup("Просмотр") {
                    Row(
                        Modifier.fillMaxWidth()
                            .toggleable(
                                state.useExternalPlayer,
                                role = Role.Switch,
                                onValueChange = viewModel::setUseExternalPlayer,
                            )
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Column(
                            Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                "Открывать во внешнем плеере",
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                if (state.useExternalPlayer)
                                    "Видео откроется в выбранном приложении"
                                else "Сейчас используется встроенный плеер",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(state.useExternalPlayer, onCheckedChange = null)
                    }
                    AnimatedVisibility(state.useExternalPlayer) {
                        Column(Modifier.selectableGroup()) {
                            HorizontalDivider()
                            listOf(
                                    SettingsRepository.PACKAGE_MPV to "MPV Android",
                                    SettingsRepository.PACKAGE_MPVEX to "mpvEx",
                                    SettingsRepository.PACKAGE_VLC to "VLC for Android",
                                    SettingsRepository.PACKAGE_CHOOSER to "Спрашивать каждый раз",
                                )
                                .forEach { (pkg, label) ->
                                    Row(
                                        Modifier.fillMaxWidth()
                                            .heightIn(min = 56.dp)
                                            .selectable(
                                                state.externalPlayerPackage == pkg,
                                                role = Role.RadioButton,
                                                onClick = {
                                                    viewModel.setExternalPlayerPackage(pkg)
                                                },
                                            ),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    ) {
                                        RadioButton(
                                            state.externalPlayerPackage == pkg,
                                            onClick = null,
                                        )
                                        Text(label, style = MaterialTheme.typography.bodyLarge)
                                    }
                                }
                        }
                    }
                    Text(
                        "Встроенный плеер сохраняет позицию и поддерживает заголовки потоков. " +
                            "Внешние приложения управляют прогрессом самостоятельно.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item {
                SettingsGroup("На этом устройстве") {
                    Text(
                        "Списки и история хранятся локально. Удалённые данные нельзя восстановить.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(
                        onClick = { clearTarget = "history" },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Outlined.History, null)
                        Spacer(Modifier.width(12.dp))
                        Text("Очистить историю просмотров", Modifier.weight(1f))
                    }
                    TextButton(
                        onClick = { clearTarget = "favorites" },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Outlined.Bookmarks, null)
                        Spacer(Modifier.width(12.dp))
                        Text("Очистить список избранного", Modifier.weight(1f))
                    }
                }
            }
            item {
                Row(
                    Modifier.fillMaxWidth().padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.primaryContainer,
                    ) {
                        Icon(
                            Icons.Outlined.PlayCircle,
                            null,
                            Modifier.padding(16.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            "AniRust · " + BuildConfig.VERSION_NAME,
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            "Сделано для твоих любимых историй",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            "Тема следует настройкам устройства",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
    clearTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { clearTarget = null },
            icon = { Icon(Icons.Outlined.DeleteOutline, null) },
            title = {
                Text(if (target == "history") "Очистить историю?" else "Очистить избранное?")
            },
            text = {
                Text(
                    if (target == "history") "Все записи и сохранённые позиции будут удалены."
                    else "Все аниме будут удалены из твоих списков. История просмотра сохранится."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (target == "history") viewModel.clearAllHistory()
                        else viewModel.clearAllFavorites()
                        clearTarget = null
                        if (target == "history")
                            scope.launch { snackbar.showSnackbar("История очищена") }
                    }
                ) {
                    Text("Очистить", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { clearTarget = null }) { Text("Отмена") } },
        )
    }
}

@Composable
private fun SettingsGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            title,
            Modifier.padding(start = 12.dp),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Card(
            colors =
                CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                )
        ) {
            Column(
                Modifier.fillMaxWidth().padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                content = content,
            )
        }
    }
}
