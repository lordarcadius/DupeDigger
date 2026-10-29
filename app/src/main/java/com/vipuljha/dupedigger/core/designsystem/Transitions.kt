package com.vipuljha.dupedigger.core.designsystem

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith

fun AnimatedContentTransitionScope<*>.pushTransition(): ContentTransform =
    (slideIntoContainer(SlideDirection.Start, tween(AppMotion.PushEnterMillis, easing = AppMotion.EmphasizedDecelerate)) +
        fadeIn(tween(AppMotion.PushEnterFadeMillis))) togetherWith
        (slideOutOfContainer(SlideDirection.Start, tween(AppMotion.PushExitMillis, easing = AppMotion.EmphasizedAccelerate)) +
            fadeOut(tween(AppMotion.PushExitFadeMillis)))

fun AnimatedContentTransitionScope<*>.popTransition(): ContentTransform =
    (slideIntoContainer(SlideDirection.End, tween(AppMotion.PushEnterMillis, easing = AppMotion.EmphasizedDecelerate)) +
        fadeIn(tween(AppMotion.PushEnterFadeMillis))) togetherWith
        (slideOutOfContainer(SlideDirection.End, tween(AppMotion.PushExitMillis, easing = AppMotion.EmphasizedAccelerate)) +
            fadeOut(tween(AppMotion.PushExitFadeMillis)))

fun fadeThrough(): ContentTransform {
    val inSpec = tween<Float>(AppMotion.FadeThroughInMillis, delayMillis = AppMotion.FadeThroughDelayMillis)
    return (fadeIn(inSpec) + scaleIn(inSpec, initialScale = AppMotion.FadeThroughInitialScale)) togetherWith
        fadeOut(tween(AppMotion.FadeThroughOutMillis))
}
