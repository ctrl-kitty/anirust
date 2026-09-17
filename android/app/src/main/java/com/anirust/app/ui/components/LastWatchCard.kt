package com.anirust.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.anirust.app.domain.model.WatchHistoryItem

@Composable
fun LastWatchCard(
    lastWatch: WatchHistoryItem,
    onResume: () -> Unit,
    onOpenExternal: () -> Unit,
    onClickAnime: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClickAnime,
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Продолжить просмотр", style = MaterialTheme.typography.labelLarge)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                AnimePoster(lastWatch.animePoster, Modifier.width(80.dp).height(112.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        lastWatch.animeTitle,
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "Серия " + lastWatch.episodeNumber,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    lastWatch.dubbing?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            if (lastWatch.durationMs > 0) {
                LinearProgressIndicator(
                    progress = { lastWatch.progressFraction },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    (lastWatch.playbackPositionMs / 60_000).toString() +
                        " из " +
                        (lastWatch.durationMs / 60_000) +
                        " мин",
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onResume, modifier = Modifier.weight(1f).heightIn(min = 52.dp)) {
                    Icon(Icons.Filled.PlayArrow, null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (lastWatch.resumePositionMs == 0L) "Смотреть" else "Продолжить")
                }
                FilledTonalIconButton(onClick = onOpenExternal, modifier = Modifier.size(52.dp)) {
                    Icon(Icons.AutoMirrored.Outlined.OpenInNew, "Открыть во внешнем плеере")
                }
            }
        }
    }
}
