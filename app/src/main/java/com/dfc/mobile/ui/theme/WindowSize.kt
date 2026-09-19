package com.dfc.mobile.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Window size classes, Material's three buckets.
 *
 * The app used to be a phone layout stretched: on a 1600px-wide display the
 * search field spanned the full width and the five-tab bar grew ~320px targets.
 * Everything that adapts reads these instead of measuring ad hoc.
 */
enum class WindowClass { COMPACT, MEDIUM, EXPANDED }

data class WindowSize(
    val width: WindowClass,
    val height: WindowClass,
) {
    val isCompact: Boolean get() = width == WindowClass.COMPACT
    val isMedium: Boolean get() = width == WindowClass.MEDIUM
    val isExpanded: Boolean get() = width == WindowClass.EXPANDED

    /** Navigation rail replaces the bottom bar from MEDIUM up. */
    val useRail: Boolean get() = !isCompact

    /** Two-pane layouts (list + detail) only make sense on EXPANDED. */
    val useTwoPane: Boolean get() = isExpanded

    /** Content column: full width on phone, capped and centred on larger. */
    val maxContentWidth: Dp
        get() = when (width) {
            WindowClass.COMPACT -> Dp.Unspecified
            WindowClass.MEDIUM -> 720.dp
            WindowClass.EXPANDED -> 1100.dp
        }

    /** Grid columns for photos/files. */
    val gridColumns: Int
        get() = when (width) {
            WindowClass.COMPACT -> 3
            WindowClass.MEDIUM -> 4
            WindowClass.EXPANDED -> 6
        }
}

val LocalWindowSize = staticCompositionLocalOf {
    WindowSize(WindowClass.COMPACT, WindowClass.COMPACT)
}

/** Read the active window class. */
val windowSize: WindowSize
    @Composable get() = LocalWindowSize.current

fun classifyWidth(widthDp: Int): WindowClass = when {
    widthDp < 600 -> WindowClass.COMPACT
    widthDp < 840 -> WindowClass.MEDIUM
    else -> WindowClass.EXPANDED
}

fun classifyHeight(heightDp: Int): WindowClass = when {
    heightDp < 480 -> WindowClass.COMPACT
    heightDp < 900 -> WindowClass.MEDIUM
    else -> WindowClass.EXPANDED
}
