package com.vipuljha.dupedigger.core.designsystem

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import kotlinx.collections.immutable.persistentListOf

// Developer-only preview gallery; its literal labels never reach users.
@Composable
private fun DesignSystemGallery() {
    val scheme = MaterialTheme.colorScheme
    val type = MaterialTheme.typography
    val shapes = MaterialTheme.shapes
    Surface(color = scheme.surface) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(Dimens.gutterCompact),
            verticalArrangement = Arrangement.spacedBy(Dimens.spaceXl),
        ) {
            GallerySection("Colour roles") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Dimens.spaceSm), verticalArrangement = Arrangement.spacedBy(Dimens.spaceSm)) {
                    listOf(
                        "primary" to scheme.primary, "onPrimary" to scheme.onPrimary,
                        "primaryContainer" to scheme.primaryContainer, "secondary" to scheme.secondary,
                        "secondaryContainer" to scheme.secondaryContainer, "tertiary" to scheme.tertiary,
                        "tertiaryContainer" to scheme.tertiaryContainer, "error" to scheme.error,
                        "errorContainer" to scheme.errorContainer, "surface" to scheme.surface,
                        "surfaceContainerLow" to scheme.surfaceContainerLow, "surfaceContainer" to scheme.surfaceContainer,
                        "surfaceContainerHigh" to scheme.surfaceContainerHigh, "surfaceContainerHighest" to scheme.surfaceContainerHighest,
                        "onSurface" to scheme.onSurface, "onSurfaceVariant" to scheme.onSurfaceVariant,
                        "outline" to scheme.outline, "outlineVariant" to scheme.outlineVariant,
                    ).forEach { (name, color) -> Swatch(name, color) }
                }
            }
            GallerySection("Type scale") {
                listOf(
                    "displaySmall" to type.displaySmall, "headlineMedium" to type.headlineMedium,
                    "titleLarge" to type.titleLarge, "titleLargeEmphasized" to type.titleLargeEmphasized,
                    "titleMedium" to type.titleMedium, "bodyLarge" to type.bodyLarge,
                    "bodyMedium" to type.bodyMedium, "bodyMediumEmphasized" to type.bodyMediumEmphasized,
                    "labelLarge" to type.labelLarge, "labelSmall" to type.labelSmall,
                    "SECTION LABEL" to SectionLabelStyle,
                ).forEach { (name, style) -> TypeSample(name, style) }
            }
            GallerySection("Shapes") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Dimens.spaceSm), verticalArrangement = Arrangement.spacedBy(Dimens.spaceSm)) {
                    listOf(
                        "4" to shapes.extraSmall, "8" to shapes.small, "12" to shapes.medium, "16" to shapes.large,
                        "20" to shapes.largeIncreased, "28" to shapes.extraLarge,
                        "32" to shapes.extraLargeIncreased, "48" to shapes.extraExtraLarge,
                    ).forEach { (name, shape) -> ShapeSample(name, shape) }
                }
            }
            GallerySection("Spacing") {
                listOf(
                    Dimens.spaceXxs, Dimens.spaceXs, Dimens.spaceSm, Dimens.spaceMd,
                    Dimens.spaceLg, Dimens.spaceXl, Dimens.spaceXxl,
                ).forEach { SpaceSample(it) }
            }
            GallerySection("Components") {
                Row(horizontalArrangement = Arrangement.spacedBy(Dimens.spaceLg), verticalAlignment = Alignment.CenterVertically) {
                    AppLoadingIndicator()
                    AppLoadingIndicator(contained = true)
                }
                AppProgressBar(progress = { 0.6f })
                AppProgressBar(progress = null)
                var picked by remember { mutableStateOf("Exact") }
                AppConnectedButtonGroup(
                    options = persistentListOf("Exact", "Near", "Semantic"),
                    isSelected = { it == picked },
                    onClick = { picked = it },
                    label = { it },
                )
                AppExtendedFab(text = "Scan", icon = Icons.Filled.Search, onClick = {})
            }
        }
    }
}

@Composable
private fun GallerySection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.spaceSm)) {
        Text(title, style = MaterialTheme.typography.titleLargeEmphasized)
        content()
    }
}

@Composable
private fun Swatch(name: String, color: Color) {
    Column(modifier = Modifier.width(Dimens.illustrationDisc)) {
        Box(
            Modifier
                .fillMaxWidth()
                .size(Dimens.touchTarget)
                .clip(MaterialTheme.shapes.small)
                .background(color)
                .border(Dimens.hairline, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.small),
        )
        Text(name, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun TypeSample(name: String, style: TextStyle) {
    Text(name, style = style, color = MaterialTheme.colorScheme.onSurface)
}

@Composable
private fun ShapeSample(name: String, shape: Shape) {
    Box(
        modifier = Modifier
            .size(Dimens.illustrationDisc / 2)
            .clip(shape)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Text(name, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
    }
}

@Composable
private fun SpaceSample(space: Dp) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.spaceSm)) {
        Box(Modifier.size(width = space, height = Dimens.spaceLg).background(MaterialTheme.colorScheme.tertiary))
        Text(space.toString(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Preview(name = "Light", heightDp = 2000)
@Composable
private fun DesignSystemGalleryLightPreview() {
    DupeDiggerTheme(darkTheme = false, dynamicColor = false) { DesignSystemGallery() }
}

@Preview(name = "Dark", heightDp = 2000, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun DesignSystemGalleryDarkPreview() {
    DupeDiggerTheme(darkTheme = true, dynamicColor = false) { DesignSystemGallery() }
}
