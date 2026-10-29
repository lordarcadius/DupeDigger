package com.vipuljha.dupedigger.core.designsystem

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.vipuljha.dupedigger.R

private fun inter(weight: Int, italic: Boolean = false) = Font(
    resId = if (italic) R.font.inter_variable_italic else R.font.inter_variable,
    weight = FontWeight(weight),
    style = if (italic) FontStyle.Italic else FontStyle.Normal,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

val InterFamily = FontFamily(
    inter(300), inter(400), inter(500), inter(600), inter(700),
    inter(300, italic = true), inter(400, italic = true), inter(500, italic = true),
    inter(600, italic = true), inter(700, italic = true),
)

val AppTypography = Typography(fontFamily = InterFamily)

val SectionLabelStyle = AppTypography.labelMedium.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.8.sp)
