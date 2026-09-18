package com.anirust.app.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

@Composable
fun ShimmerBlock(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val sweep =
        transition.animateFloat(
            initialValue = -1f,
            targetValue = 2f,
            animationSpec =
                infiniteRepeatable(tween(1400, delayMillis = 180, easing = LinearEasing)),
            label = "highlight",
        )
    val base = MaterialTheme.colorScheme.surfaceContainerHighest
    val highlight = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
    Box(
        modifier.clip(MaterialTheme.shapes.small).testTag("shimmer").drawBehind {
            // Read animation state in drawing, so every frame does not recompose the list.
            val x = size.width * sweep.value
            drawRect(base)
            drawRect(
                Brush.linearGradient(
                    colors = listOf(base.copy(alpha = 0f), highlight, base.copy(alpha = 0f)),
                    start = Offset(x - size.width, 0f),
                    end = Offset(x, size.height),
                )
            )
        }
    )
}

@Composable
fun LoadingSkeleton(message: String, modifier: Modifier = Modifier, posters: Boolean = false) {
    Column(
        modifier.fillMaxWidth().padding(vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (posters) {
            repeat(2) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    repeat(2) {
                        Column(
                            Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            ShimmerBlock(Modifier.fillMaxWidth().aspectRatio(0.72f))
                            ShimmerBlock(Modifier.fillMaxWidth(0.86f).height(16.dp))
                            ShimmerBlock(Modifier.fillMaxWidth(0.5f).height(12.dp))
                        }
                    }
                }
            }
        } else {
            repeat(3) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    ShimmerBlock(Modifier.width(76.dp).height(106.dp))
                    Column(
                        Modifier.weight(1f).padding(top = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        ShimmerBlock(Modifier.fillMaxWidth(0.85f).height(20.dp))
                        ShimmerBlock(Modifier.fillMaxWidth().height(14.dp))
                        ShimmerBlock(Modifier.fillMaxWidth(0.55f).height(14.dp))
                    }
                }
            }
        }
    }
}
