package com.dfc.mobile.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * A real type scale: eight named roles, one source of truth, responsive.
 *
 * The old theme had nine Material slots and no scale, so screens picked sizes
 * ad hoc — a "Good evening" greeting outranked the backup state, and metadata
 * drifted between 11sp and 13sp depending on which file happened to render it.
 * Here every screen reads a role, and a foldable or tablet gets a different set
 * of roles through the same provider, so nothing edits forty call sites.
 *
 * Leading is tight on display (1.19) and generous on body (1.5); metadata keeps
 * 16sp of leading even at 12sp of size, which is the legibility floor.
 */
data class DfcType(
    /** The one figure a screen is about. */
    val display: TextStyle,
    /** Screen title. */
    val title: TextStyle,
    /** Section title within a screen. */
    val section: TextStyle,
    /** Item name / primary row text. */
    val item: TextStyle,
    /** Body copy, empty-state explanation. */
    val body: TextStyle,
    /** Secondary line under body copy. */
    val bodyMuted: TextStyle,
    /** Buttons, chips, tabs. */
    val action: TextStyle,
    /** Sizes, dates, counts. Never below 12sp. */
    val meta: TextStyle,
)

private fun s(
    size: Int,
    line: Int,
    weight: FontWeight,
    tracking: Float = 0f,
): TextStyle = TextStyle(
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = tracking.sp,
)

/** Compact (phone portrait). */
val CompactType = DfcType(
    display = s(32, 38, FontWeight.Bold, -0.6f),
    title = s(22, 28, FontWeight.SemiBold, -0.2f),
    section = s(15, 20, FontWeight.SemiBold),
    item = s(15, 21, FontWeight.Medium),
    body = s(14, 21, FontWeight.Normal),
    bodyMuted = s(14, 20, FontWeight.Normal),
    action = s(14, 18, FontWeight.SemiBold),
    meta = s(12, 16, FontWeight.Medium),
)

/** Expanded (tablet, foldable unfolded, landscape large screens). */
val ExpandedType = DfcType(
    display = s(40, 46, FontWeight.Bold, -0.8f),
    title = s(26, 32, FontWeight.SemiBold, -0.3f),
    section = s(17, 22, FontWeight.SemiBold),
    item = s(16, 22, FontWeight.Medium),
    body = s(15, 22, FontWeight.Normal),
    bodyMuted = s(15, 21, FontWeight.Normal),
    action = s(15, 19, FontWeight.SemiBold),
    meta = s(13, 18, FontWeight.Medium),
)

/** Provided by [DfcTheme]; read with [LocalType]. */
val LocalType = staticCompositionLocalOf { CompactType }

/** Read the active type scale. */
val currentType: DfcType
    @Composable @ReadOnlyComposable get() = LocalType.current
