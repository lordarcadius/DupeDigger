package com.vipuljha.dupedigger.core.designsystem

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.roundToIntRect
import androidx.compose.ui.window.PopupPositionProvider

// PrimeOS doesn't report its caption bar and taskbar as insets, so popups clamp to the measured content area.
val LocalContentBounds = compositionLocalOf { mutableStateOf(IntRect.Zero) }

@Composable
fun MeasureContentBounds(target: MutableState<IntRect>) = Spacer(
    Modifier
        .fillMaxSize()
        .windowInsetsPadding(WindowInsets.safeDrawing)
        .onGloballyPositioned { target.value = it.boundsInWindow().roundToIntRect() },
)

fun IntOffset.clampInto(area: IntRect, size: IntSize) = IntOffset(
    x.coerceIn(area.left, maxOf(area.left, area.right - size.width)),
    y.coerceIn(area.top, maxOf(area.top, area.bottom - size.height)),
)

private fun IntRect.orWindow(windowSize: IntSize) = if (width > 0 && height > 0) this else IntRect(IntOffset.Zero, windowSize)

class BoundedTooltipPositionProvider(private val bounds: IntRect, private val spacing: Int) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val area = bounds.orWindow(windowSize)
        var y = anchorBounds.top - popupContentSize.height - spacing
        if (y < area.top) y = anchorBounds.bottom + spacing
        return IntOffset(anchorBounds.center.x - popupContentSize.width / 2, y).clampInto(area, popupContentSize)
    }
}

@Composable
fun rememberBoundedTooltipPositionProvider(): PopupPositionProvider {
    val bounds by LocalContentBounds.current
    val spacing = with(LocalDensity.current) { Dimens.spaceXs.roundToPx() }
    return remember(bounds, spacing) { BoundedTooltipPositionProvider(bounds, spacing) }
}
