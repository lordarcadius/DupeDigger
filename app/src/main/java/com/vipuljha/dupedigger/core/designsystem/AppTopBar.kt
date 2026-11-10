package com.vipuljha.dupedigger.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.vipuljha.dupedigger.R

/** Top bar inside the pane; the leading slot holds Back on pushed screens and is empty on a top-level one. */
@Composable
fun AppTopBar(
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    middle: @Composable RowScope.() -> Unit,
) {
    val gutter = LocalPaneMetrics.current.gutter
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .background(MaterialTheme.colorScheme.surface)
            .fillMaxWidth()
            .heightIn(min = Dimens.topBarMin)
            .padding(start = gutter, end = gutter, top = Dimens.spaceXl, bottom = Dimens.spaceSm),
    ) {
        if (onBack != null) {
            BackButton(onBack)
            Spacer(Modifier.width(Dimens.spaceMd))
        }
        middle()
        actions()
    }
}

@Composable
fun RowScope.TopBarTitle(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleLarge,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.weight(1f).semantics { heading() },
    )
}
