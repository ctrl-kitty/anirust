package com.anirust.app.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.anirust.app.ui.components.AppSnackbarHost
import com.anirust.app.ui.components.FilterTabChip
import com.anirust.app.ui.components.PlayerChoices
import kotlinx.coroutines.launch

@Composable
fun OnboardingScreen(
    settings: com.anirust.app.data.repository.SettingsRepository,
    onFinish: (Boolean) -> Unit,
) {
    val dubbing by settings.preferredDubbing.collectAsState()
    val useExternal by settings.useExternalPlayer.collectAsState()
    val player by settings.externalPlayerPackage.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var page by rememberSaveable { mutableIntStateOf(0) }
    BackHandler(page > 0) { page-- }
    val titles =
        listOf(
            "Любимые истории\nвсегда рядом",
            "Выбери свой плеер",
            "Твои списки.\nНа всех устройствах.",
        )
    val icons =
        listOf(Icons.Outlined.AutoAwesome, Icons.Outlined.PlayCircle, Icons.Outlined.CloudSync)
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val compact = maxHeight < 500.dp
        Column(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
            Row(
                Modifier.fillMaxWidth().padding(top = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (page > 0)
                    IconButton(onClick = { page-- }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Предыдущий шаг")
                    }
                Text("AniRust", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                TextButton(onClick = { onFinish(false) }) { Text("Пропустить") }
            }
            AnimatedContent(
                targetState = page,
                modifier = Modifier.weight(1f),
                transitionSpec = {
                    val direction = if (targetState > initialState) 1 else -1
                    (fadeIn(tween(320)) +
                            slideInHorizontally(tween(380, easing = FastOutSlowInEasing)) {
                                it / 5 * direction
                            })
                        .togetherWith(
                            fadeOut(tween(180)) +
                                slideOutHorizontally(tween(280)) { -it / 8 * direction }
                        )
                },
                label = "onboarding_page",
            ) { step ->
                Column(
                    Modifier.fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    if (compact) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(20.dp),
                        ) {
                            OnboardingHero(icons[step], Modifier.size(72.dp), compact = true)
                            Text(titles[step], style = MaterialTheme.typography.headlineSmall)
                        }
                    } else {
                        OnboardingHero(
                            icons[step],
                            Modifier.fillMaxWidth().height(if (step == 1) 96.dp else 132.dp),
                        )
                        Text(titles[step], style = MaterialTheme.typography.headlineLarge)
                    }
                    when (step) {
                        0 -> {
                            Text(
                                "Найди аниме, выбери озвучку и продолжай с того места, где остановился.",
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            Text("Любимая озвучка", style = MaterialTheme.typography.titleMedium)
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(
                                    listOf(
                                        "ТО Дубляжная",
                                        "AniLibria",
                                        "Dream Cast",
                                        "StudioBand",
                                        "Субтитры",
                                    )
                                ) { name ->
                                    FilterTabChip(
                                        name,
                                        dubbing == name,
                                        { settings.setPreferredDubbing(name) },
                                    )
                                }
                            }
                            OutlinedTextField(
                                dubbing,
                                settings::setPreferredDubbing,
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                label = { Text("Своя озвучка") },
                            )
                        }
                        1 -> {
                            Text("Выбери плеер. Позже его можно поменять в настройках.")
                            PlayerChoices(
                                useExternal,
                                player,
                                onSelect = { pkg ->
                                    if (pkg != null) settings.setExternalPlayerPackage(pkg)
                                    settings.setUseExternalPlayer(pkg != null)
                                },
                                onError = { message ->
                                    scope.launch { snackbar.showSnackbar(message) }
                                },
                            )
                        }
                        else -> {
                            Feature(
                                Icons.Outlined.Bookmarks,
                                "Подключи Shikimori",
                                "Твои списки, оценки и число просмотренных серий будут доступны здесь. Вход через браузер — затем вставь одноразовый код.",
                            )
                            Feature(
                                Icons.Outlined.Sync,
                                "Синхронизация без суеты",
                                "При старте и, по умолчанию, каждые 15 минут, пока приложение открыто. Интервал можно изменить; ручное обновление доступно в списках.",
                            )
                            Text(
                                "Можно начать без аккаунта: поиск, просмотр, история и локальные закладки уже доступны. Точная позиция видео хранится только на устройстве.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
            ) {
                repeat(3) { index ->
                    val width by
                        animateDpAsState(
                            if (index == page) 28.dp else 8.dp,
                            tween(260),
                            label = "page_indicator",
                        )
                    Box(
                        Modifier.width(width)
                            .height(8.dp)
                            .clip(CircleShape)
                            .background(
                                if (index == page) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outlineVariant
                            )
                    )
                }
            }
            Button(
                onClick = { if (page < 2) page++ else onFinish(true) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            ) {
                Text(if (page < 2) "Дальше" else "Войти в Shikimori")
            }
            if (page == 2)
                TextButton(onClick = { onFinish(false) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Начать без аккаунта")
                }
            else Spacer(Modifier.height(16.dp))
        }
        Box(Modifier.align(Alignment.BottomCenter)) { AppSnackbarHost(snackbar) }
    }
}

@Composable
private fun OnboardingHero(icon: ImageVector, modifier: Modifier, compact: Boolean = false) {
    Box(
        modifier
            .clip(MaterialTheme.shapes.extraLarge)
            .background(
                Brush.linearGradient(
                    listOf(
                        MaterialTheme.colorScheme.primaryContainer,
                        MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.55f),
                    )
                )
            ),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.65f),
        ) {
            Icon(
                icon,
                null,
                Modifier.padding(if (compact) 12.dp else 24.dp).size(if (compact) 32.dp else 56.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun Feature(icon: ImageVector, title: String, description: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Icon(
            icon,
            null,
            Modifier.padding(top = 4.dp).size(24.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
