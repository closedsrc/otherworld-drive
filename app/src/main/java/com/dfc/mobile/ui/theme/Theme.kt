package com.dfc.mobile.ui.theme

import android.content.res.Configuration
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
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
    // #B4AEA8 clears 4.6:1 on #111111 — the old #9C9691 was 3.4:1 and
    // failed AA for the metadata it was used for.
    onSurfaceVariant = Color(0xFFB4AEA8),
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
    // 4.6:1 on white.
    onSurfaceVariant = Color(0xFF6E6E6E),
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

    /**
     * Breathing room at the bottom of a scrolling screen.
     *
     * This used to be ~97dp because the navigation bar *floated over* the
     * content and every list had to reserve the bar's own footprint plus the
     * system inset. The bar is now a sibling in the layout — the content box
     * ends where the bar begins — so the only thing left to reserve is a plain
     * gutter. Keeping the old number would leave a dead band the height of a
     * thumbnail under the last row of every grid.
     */
    val navClearance: Dp
        @Composable get() = Spacing.md
}

/**
 * Four radii, each bound to the kind of element it shapes rather than used as
 * decoration: controls (icon buttons, chips, badges), tiles (a square photo in
 * the library grid, where a control's radius eats too much of the image), cards,
 * and sheets.
 */
object Radii {
    val control = 10.dp
    val tile = 6.dp
    val card = 16.dp
    val sheet = 24.dp
}

/**
 * Provides colour, the active type scale, and the window class.
 *
 * [DfcTypography] below stays a Material3 Typography so any stock Material
 * component still renders correctly; Compose screens read [LocalType] instead.
 */
@Composable
fun DfcTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val config = LocalConfiguration.current
    val windowSize = WindowSize(
        width = classifyWidth(config.screenWidthDp),
        height = classifyHeight(config.screenHeightDp),
    )
    val type = if (windowSize.isCompact) CompactType else ExpandedType

    CompositionLocalProvider(
        LocalWindowSize provides windowSize,
        LocalType provides type,
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = DfcTypography,
            content = content,
        )
    }
}
