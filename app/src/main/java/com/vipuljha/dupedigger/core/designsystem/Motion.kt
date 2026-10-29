package com.vipuljha.dupedigger.core.designsystem

import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

object AppMotion {
    const val StateFadeMillis = 300
    const val ImageFadeMillis = 250
    const val ShimmerSweepMillis = 1200
    const val ColorChangeMillis = 250

    // M3's emphasized curves, which material3 doesn't expose publicly.
    val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    val EmphasizedAccelerate = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)
    const val PushEnterMillis = 400
    const val PushEnterFadeMillis = 250
    const val PushExitMillis = 300
    const val PushExitFadeMillis = 200
    const val FadeThroughInMillis = 220
    const val FadeThroughDelayMillis = 90
    const val FadeThroughOutMillis = 90
    const val FadeThroughInitialScale = 0.92f

    val SharedBounds = BoundsTransform { _, _ ->
        spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessLow,
            visibilityThreshold = Rect.VisibilityThreshold,
        )
    }
    val SharedFlightFade: SpringSpec<Float> = spring(stiffness = Spring.StiffnessMediumLow)

    const val ChevronMillis = 200
    const val PopoverInitialScale = 0.92f
    const val RichTooltipDwellMillis = 1000L
    const val SeamMillis = 150
    const val ScrollbarFadeInMillis = 150
    const val ScrollbarFadeOutMillis = 400
    const val ScrollbarHideDelayMillis = 1500L
    const val ScrollbarGrabMillis = 180
    val ScrollGlide = spring<Float>(Spring.DampingRatioNoBouncy, Spring.StiffnessMediumLow, visibilityThreshold = 0.5f)
}

object HoverMotion {
    val Lift: Dp = 1.dp
    const val Scale = 1.0225f
    const val ZoomScale = 1.045f
    const val NoScale = 1f
    const val PressDip = -0.35f
    val Rise: SpringSpec<Float> = spring(dampingRatio = 0.42f, stiffness = Spring.StiffnessMediumLow)
    val Settle: SpringSpec<Float> = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow)
}

object TintStrength {
    const val SurfaceBlend = 0.13f
    const val HeroWash = 0.24f
    const val MediaBackdropMax = 0.40f
}
