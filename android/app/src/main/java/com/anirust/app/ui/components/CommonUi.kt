package com.anirust.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun ScreenHeading(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    action: @Composable () -> Unit = {},
) {
    Row(
        modifier.fillMaxWidth().padding(top = 12.dp, bottom = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.headlineLarge,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        action()
    }
}

@Composable
fun AnirustSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: (String) -> Unit,
    placeholder: String = "Название аниме",
    modifier: Modifier = Modifier,
) {
    val focus = LocalFocusManager.current
    TextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier.fillMaxWidth(),
        placeholder = { Text(placeholder) },
        leadingIcon = { Icon(Icons.Outlined.Search, null) },
        trailingIcon = {
            if (query.isNotEmpty())
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Outlined.Close, "Очистить поиск")
                }
        },
        singleLine = true,
        shape = CircleShape,
        colors =
            TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
            ),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions =
            KeyboardActions(
                onSearch = {
                    focus.clearFocus()
                    onSearch(query)
                }
            ),
    )
}

@Composable
fun FilterTabChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(text) },
        modifier = modifier.heightIn(min = 48.dp),
        shape = CircleShape,
        leadingIcon =
            if (selected) {
                { Icon(Icons.Outlined.Check, null, Modifier.size(18.dp)) }
            } else null,
    )
}

@Composable
fun LoadingView(message: String = "Загружаем…", modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
    ) {
        CircularProgressIndicator(Modifier.size(40.dp))
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
fun EmptyView(
    message: String,
    subtitle: String = "",
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Outlined.VideoLibrary,
    action: @Composable () -> Unit = {},
) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.secondaryContainer,
        ) {
            Icon(
                icon,
                null,
                Modifier.padding(24.dp).size(40.dp),
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
        Text(message, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        if (subtitle.isNotBlank())
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        action()
    }
}

@Composable
fun ErrorView(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    EmptyView("Не удалось загрузить", message, modifier, Icons.Outlined.CloudOff) {
        Button(onClick = onRetry) {
            Icon(Icons.Outlined.Refresh, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Повторить")
        }
    }
}

@Composable
fun ResolvingDialog(onCancel: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCancel,
        icon = { CircularProgressIndicator(Modifier.size(32.dp)) },
        title = { Text("Готовим видео") },
        text = { Text("Получаем ссылку для внешнего плеера. Это может занять несколько секунд.") },
        confirmButton = { TextButton(onClick = onCancel) { Text("Отмена") } },
    )
}
