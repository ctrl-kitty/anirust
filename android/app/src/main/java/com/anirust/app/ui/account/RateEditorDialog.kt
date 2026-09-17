package com.anirust.app.ui.account

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.anirust.app.data.remote.shikimori.*
import com.anirust.app.domain.model.Anime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RateEditorDialog(
    anime: Anime,
    existing: ShikimoriRate?,
    busy: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, Int, Int) -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    var status by rememberSaveable(anime.id) { mutableStateOf(existing?.status ?: "planned") }
    var score by rememberSaveable(anime.id) { mutableStateOf((existing?.score ?: 0).toString()) }
    var episodes by
        rememberSaveable(anime.id) { mutableStateOf((existing?.episodes ?: 0).toString()) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val scoreValue = score.toIntOrNull()
    val episodeValue = episodes.toIntOrNull()
    val validScore = scoreValue != null && scoreValue in 0..10
    val validEpisodes =
        episodeValue != null &&
            episodeValue >= 0 &&
            (anime.episodesCount == null || episodeValue <= anime.episodesCount)
    val currentBusy by rememberUpdatedState(busy)
    val sheetState =
        rememberModalBottomSheetState(
            skipPartiallyExpanded = true,
            confirmValueChange = { it != SheetValue.Hidden || !currentBusy },
        )
    ModalBottomSheet(onDismissRequest = { if (!busy) onDismiss() }, sheetState = sheetState) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.9f).padding(horizontal = 24.dp)) {
            Text("Список Shikimori", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(16.dp))
            Column(
                Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(anime.displayTitle, style = MaterialTheme.typography.titleMedium)
                Text("Статус", style = MaterialTheme.typography.labelLarge)
                Column(Modifier.fillMaxWidth().selectableGroup()) {
                    ShikimoriListStatus.entries.forEach { item ->
                        Row(
                            Modifier.fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .selectable(
                                    selected = status == item.apiValue,
                                    enabled = !busy,
                                    role = Role.RadioButton,
                                    onClick = { status = item.apiValue },
                                ),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = status == item.apiValue,
                                onClick = null,
                                enabled = !busy,
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(item.title)
                        }
                    }
                }
                OutlinedTextField(
                    score,
                    { score = it.filter(Char::isDigit).take(2) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Моя оценка · 0–10") },
                    supportingText = { Text("0 — без оценки") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    isError = !validScore,
                    enabled = !busy,
                )
                OutlinedTextField(
                    episodes,
                    { episodes = it.filter(Char::isDigit).take(6) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Просмотрено серий") },
                    supportingText = {
                        Text(
                            anime.episodesCount?.let { "Всего: " + it }
                                ?: "Число просмотренных серий"
                        )
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    isError = !validEpisodes,
                    enabled = !busy,
                )
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (onDelete != null)
                    TextButton(onClick = { confirmDelete = true }, enabled = !busy) {
                        Text("Удалить из Shikimori", color = MaterialTheme.colorScheme.error)
                    }
            }
            Row(
                Modifier.fillMaxWidth().padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
            ) {
                TextButton(onClick = onDismiss, enabled = !busy) { Text("Отмена") }
                Button(
                    onClick = { onSave(status, scoreValue!!, episodeValue!!) },
                    enabled = validScore && validEpisodes && !busy,
                ) {
                    Text("Сохранить")
                }
            }
        }
    }
    if (confirmDelete)
        AlertDialog(
            onDismissRequest = { if (!busy) confirmDelete = false },
            title = { Text("Удалить из списка Shikimori?") },
            text = {
                Text(
                    "Запись «" +
                        anime.displayTitle +
                        "» будет удалена и на сайте. Локальная история просмотра сохранится."
                )
            },
            confirmButton = {
                TextButton(onClick = { onDelete?.invoke() }, enabled = !busy) { Text("Удалить") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }, enabled = !busy) { Text("Отмена") }
            },
        )
}
