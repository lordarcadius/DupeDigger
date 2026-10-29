package com.vipuljha.dupedigger.core.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.WavyProgressIndicatorDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import kotlinx.collections.immutable.ImmutableList

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AppLoadingIndicator(modifier: Modifier = Modifier, contained: Boolean = false) {
    if (contained) ContainedLoadingIndicator(modifier = modifier) else LoadingIndicator(modifier = modifier)
}

/** A null [progress] draws the indeterminate wave; a lambda keeps progress ticks from recomposing callers. */
@Composable
fun AppProgressBar(
    progress: (() -> Float)?,
    modifier: Modifier = Modifier,
    color: Color = WavyProgressIndicatorDefaults.indicatorColor,
    trackColor: Color = WavyProgressIndicatorDefaults.trackColor,
) {
    if (progress == null) {
        LinearWavyProgressIndicator(modifier = modifier.fillMaxWidth(), color = color, trackColor = trackColor)
    } else {
        LinearWavyProgressIndicator(
            progress = progress,
            modifier = modifier.fillMaxWidth(),
            color = color,
            trackColor = trackColor,
        )
    }
}

@Composable
fun AppExtendedFab(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    expanded: Boolean = true,
) {
    ExtendedFloatingActionButton(
        text = { Text(text) },
        icon = { Icon(icon, contentDescription = null) },
        onClick = onClick,
        expanded = expanded,
        modifier = modifier,
    )
}

/** Connected toggle buttons for picking one or several [options], such as a scan scope or detection tiers. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun <T> AppConnectedButtonGroup(
    options: ImmutableList<T>,
    isSelected: (T) -> Boolean,
    onClick: (T) -> Unit,
    label: @Composable (T) -> String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        options.forEachIndexed { index, option ->
            ToggleButton(
                checked = isSelected(option),
                onCheckedChange = { onClick(option) },
                modifier = Modifier.weight(1f),
                shapes = when (index) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
            ) {
                Text(label(option), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
