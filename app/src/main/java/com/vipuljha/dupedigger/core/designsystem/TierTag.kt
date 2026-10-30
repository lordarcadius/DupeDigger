package com.vipuljha.dupedigger.core.designsystem

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Marks a group whose files aren't byte-identical, so it never passes for an exact one. */
@Composable
fun TierTag(text: String, modifier: Modifier = Modifier) {
    Surface(modifier, shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.tertiaryContainer) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = Dimens.spaceSm, vertical = Dimens.spaceXxs),
        )
    }
}
