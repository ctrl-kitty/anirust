package com.anirust.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.anirust.app.domain.model.Episode

@Composable
fun EpisodeItem(
    episode: Episode,
    isWatched: Boolean,
    currentSelectedDubbing: String?,
    onPlay: () -> Unit,
    onPlayExternal: () -> Unit,
    onSelectDubbing: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Card(
        onClick = onPlay,
        modifier = modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Row(
            Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color =
                    if (isWatched) MaterialTheme.colorScheme.secondaryContainer
                    else MaterialTheme.colorScheme.primaryContainer,
            ) {
                Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                    Icon(
                        if (isWatched) Icons.Outlined.Check else Icons.Outlined.PlayArrow,
                        if (isWatched) "Просмотрено" else null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(episode.displayTitle, style = MaterialTheme.typography.titleMedium)
                Text(
                    currentSelectedDubbing ?: "Выбрать озвучку · " + episode.voiceVariants.size,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Outlined.MoreVert, "Действия с серией " + episode.number)
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Выбрать озвучку") },
                        leadingIcon = { Icon(Icons.Outlined.RecordVoiceOver, null) },
                        onClick = {
                            menuOpen = false
                            onSelectDubbing()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Внешний плеер") },
                        leadingIcon = { Icon(Icons.AutoMirrored.Outlined.OpenInNew, null) },
                        onClick = {
                            menuOpen = false
                            onPlayExternal()
                        },
                    )
                }
            }
        }
    }
}
