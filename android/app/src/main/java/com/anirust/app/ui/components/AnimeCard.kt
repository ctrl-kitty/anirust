package com.anirust.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.ImageNotSupported
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.anirust.app.domain.model.Anime
import java.util.Locale

@Composable
fun AnimePoster(url: String?, modifier: Modifier = Modifier, title: String? = null) {
    var loaded by remember(url) { mutableStateOf(false) }
    var failed by remember(url) { mutableStateOf(false) }
    var attempt by remember(url) { mutableIntStateOf(0) }
    val context = LocalContext.current
    val missing = url.isNullOrBlank()
    Box(
        modifier
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        if (!missing && !loaded && !failed) ShimmerBlock(Modifier.matchParentSize())
        if (missing || failed)
            Column(
                Modifier.fillMaxWidth().padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (failed)
                    IconButton(
                        onClick = {
                            failed = false
                            attempt++
                        }
                    ) {
                        Icon(Icons.Outlined.Refresh, "Повторить загрузку обложки")
                    }
                else Icon(Icons.Outlined.ImageNotSupported, "Обложка отсутствует")
                title
                    ?.takeIf { it.isNotBlank() }
                    ?.let {
                        Text(
                            it,
                            modifier = Modifier.clearAndSetSemantics {},
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
            }
        if (!missing)
            key(url, attempt) {
                AsyncImage(
                    remember(url, attempt) { ImageRequest.Builder(context).data(url).build() },
                    contentDescription = null,
                    modifier = Modifier.matchParentSize(),
                    contentScale = ContentScale.Crop,
                    onSuccess = { loaded = true },
                    onError = {
                        loaded = false
                        failed = true
                    },
                )
            }
    }
}

@Composable
fun AnimeCard(anime: Anime, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Box {
            AnimePoster(
                anime.posterUrl,
                Modifier.fillMaxWidth().aspectRatio(0.72f),
                anime.displayTitle,
            )
            if (anime.score != null && anime.score > 0f) {
                Surface(
                    Modifier.padding(10.dp).align(Alignment.TopStart),
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                ) {
                    Row(
                        Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(Icons.Filled.Star, null, Modifier.size(14.dp))
                        Text(
                            String.format(Locale.getDefault(), "%.1f", anime.score),
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
            }
        }
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                anime.displayTitle,
                style = MaterialTheme.typography.titleSmall,
                minLines = 2,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                anime.episodesCount?.takeIf { it > 0 }?.let { "$it серий" } ?: anime.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
