package com.dfc.mobile.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Elevation as a system, not improvised per screen.
 *
 * Dialogs used to be "a darker rectangle": radius 28 on a card whose neighbours
 * were 16, a scrim that blended into a muddy brown, and a nav bar that stayed
 * lit underneath. Each level here carries its own surface, border, scrim, and
 * radius so a sheet reads as above the page rather than merely darker than it.
 */
enum class ElevationLevel(val dp: Dp) {
    /** Flat content: rows, tiles. */
    LEVEL_0(0.dp),

    /** Raised content: cards, chips. */
    LEVEL_1(1.dp),

    /** Sticky chrome: top bars, the navigation rail. */
    LEVEL_2(3.dp),

    /** Floating: the bottom bar, snackbars, menus. */
    LEVEL_3(6.dp),

    /** Modal: dialogs and sheets. */
    LEVEL_4(12.dp),
}

object Elevation {
    /** Dialog/sheet surface, lifted above whatever is behind it. */
    val modal: Dp get() = ElevationLevel.LEVEL_4.dp
    val floating: Dp get() = ElevationLevel.LEVEL_3.dp
    val chrome: Dp get() = ElevationLevel.LEVEL_2.dp
    val card: Dp get() = ElevationLevel.LEVEL_1.dp

    /** Radii by kind of surface. */
    val radiusSheet = 24.dp
    val radiusCard = 16.dp
    val radiusControl = 10.dp
    val radiusTile = 6.dp
}

/** Scrim behind a modal. Opaque enough to signal modality, not black. */
val modalScrim: Color
    @Composable get() = MaterialTheme.colorScheme.scrim.copy(alpha = 0.62f)

/** Surface for a dialog — a real step above the page, with its own outline. */
val modalSurface: Color
    @Composable get() = MaterialTheme.colorScheme.surfaceContainerHigh

val modalOutline: Color
    @Composable get() = MaterialTheme.colorScheme.outlineVariant
