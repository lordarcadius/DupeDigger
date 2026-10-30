package com.vipuljha.dupedigger.presentation.group

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Audiotrack
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.FolderOff
import androidx.compose.material.icons.outlined.InsertDriveFile
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.vipuljha.dupedigger.R
import com.vipuljha.dupedigger.core.designsystem.AppButton
import com.vipuljha.dupedigger.core.designsystem.AppLoadingIndicator
import com.vipuljha.dupedigger.core.designsystem.AppTopBar
import com.vipuljha.dupedigger.core.designsystem.ButtonKind
import com.vipuljha.dupedigger.core.designsystem.Dimens
import com.vipuljha.dupedigger.core.designsystem.LocalPaneMetrics
import com.vipuljha.dupedigger.core.designsystem.StateMessage
import com.vipuljha.dupedigger.core.designsystem.TopBarTitle
import com.vipuljha.dupedigger.core.ui.ContentState
import com.vipuljha.dupedigger.core.ui.StateSwitch
import com.vipuljha.dupedigger.core.ui.UiText
import com.vipuljha.dupedigger.core.ui.asString
import com.vipuljha.dupedigger.domain.model.MediaKind
import kotlinx.collections.immutable.ImmutableMap

@Composable
fun GroupScreen(
    state: GroupState,
    onIntent: (GroupIntent) -> Unit,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hasGroup = (state.content as? ContentState.Success)?.data != null
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            AppTopBar(
                onBack = onBack,
                actions = {
                    if (hasGroup) {
                        AppButton(stringResource(R.string.group_action_not_duplicates), { onIntent(GroupIntent.NotDuplicatesClicked) }, kind = ButtonKind.Text)
                    }
                },
            ) { TopBarTitle(stringResource(R.string.group_title)) }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        StateSwitch(
            state = state.content,
            isEmpty = { it == null },
            modifier = Modifier.fillMaxSize().padding(padding),
            loading = { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { AppLoadingIndicator() } },
            empty = {
                StateMessage(
                    icon = Icons.Outlined.FolderOff,
                    title = stringResource(R.string.group_gone_title),
                    subtitle = stringResource(R.string.group_gone_body),
                    action = { AppButton(stringResource(R.string.action_back), onBack) },
                )
            },
            error = { error ->
                StateMessage(Icons.Outlined.ErrorOutline, stringResource(R.string.group_error_title), error.message.asString(), isError = true)
            },
        ) { detail -> if (detail != null) Members(detail, state.resolutions, onIntent) }
    }
}

/** The detail pane's content while no group is open beside the list. */
@Composable
fun GroupPlaceholder(modifier: Modifier = Modifier) {
    StateMessage(
        icon = Icons.Outlined.ContentCopy,
        title = stringResource(R.string.group_none_open_title),
        subtitle = stringResource(R.string.group_none_open_body),
        modifier = modifier,
    )
}

@Composable
private fun Members(detail: GroupDetailUi, resolutions: ImmutableMap<Long, UiText>, onIntent: (GroupIntent) -> Unit) {
    val gutter = LocalPaneMetrics.current.gutter
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier.widthIn(max = Dimens.maxWidthDetailHeader).fillMaxWidth(),
            contentPadding = PaddingValues(start = gutter, end = gutter, bottom = Dimens.listEndSpace),
            verticalArrangement = Arrangement.spacedBy(Dimens.spaceSm),
        ) {
            item(key = "header", contentType = "header") {
                Column(Modifier.padding(bottom = Dimens.spaceSm).animateItem()) {
                    Text(detail.title, style = MaterialTheme.typography.titleLargeEmphasized, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(detail.summary.asString(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (detail.similar) {
                        Text(
                            stringResource(R.string.group_similar_note),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.padding(top = Dimens.spaceXs),
                        )
                    }
                }
            }
            items(detail.members, key = { it.fileId }, contentType = { "member" }) { member ->
                MemberCard(member, resolutions[member.fileId], onIntent, Modifier.animateItem())
            }
        }
    }
}

@Composable
private fun MemberCard(member: MemberUi, resolution: UiText?, onIntent: (GroupIntent) -> Unit, modifier: Modifier = Modifier) {
    Surface(modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.padding(Dimens.spaceLg)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Thumbnail(member)
                Spacer(Modifier.width(Dimens.spaceLg))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Dimens.spaceXxs)) {
                    Text(member.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        member.location.asString(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        (resolution?.let { UiText.res(R.string.group_member_details_with_resolution, member.details, it) } ?: member.details).asString(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (member.isKeeper) {
                        Text(stringResource(R.string.group_keeping), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            FlowRow(
                modifier = Modifier.fillMaxWidth().padding(top = Dimens.spaceSm),
                horizontalArrangement = Arrangement.spacedBy(Dimens.spaceSm, Alignment.End),
            ) {
                if (member.comparable) {
                    AppButton(stringResource(R.string.group_action_compare), { onIntent(GroupIntent.CompareClicked(member.fileId)) }, kind = ButtonKind.Text)
                }
                AppButton(stringResource(R.string.group_action_open), { onIntent(GroupIntent.OpenClicked(member.fileId)) }, kind = ButtonKind.Text)
                if (!member.isKeeper) {
                    AppButton(stringResource(R.string.group_action_keep), { onIntent(GroupIntent.KeepClicked(member.fileId)) }, kind = ButtonKind.Tonal)
                }
            }
        }
    }
}

/** Images and video frames load through the app's image loader; other kinds show their type glyph. */
@Composable
private fun Thumbnail(member: MemberUi) {
    val shape = MaterialTheme.shapes.extraSmall
    if (member.thumbnail != null) {
        AsyncImage(
            model = member.thumbnail,
            contentDescription = null,
            placeholder = painterResource(R.drawable.art_thumb_placeholder),
            error = painterResource(R.drawable.art_thumb_broken),
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(ThumbnailSize).clip(shape),
        )
    } else {
        Box(
            Modifier.size(ThumbnailSize).background(MaterialTheme.colorScheme.secondaryContainer, shape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                when (member.kind) {
                    MediaKind.AUDIO -> Icons.Outlined.Audiotrack
                    MediaKind.DOCUMENT -> Icons.Outlined.Description
                    else -> Icons.Outlined.InsertDriveFile
                },
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(Dimens.icon),
            )
        }
    }
}

private val ThumbnailSize = 72.dp
