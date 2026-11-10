package com.vipuljha.dupedigger.core.designsystem

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp

/**
 * One bar for a volume: everything in use, with the share duplicates take picked out inside it, and the rest free.
 * Both shares are fractions of the whole volume; the duplicate band is drawn at the right-hand end of the used part,
 * where it meets the free space, because that is the space a clean-up would hand back.
 */
@Composable
fun StorageMeter(
    usedFraction: Float,
    duplicateFraction: Float,
    modifier: Modifier = Modifier,
    usedColor: Color = MaterialTheme.colorScheme.primary,
    duplicateColor: Color = MaterialTheme.colorScheme.tertiary,
    trackColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
) {
    val used by animateFloatAsState(usedFraction.coerceIn(0f, 1f), stateChangeSpec(), label = "used")
    val duplicates by animateFloatAsState(duplicateFraction.coerceIn(0f, used), stateChangeSpec(), label = "duplicates")
    Canvas(modifier.fillMaxWidth().height(MeterHeight).clearAndSetSemantics {}) {
        val radius = CornerRadius(size.height / 2)
        drawRoundRect(trackColor, cornerRadius = radius)
        if (used > 0f) drawRoundRect(usedColor, size = Size(size.width * used, size.height), cornerRadius = radius)
        if (duplicates > 0f) {
            val width = size.width * duplicates
            drawRoundRect(duplicateColor, topLeft = Offset(size.width * used - width, 0f), size = Size(width, size.height), cornerRadius = radius)
        }
    }
}

private val MeterHeight = 12.dp
