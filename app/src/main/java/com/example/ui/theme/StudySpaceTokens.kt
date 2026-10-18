package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object StudySpaceTokens {
    // VitalTrack reference palette (src/index.css).
    val background = Color(0xFFF8FAFC)
    val foreground = Color(0xFF0F172A)
    val surface = Color(0xFFFFFFFF)
    val surfaceHover = Color(0xFFF1F5F9)
    val muted = Color(0xFFF1F5F9)
    val mutedForeground = Color(0xFF64748B)
    val border = Color(0xFFE2E8F0)
    val borderHover = Color(0xFFCBD5E1)

    val primary50 = Color(0xFFEEF2FF)
    val primary100 = Color(0xFFE0E7FF)
    val primary200 = Color(0xFFC7D2FE)
    val primary300 = Color(0xFFA5B4FC)
    val primary400 = Color(0xFF818CF8)
    val primary500 = Color(0xFF6366F1)
    val primary = Color(0xFF4F46E5)
    val primary700 = Color(0xFF4338CA)
    val primary800 = Color(0xFF3730A3)
    val primary900 = Color(0xFF312E81)

    val accent50 = Color(0xFFF0FDFA)
    val accent100 = Color(0xFFCCFBF1)
    val accent200 = Color(0xFF99F6E4)
    val accent = Color(0xFF14B8A6)
    val accent600 = Color(0xFF0D9488)
    val accent700 = Color(0xFF0F766E)

    val success = Color(0xFF10B981)
    val warning = Color(0xFFF59E0B)
    val error = Color(0xFFEF4444)
    val info = Color(0xFF0EA5E9)

    // Web uses Google Fonts Inter for body text and Outfit for display headings.
    const val fontSansName = "Inter"
    const val fontDisplayName = "Outfit"

    val textXs = 12.sp
    val textSm = 14.sp
    val textBase = 16.sp
    val textLg = 18.sp
    val textXl = 20.sp
    val text2Xl = 24.sp
    val text3Xl = 30.sp
    val text4Xl = 36.sp
    val text5Xl = 48.sp
    val text6Xl = 60.sp
    val text7Xl = 72.sp

    val radiusSm = 8.dp
    val radiusMd = 12.dp
    val radiusLg = 16.dp
    val radiusXl = 24.dp
    val radius2Xl = 32.dp

    // Kept verbatim from CSS; Compose elevation equivalents can be tuned per component.
    const val shadowXsCss = "0 1px 2px rgba(15, 23, 42, 0.05)"
    const val shadowSmCss = "0 4px 12px -2px rgba(15, 23, 42, 0.04), 0 2px 4px -2px rgba(15, 23, 42, 0.02)"
    const val shadowMdCss = "0 12px 28px -6px rgba(15, 23, 42, 0.08), 0 8px 12px -4px rgba(15, 23, 42, 0.04)"
    const val shadowLgCss = "0 24px 48px -12px rgba(15, 23, 42, 0.12), 0 12px 24px -8px rgba(15, 23, 42, 0.06)"
    const val shadowFloatCss = "0 20px 40px -8px rgba(99, 102, 241, 0.15), 0 10px 20px -4px rgba(99, 102, 241, 0.1)"
    const val shadowGlowCss = "0 0 20px 0 rgba(99, 102, 241, 0.3)"

    val spaceSmall = 8.dp
    val spaceMedium = 16.dp
    val spaceLarge = 24.dp
    val buttonHeight = 48.dp
    val cardShape = RoundedCornerShape(radiusLg)
    val buttonShape = RoundedCornerShape(radiusMd)
    val controlShape = RoundedCornerShape(radiusSm)
}
