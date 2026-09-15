package com.dfc.mobile.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Neutral surfaces carry the hierarchy; a single terracotta accent marks the
 * one action or figure that matters on each screen. Dark is the primary theme
 * because this is a storage utility that gets opened at night, and the values
 * stay near-black so photo thumbnails read as the brightest thing on screen.
 */
private val Terracotta = Color(0xFFA6422A)
// Lifted for dark surfaces: the base terracotta only reaches 2.3:1 on #0B0B0B,
// which fails for icons and text.
private val TerracottaOnDark = Color(0xFFD2704F)

private val DarkColors = darkColorScheme(
    primary = TerracottaOnDark,
    onPrimary = Color(0xFF1A0C07),
    primaryContainer = Color(0xFF3A1A10),
    onPrimaryContainer = Color(0xFFF3D9CE),
    secondary = Color(0xFFB9B3AC),
    onSecondary = Color(0xFF141210),
    background = Color(0xFF0B0B0B),
    onBackground = Color(0xFFF2F0ED),
    surface = Color(0xFF111111),
    onSurface = Color(0xFFF2F0ED),
    surfaceVariant = Color(0xFF161616),
    onSurfaceVariant = Color(0xFF9C9691),
    surfaceContainer = Color(0xFF111111),
    surfaceContainerHigh = Color(0xFF161616),
    surfaceContainerLow = Color(0xFF0E0E0E),
    outline = Color(0xFF2A2A2A),
    outlineVariant = Color(0xFF1E1E1E),
    error = Color(0xFFE58379),
    onError = Color(0xFF2A0D09),
)

private val LightColors = lightColorScheme(
    primary = Terracotta,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFF6E3DC),
    onPrimaryContainer = Color(0xFF3E1A0E),
    secondary = Color(0xFF4A4641),
    onSecondary = Color.White,
    background = Color(0xFFFFFFFF),
    onBackground = Color(0xFF0B0B0B),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF0B0B0B),
    surfaceVariant = Color(0xFFF6F6F4),
    // 4.9:1 on white: metadata is small, so it has to clear 4.5:1.
    onSurfaceVariant = Color(0xFF767676),
    surfaceContainer = Color(0xFFFCFCFB),
    surfaceContainerHigh = Color(0xFFF6F6F4),
    surfaceContainerLow = Color(0xFFFAFAF9),
    outline = Color(0xFFE4E4E1),
    outlineVariant = Color(0xFFEFEFEC),
    error = Color(0xFFB23A2E),
    onError = Color.White,
)

/**
 * One sans family throughout; hierarchy comes from size and weight rather than
 * a second typeface. Sizes are floored at 12sp so metadata stays legible.
 */
private val DfcTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 34.sp,
        lineHeight = 38.sp,
        letterSpacing = (-0.5).sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = (-0.2).sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 19.sp,
        lineHeight = 25.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 21.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 21.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 19.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 18.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 17.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        textAlign = TextAlign.Start,
    ),
)

/** 4 / 8 / 16 / 24 / 32 scale. Every gap in the app comes from here. */
object Spacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 16.dp
    val lg = 24.dp
    val xl = 32.dp
    /** Bottom bar height plus its float gap, so lists can pad clear of it. */
    val navClearance = 108.dp
}

/** Three radii only: controls, cards, and sheets. */
object Radii {
    val control = 10.dp
    val card = 16.dp
    val sheet = 24.dp
}

@Composable
fun DfcTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = DfcTypography,
        content = content,
    )
}
