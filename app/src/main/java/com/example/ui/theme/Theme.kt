package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// ==========================================
// 1. STUDYSPACE INDIGO / MINT PALETTES
// ==========================================
private val SlateDarkColorScheme = darkColorScheme(
    primary = SlatePrimary,
    secondary = SlateSecondary,
    tertiary = SlateTertiary,
    primaryContainer = Color(0xFF302C4E),
    onPrimaryContainer = Color(0xFFE4DFFF),
    secondaryContainer = Color(0xFF203B3A),
    onSecondaryContainer = Color(0xFFC9F1EB),
    background = SlateDarkBg,
    surface = SlateSurface,
    surfaceVariant = SlateSurfaceVariant,
    onPrimary = Color(0xFF211B45),
    onSecondary = Color(0xFF102D2B),
    onTertiary = Color(0xFF12352D),
    onBackground = Color(0xFFF2F1FA),
    onSurface = Color(0xFFF2F1FA),
    onSurfaceVariant = SlateOnSurfaceVariant,
    outline = SlateOutline
)

private val SlateLightColorScheme = lightColorScheme(
    primary = SlateLightPrimary,
    secondary = SlateLightSecondary,
    tertiary = SlateLightTertiary,
    primaryContainer = Color(0xFFEEF2FF),
    onPrimaryContainer = Color(0xFF312E81),
    secondaryContainer = Color(0xFFCCFBF1),
    onSecondaryContainer = Color(0xFF0F766E),
    background = SlateLightBg,
    surface = SlateLightSurface,
    surfaceVariant = SlateLightSurfaceVariant,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFF0F172A),
    onSurface = Color(0xFF0F172A),
    onSurfaceVariant = SlateLightOnSurfaceVariant,
    outline = SlateLightOutline
)

// ==========================================
// 2. EMERALD FOREST PALETTES
// ==========================================
private val ForestDarkColorScheme = darkColorScheme(
    primary = ForestPrimary,
    secondary = ForestSecondary,
    tertiary = ForestTertiary,
    primaryContainer = Color(0xFF214C35),
    onPrimaryContainer = Color(0xFFB9F2CA),
    secondaryContainer = Color(0xFF284735),
    onSecondaryContainer = Color(0xFFD7F1D8),
    background = ForestDarkBg,
    surface = ForestSurface,
    surfaceVariant = ForestSurfaceVariant,
    onPrimary = Color(0xFF090D0B),
    onSecondary = Color(0xFF090D0B),
    onTertiary = Color(0xFF090D0B),
    onBackground = Color(0xFFE2EFE9),
    onSurface = Color(0xFFE2EFE9),
    onSurfaceVariant = ForestOnSurfaceVariant,
    outline = ForestOutline
)

private val ForestLightColorScheme = lightColorScheme(
    primary = ForestLightPrimary,
    secondary = ForestLightSecondary,
    tertiary = ForestLightTertiary,
    primaryContainer = Color(0xFFD8EBDD),
    onPrimaryContainer = Color(0xFF143C20),
    secondaryContainer = Color(0xFFDCEDE0),
    onSecondaryContainer = Color(0xFF173D25),
    background = ForestLightBg,
    surface = ForestLightSurface,
    surfaceVariant = ForestLightSurfaceVariant,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFF0C130D),
    onSurface = Color(0xFF0C130D),
    onSurfaceVariant = ForestLightOnSurfaceVariant,
    outline = ForestLightOutline
)

// ==========================================
// 3. ROYAL GOLD PALETTES
// ==========================================
private val RoyalDarkColorScheme = darkColorScheme(
    primary = RoyalPrimary,
    secondary = RoyalSecondary,
    tertiary = RoyalTertiary,
    primaryContainer = Color(0xFF554719),
    onPrimaryContainer = Color(0xFFF2E2A2),
    secondaryContainer = Color(0xFF21454C),
    onSecondaryContainer = Color(0xFFC4EDF2),
    background = RoyalDarkBg,
    surface = RoyalSurface,
    surfaceVariant = RoyalSurfaceVariant,
    onPrimary = Color(0xFF080912),
    onSecondary = Color(0xFF080912),
    onTertiary = Color(0xFF080912),
    onBackground = Color(0xFFEBF0FE),
    onSurface = Color(0xFFEBF0FE),
    onSurfaceVariant = RoyalOnSurfaceVariant,
    outline = RoyalOutline
)

private val RoyalLightColorScheme = lightColorScheme(
    primary = RoyalLightPrimary,
    secondary = RoyalLightSecondary,
    tertiary = RoyalLightTertiary,
    primaryContainer = Color(0xFFF0E7C9),
    onPrimaryContainer = Color(0xFF45370D),
    secondaryContainer = Color(0xFFD3EBEF),
    onSecondaryContainer = Color(0xFF123E46),
    background = RoyalLightBg,
    surface = RoyalLightSurface,
    surfaceVariant = RoyalLightSurfaceVariant,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFF1A1713),
    onSurface = Color(0xFF1A1713),
    onSurfaceVariant = RoyalLightOnSurfaceVariant,
    outline = RoyalLightOutline
)

@Composable
fun MyApplicationTheme(
    isDark: Boolean = true,
    themeName: String = "space_slate",
    content: @Composable () -> Unit
) {
    val shapes = androidx.compose.material3.Shapes(
        extraSmall = RoundedCornerShape(StudySpaceTokens.radiusSm),
        small = StudySpaceTokens.buttonShape,
        medium = RoundedCornerShape(StudySpaceTokens.radiusLg),
        large = RoundedCornerShape(StudySpaceTokens.radiusXl),
        extraLarge = RoundedCornerShape(StudySpaceTokens.radius2Xl)
    )
    val colorScheme = if (isDark) {
        when (themeName) {
            "emerald_forest" -> ForestDarkColorScheme
            "royal_gold" -> RoyalDarkColorScheme
            else -> SlateDarkColorScheme
        }
    } else {
        when (themeName) {
            "emerald_forest" -> ForestLightColorScheme
            "royal_gold" -> RoyalLightColorScheme
            else -> SlateLightColorScheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = shapes,
        content = content
    )
}

