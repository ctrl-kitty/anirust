package com.anirust.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val LightColors =
    lightColorScheme(
        primary = Color(0xFF62539A),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFE9DDFF),
        onPrimaryContainer = Color(0xFF28164F),
        secondary = Color(0xFF615B71),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFE7DFF4),
        onSecondaryContainer = Color(0xFF1E192B),
        tertiary = Color(0xFF85524A),
        onTertiary = Color.White,
        tertiaryContainer = Color(0xFFFFDAD2),
        onTertiaryContainer = Color(0xFF34110C),
        background = Color(0xFFFEF7FF),
        onBackground = Color(0xFF1D1B20),
        surface = Color(0xFFFEF7FF),
        onSurface = Color(0xFF1D1B20),
        surfaceVariant = Color(0xFFE7E0EC),
        onSurfaceVariant = Color(0xFF49454F),
        surfaceContainerLowest = Color.White,
        surfaceContainerLow = Color(0xFFF8F1FA),
        surfaceContainer = Color(0xFFF2EBF4),
        surfaceContainerHigh = Color(0xFFECE5EE),
        surfaceContainerHighest = Color(0xFFE6DFE8),
        outline = Color(0xFF7A757F),
        outlineVariant = Color(0xFFCBC4CF),
    )
private val DarkColors =
    darkColorScheme(
        primary = Color(0xFFCDBDFF),
        onPrimary = Color(0xFF33255F),
        primaryContainer = Color(0xFF4A3B7C),
        onPrimaryContainer = Color(0xFFE9DDFF),
        secondary = Color(0xFFCBC2DB),
        onSecondary = Color(0xFF332D41),
        secondaryContainer = Color(0xFF494357),
        onSecondaryContainer = Color(0xFFE7DFF4),
        tertiary = Color(0xFFF9B8AA),
        onTertiary = Color(0xFF4F251E),
        tertiaryContainer = Color(0xFF6A3B33),
        onTertiaryContainer = Color(0xFFFFDAD2),
        background = Color(0xFF141218),
        onBackground = Color(0xFFE6E0E9),
        surface = Color(0xFF141218),
        onSurface = Color(0xFFE6E0E9),
        surfaceVariant = Color(0xFF49454F),
        onSurfaceVariant = Color(0xFFCBC4CF),
        surfaceContainerLowest = Color(0xFF0F0D13),
        surfaceContainerLow = Color(0xFF1D1B20),
        surfaceContainer = Color(0xFF211F26),
        surfaceContainerHigh = Color(0xFF2B2930),
        surfaceContainerHighest = Color(0xFF36343B),
        outline = Color(0xFF948F99),
        outlineVariant = Color(0xFF49454F),
    )

@Composable
fun AnirustTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = Typography,
        shapes =
            Shapes(
                extraSmall = RoundedCornerShape(8.dp),
                small = RoundedCornerShape(12.dp),
                medium = RoundedCornerShape(20.dp),
                large = RoundedCornerShape(28.dp),
                extraLarge = RoundedCornerShape(32.dp),
            ),
        content = content,
    )
}
