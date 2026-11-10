package com.vipuljha.dupedigger.core.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.vipuljha.dupedigger.core.designsystem.stateChangeSpec

/** Cross-fades a content area only when the kind of state changes: loading, error, empty or content. */
@Composable
fun <T> StateSwitch(
    state: ContentState<T>,
    isEmpty: (T) -> Boolean,
    loading: @Composable () -> Unit,
    empty: @Composable () -> Unit,
    error: @Composable (ContentState.Error) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (T) -> Unit,
) {
    // The theme's own effects spec, so a state change reads like the rest of the app rather than a hand-set curve.
    val fade = stateChangeSpec()
    AnimatedContent(
        targetState = state,
        modifier = modifier,
        contentKey = { s ->
            when (s) {
                ContentState.Loading -> 0
                is ContentState.Error -> 1
                is ContentState.Success -> if (isEmpty(s.data)) 2 else 3
            }
        },
        transitionSpec = { fadeIn(fade) togetherWith fadeOut(fade) using SizeTransform(clip = false) },
        label = "contentState",
    ) { s ->
        when (s) {
            ContentState.Loading -> loading()
            is ContentState.Error -> error(s)
            is ContentState.Success -> if (isEmpty(s.data)) empty() else content(s.data)
        }
    }
}
