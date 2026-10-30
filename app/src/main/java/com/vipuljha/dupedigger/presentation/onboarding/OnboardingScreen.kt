package com.vipuljha.dupedigger.presentation.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.PermMedia
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import com.vipuljha.dupedigger.R
import com.vipuljha.dupedigger.core.designsystem.AppButton
import com.vipuljha.dupedigger.core.designsystem.ButtonKind
import com.vipuljha.dupedigger.core.designsystem.Dimens
import com.vipuljha.dupedigger.core.designsystem.DupeDiggerTheme
import com.vipuljha.dupedigger.core.ui.UiText
import com.vipuljha.dupedigger.core.ui.asString

@Composable
fun OnboardingScreen(
    state: OnboardingState,
    onIntent: (OnboardingIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(rememberScrollState()),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = Dimens.maxWidthReading)
                    .fillMaxWidth()
                    .padding(horizontal = Dimens.gutterCompact, vertical = Dimens.spaceXxl),
                verticalArrangement = Arrangement.spacedBy(Dimens.spaceLg),
            ) {
                Text(
                    stringResource(R.string.onboarding_title),
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    stringResource(R.string.onboarding_body),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                AccessCard(
                    icon = Icons.Outlined.PermMedia,
                    title = stringResource(R.string.onboarding_media_title),
                    body = stringResource(R.string.onboarding_media_body),
                    status = state.mediaStatus,
                    actionLabel = when (state.mediaAction) {
                        MediaAction.Allow -> stringResource(R.string.onboarding_action_allow)
                        MediaAction.AllowAll -> stringResource(R.string.onboarding_action_allow_all)
                        MediaAction.OpenSettings -> stringResource(R.string.onboarding_action_open_settings)
                        MediaAction.None -> null
                    },
                    onAction = { onIntent(OnboardingIntent.MediaActionClicked) },
                )
                AccessCard(
                    icon = Icons.Outlined.FolderOpen,
                    title = stringResource(R.string.onboarding_all_files_title),
                    body = stringResource(R.string.onboarding_all_files_body),
                    status = state.allFilesStatus,
                    actionLabel = if (state.allFilesActionVisible) stringResource(R.string.onboarding_action_turn_on) else null,
                    onAction = { onIntent(OnboardingIntent.AllFilesClicked) },
                )
                Text(
                    stringResource(R.string.onboarding_privacy),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                AppButton(
                    label = stringResource(R.string.onboarding_action_continue),
                    onClick = { onIntent(OnboardingIntent.ContinueClicked) },
                    loading = state.isFinishing,
                    modifier = Modifier.align(Alignment.End),
                )
            }
        }
    }
}

@Composable
private fun AccessCard(
    icon: ImageVector,
    title: String,
    body: String,
    status: UiText?,
    actionLabel: String?,
    onAction: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(Dimens.spaceLg),
            verticalArrangement = Arrangement.spacedBy(Dimens.spaceSm),
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(Dimens.icon))
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            status?.let {
                Text(
                    it.asString(),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
            actionLabel?.let { AppButton(label = it, onClick = onAction, kind = ButtonKind.Tonal) }
        }
    }
}

@Preview
@Composable
private fun OnboardingScreenPreview() {
    DupeDiggerTheme {
        OnboardingScreen(
            state = OnboardingState(
                mediaStatus = UiText.res(R.string.onboarding_status_partial),
                mediaAction = MediaAction.AllowAll,
                allFilesStatus = UiText.res(R.string.onboarding_status_off),
                allFilesActionVisible = true,
            ),
            onIntent = {},
        )
    }
}
