package com.vipuljha.dupedigger.core.designsystem

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** The one empty and error look: a tinted disc with an outlined glyph, a title, one sentence and an optional action. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun StateMessage(
    icon: ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    action: (@Composable () -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier
                .widthIn(max = Dimens.maxWidthEmptyState)
                .padding(horizontal = Dimens.spaceXxl)
                .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(Dimens.illustrationDisc)
                    .background(
                        if (isError) colors.errorContainer.copy(alpha = ErrorDiscAlpha) else colors.primaryContainer.copy(alpha = EmptyDiscAlpha),
                        CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, null, Modifier.size(Dimens.iconIllustration), tint = if (isError) colors.error else colors.primary)
            }
            Spacer(Modifier.height(StateDiscGap))
            Text(title, style = MaterialTheme.typography.titleLargeEmphasized, color = colors.onSurface, textAlign = TextAlign.Center)
            Spacer(Modifier.height(Dimens.spaceSm))
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant, textAlign = TextAlign.Center)
            if (action != null) {
                Spacer(Modifier.height(Dimens.spaceLg))
                action()
            }
        }
    }
}

private val StateDiscGap = 20.dp
private const val EmptyDiscAlpha = 0.4f
private const val ErrorDiscAlpha = 0.5f

@Stable
class Shimmer internal constructor(val base: Color, val highlight: Color, val progress: State<Float>)

/** One sweep clock per screen; create it inside the loading branch only. */
@Composable
fun rememberShimmer(): Shimmer {
    val progress = rememberInfiniteTransition(label = "shimmer").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(AppMotion.ShimmerSweepMillis, easing = LinearEasing), RepeatMode.Restart),
        label = "shimmerSweep",
    )
    val base = MaterialTheme.colorScheme.surfaceContainerHighest
    val highlight = MaterialTheme.colorScheme.surfaceBright
    return remember(base, highlight, progress) { Shimmer(base, highlight, progress) }
}

/** Draws the sweep in the draw phase only, so it never recomposes. */
fun Modifier.shimmer(shimmer: Shimmer, shape: Shape = RectangleShape): Modifier = drawWithCache {
    val outline = shape.createOutline(size, layoutDirection, this)
    val band = size.width * ShimmerBandFraction
    onDrawBehind {
        val x = -band + shimmer.progress.value * (size.width + 2 * band)
        drawOutline(
            outline = outline,
            brush = Brush.linearGradient(
                colors = listOf(shimmer.base, shimmer.highlight, shimmer.base),
                start = Offset(x - band, 0f),
                end = Offset(x + band, 0f),
            ),
        )
    }
}

private const val ShimmerBandFraction = 0.4f

/** Text-bar widths for skeleton lines, so the bars read as lines of different lengths. */
val SkeletonBarFractions = listOf(0.62f, 0.44f, 0.78f, 0.53f, 0.70f, 0.38f)
