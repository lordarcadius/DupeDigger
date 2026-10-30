package com.vipuljha.dupedigger.core.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, description: String? = null) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = Dimens.spaceLg, bottom = Dimens.spaceXs)
            .heightIn(min = Dimens.controlDense),
        verticalArrangement = Arrangement.spacedBy(Dimens.spaceXxs),
    ) {
        Text(title, style = MaterialTheme.typography.titleLargeEmphasized, modifier = Modifier.semantics { heading() })
        description?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
