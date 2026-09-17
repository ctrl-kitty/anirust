package com.anirust.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontWeight

private val Baseline = Typography()
val Typography =
    Typography(
        displaySmall = Baseline.displaySmall.copy(fontWeight = FontWeight.Bold),
        headlineLarge = Baseline.headlineLarge.copy(fontWeight = FontWeight.Bold),
        headlineMedium = Baseline.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
        headlineSmall = Baseline.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
        titleLarge = Baseline.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    )
