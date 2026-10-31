package com.eva1.companion.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val EvaBlack       = Color(0xFF070B14)
val EvaDarkNavy    = Color(0xFF0D1425)
val EvaSurface     = Color(0xFF111B2E)
val EvaSurfaceHigh = Color(0xFF1A2540)
val EvaBorder      = Color(0xFF1E3054)

val EvaBlue        = Color(0xFF4F8EF7)
val EvaBlueBright  = Color(0xFF70A9FF)
val EvaPurple      = Color(0xFF9B5DE5)
val EvaViolet      = Color(0xFF7B2FBE)
val EvaCyan        = Color(0xFF00D4FF)
val EvaGreen       = Color(0xFF00E5A0)
val EvaAmber       = Color(0xFFFFB703)
val EvaRed         = Color(0xFFFF4D6D)

val EvaTextPrimary   = Color(0xFFE8F0FE)
val EvaTextSecondary = Color(0xFF8DA3C7)
val EvaTextMuted     = Color(0xFF4A6080)

private val EvaDarkColorScheme = darkColorScheme(
    primary          = EvaBlue,
    onPrimary        = Color.White,
    primaryContainer = Color(0xFF1A3A6E),
    secondary        = EvaPurple,
    onSecondary      = Color.White,
    tertiary         = EvaCyan,
    background       = EvaBlack,
    onBackground     = EvaTextPrimary,
    surface          = EvaSurface,
    onSurface        = EvaTextPrimary,
    surfaceVariant   = EvaSurfaceHigh,
    onSurfaceVariant = EvaTextSecondary,
    outline          = EvaBorder,
    error            = EvaRed
)

val EvaTypography = Typography(
    headlineLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize   = 28.sp,
        color      = EvaTextPrimary
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize   = 22.sp,
        color      = EvaTextPrimary
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize   = 18.sp,
        color      = EvaTextPrimary
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize   = 15.sp,
        color      = EvaTextPrimary
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize   = 15.sp,
        lineHeight = 22.sp,
        color      = EvaTextPrimary
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize   = 13.sp,
        lineHeight = 19.sp,
        color      = EvaTextSecondary
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Medium,
        fontSize   = 11.sp,
        color      = EvaTextMuted
    )
)

@Composable
fun EVA1Theme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = EvaDarkColorScheme,
        typography  = EvaTypography,
        content     = content
    )
}
