package com.anirust.app.ui.account

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.anirust.app.ui.components.LoadingView

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountScreen(viewModel: ShikimoriViewModel, onBack: () -> Unit, onOpenLists: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // The one-time authorization code must not enter a saved-instance-state Bundle.
    var code by remember { mutableStateOf("") }
    var logout by rememberSaveable { mutableStateOf(false) }
    fun open(url: String) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
        } catch (_: Exception) {
            Toast.makeText(context, "Не удалось открыть браузер", Toast.LENGTH_LONG).show()
        }
    }
    LaunchedEffect(state.user?.id, state.needsLogin) {
        if (state.user != null && !state.needsLogin) {
            code = ""
        }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Аккаунт Shikimori") },
                navigationIcon = {
                    IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") }
                },
            )
        }
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (state.initializing) item { LoadingView() }
            if (state.user != null)
                item {
                    Card {
                        Column(
                            Modifier.fillMaxWidth().padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Icon(Icons.Outlined.AccountCircle, null, Modifier.size(40.dp))
                            Text(
                                state.user!!.nickname,
                                style = MaterialTheme.typography.headlineSmall,
                            )
                            Text(
                                "В списках: " + state.rates.size,
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            Text(
                                "Статусы, оценки и число просмотренных серий синхронизируются с аккаунтом. " +
                                    "История просмотра и выбор озвучки остаются на устройстве.",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Button(onOpenLists) { Text("Открыть списки Shikimori") }
                            OutlinedButton(
                                onClick = viewModel::sync,
                                enabled = !state.busy && !state.needsLogin,
                            ) {
                                Icon(Icons.Outlined.Sync, null)
                                Spacer(Modifier.width(8.dp))
                                Text("Синхронизировать")
                            }
                            TextButton(onClick = { logout = true }, enabled = !state.busy) {
                                Text("Отключить аккаунт")
                            }
                        }
                    }
                }
            if (state.error != null)
                item {
                    Text(
                        state.error!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            if (state.busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            if (!state.initializing && (state.user == null || state.needsLogin)) {
                item {
                    Text(
                        if (state.needsLogin) "Подключить заново" else "Подключи свои списки",
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Text(
                        "Разреши AniRust доступ к спискам на сайте Shikimori, затем вставь полученный код. Пароль вводится только в браузере.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                item {
                    Card(
                        colors =
                            CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer
                            )
                    ) {
                        Column(
                            Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                "1. Разреши доступ в браузере",
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                "Войди в свой аккаунт Shikimori и подтверди доступ AniRust к спискам аниме."
                            )
                            FilledTonalButton(
                                onClick = { open(viewModel.authorizationUrl()) },
                                enabled = !state.busy,
                            ) {
                                Text("Войти через Shikimori")
                            }
                        }
                    }
                }
                item {
                    OutlinedTextField(
                        code,
                        { code = it },
                        Modifier.fillMaxWidth(),
                        label = { Text("2. Код авторизации из браузера") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        supportingText = {
                            Text("Скопируй одноразовый код после подтверждения доступа.")
                        },
                        enabled = !state.busy,
                    )
                }
                item {
                    Button(
                        onClick = { viewModel.signIn(code) },
                        enabled = code.isNotBlank() && !state.busy,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Подключить Shikimori")
                    }
                    Text(
                        "Токены твоего аккаунта зашифрованы ключом Android Keystore и исключены из резервных копий.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
    if (logout)
        AlertDialog(
            onDismissRequest = { logout = false },
            title = { Text("Отключить Shikimori?") },
            text = {
                Text(
                    "Токены и сохранённая копия списков будут удалены с устройства. Списки на сайте и локальные закладки не изменятся. Доступ можно отозвать на сайте Shikimori."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        logout = false
                        viewModel.signOut()
                    }
                ) {
                    Text("Отключить")
                }
            },
            dismissButton = { TextButton(onClick = { logout = false }) { Text("Отмена") } },
        )
}
