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

    /**
     * A navigation rail replaces the bottom bar only on EXPANDED windows.
     *
     * This used to be `!isCompact`, so a 600dp portrait phone drew an 80dp
     * vertical slab of navigation down the left of every screen — the single
     * ugliest thing in the app, and a control no phone design uses. The bottom
     * bar is correct up to tablet width; the rail is for tablets and unfolded
     * foldables.
     */
    val useRail: Boolean get() = isExpanded

    /** Two-pane layouts (list + detail) only make sense on EXPANDED. */
    val useTwoPane: Boolean get() = isExpanded

    /**
     * Content column: full width on phone, capped and centred on larger.
     *
     * The cap is well below the window classes' own breakpoints on purpose. A
     * 1000dp row that reads "Albums ……………………… See all" is the same defect as
     * the old stretched phone UI, only quieter: the heading and the thing it
     * labels stop looking related. 840dp is about the widest a row of content
     * can be before the eye loses the pairing.
     */
    val maxContentWidth: Dp
        get() = when (width) {
            WindowClass.COMPACT -> Dp.Unspecified
            WindowClass.MEDIUM -> 720.dp
            WindowClass.EXPANDED -> 840.dp
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
