package com.vipuljha.dupedigger.core.designsystem

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonColors
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vipuljha.dupedigger.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlainTooltipBox(label: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) = TooltipBox(
    positionProvider = rememberBoundedTooltipPositionProvider(),
    tooltip = { PlainTooltip(shadowElevation = TooltipShadow) { Text(label) } },
    state = rememberTooltipState(),
    modifier = modifier,
    content = content,
)

@Composable
fun TooltipIconButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    iconSize: Dp = Dimens.icon,
    colors: IconButtonColors = IconButtonDefaults.iconButtonColors(
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = StateAlpha.DisabledContent),
    ),
) = PlainTooltipBox(label, modifier) {
    IconButton(onClick = onClick, enabled = enabled, colors = colors) {
        Icon(icon, contentDescription = label, modifier = Modifier.size(iconSize))
    }
}

@Composable
fun BackButton(onBack: () -> Unit) =
    TooltipIconButton(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back), onBack)

private val TooltipShadow = 3.dp
