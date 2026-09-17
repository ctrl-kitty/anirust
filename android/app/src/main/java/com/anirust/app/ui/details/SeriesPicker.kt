package com.anirust.app.ui.details

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.anirust.app.domain.model.SeriesEntry

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeriesPicker(series: List<SeriesEntry>, selectedId: String?, onSelect: (String) -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    val current = series.firstOrNull { it.id == selectedId }
    OutlinedCard(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "Сезоны и части · ${series.size}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    current?.title ?: "Выбрать часть",
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text("Открыть порядок просмотра", style = MaterialTheme.typography.bodySmall)
            }
            Icon(Icons.Outlined.ChevronRight, null)
        }
    }
    if (open) {
        val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val listState =
            rememberLazyListState(
                initialFirstVisibleItemIndex =
                    series.indexOfFirst { it.id == selectedId }.coerceAtLeast(0)
            )
        ModalBottomSheet(onDismissRequest = { open = false }, sheetState = sheet) {
            Text(
                "Сезоны и части",
                Modifier.padding(horizontal = 24.dp),
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                "Порядок просмотра из каталога",
                Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LazyColumn(
                Modifier.fillMaxWidth().weight(1f, fill = false),
                state = listState,
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                itemsIndexed(series, key = { _, entry -> entry.id }) { index, entry ->
                    val active = entry.id == selectedId
                    Card(
                        onClick = {
                            open = false
                            if (!active) onSelect(entry.id)
                        },
                        modifier = Modifier.fillMaxWidth().semantics { selected = active },
                        colors =
                            CardDefaults.cardColors(
                                containerColor =
                                    if (active) MaterialTheme.colorScheme.secondaryContainer
                                    else MaterialTheme.colorScheme.surfaceContainerLow
                            ),
                    ) {
                        Row(
                            Modifier.padding(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                (index + 1).toString().padStart(2, '0'),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Column(
                                Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                // Full titles wrap here; no guessed season numbers or ambiguous
                                // truncation.
                                Text(entry.title, style = MaterialTheme.typography.titleMedium)
                                if (active)
                                    Text(
                                        "Текущая часть",
                                        style = MaterialTheme.typography.labelMedium,
                                    )
                            }
                            if (active) Icon(Icons.Outlined.CheckCircle, null)
                        }
                    }
                }
            }
        }
    }
}
