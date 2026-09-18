package com.anirust.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.anirust.app.domain.model.WatchHistoryItem

@Composable
fun CompletionDialog(item: WatchHistoryItem, onConfirm: (Boolean) -> Unit, onDismiss: () -> Unit) {
    var rememberPercent by rememberSaveable(item.historyId) { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Серия уже просмотрена?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "${item.animeTitle} · серия ${item.episodeNumber}\nПросмотрено ${item.progressPercent}%. Можно скрыть её и перейти к следующей."
                )
                Row(
                    Modifier.fillMaxWidth()
                        .toggleable(
                            rememberPercent,
                            role = Role.Checkbox,
                            onValueChange = { rememberPercent = it },
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(rememberPercent, onCheckedChange = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Для этого аниме отмечать автоматически с ${item.progressPercent}%")
                }
                Text(
                    "Без этого правила автоматически отмечаем с 95%. Правило можно сбросить на странице аниме.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(rememberPercent) }) { Text("Да, просмотрена") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Ещё не досмотрел") } },
    )
}
