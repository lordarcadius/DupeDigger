package com.vipuljha.dupedigger.core.designsystem

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

enum class ButtonKind { Filled, Tonal, Outlined, Text }

@Composable
fun AppButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: ButtonKind = ButtonKind.Filled,
    icon: ImageVector? = null,
    loading: Boolean = false,
    enabled: Boolean = true,
    contentPadding: PaddingValues = buttonPadding(kind, leading = loading || icon != null),
) {
    val content: @Composable RowScope.() -> Unit = {
        when {
            loading -> CircularProgressIndicator(Modifier.size(ButtonDefaults.IconSize), LocalContentColor.current, strokeWidth = SpinnerStroke)
            icon != null -> Icon(icon, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
        }
        if (loading || icon != null) Spacer(Modifier.width(ButtonDefaults.IconSpacing))
        Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    val active = enabled && !loading
    when (kind) {
        ButtonKind.Filled -> Button(
            onClick, modifier, active,
            elevation = ButtonDefaults.buttonElevation(hoveredElevation = 0.dp),
            contentPadding = contentPadding, content = content,
        )
        ButtonKind.Tonal -> FilledTonalButton(
            onClick, modifier, active,
            elevation = ButtonDefaults.filledTonalButtonElevation(hoveredElevation = 0.dp),
            contentPadding = contentPadding, content = content,
        )
        ButtonKind.Outlined -> OutlinedButton(onClick, modifier, active, contentPadding = contentPadding, content = content)
        ButtonKind.Text -> TextButton(onClick, modifier, active, contentPadding = contentPadding, content = content)
    }
}

private fun buttonPadding(kind: ButtonKind, leading: Boolean) = when {
    kind == ButtonKind.Text -> if (leading) ButtonDefaults.TextButtonWithIconContentPadding else ButtonDefaults.TextButtonContentPadding
    leading -> ButtonDefaults.ButtonWithIconContentPadding
    else -> ButtonDefaults.ContentPadding
}

private val SpinnerStroke = 2.dp
