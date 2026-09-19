package com.dfc.mobile.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PhotoAlbum
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.PhotoAlbum
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.dfc.mobile.ui.theme.Radii
import com.dfc.mobile.ui.theme.Spacing

/**
 * The five destinations, in the order they appear in the bar. Photos leads
 * because the library is what the app is opened for: what is on this phone, and
 * is it safe. Everything else is one step behind it.
 */
enum class Destination(
    val route: String,
    val label: String,
    val filled: ImageVector,
    val outlined: ImageVector,
) {
    PHOTOS("photos", "Photos", Icons.Filled.PhotoLibrary, Icons.Outlined.PhotoLibrary),
    ALBUMS("albums", "Albums", Icons.Filled.PhotoAlbum, Icons.Outlined.PhotoAlbum),
    FILES("files", "Files", Icons.Filled.Folder, Icons.Outlined.Folder),
    UPLOADS("uploads", "Uploads", Icons.Filled.CloudUpload, Icons.Outlined.CloudUpload),
    SETTINGS("settings", "Settings", Icons.Filled.Settings, Icons.Outlined.Settings),
}

/**
 * Floating bottom navigation. It sits above the content on a raised surface so
 * the list keeps its full height underneath, and it reserves its own footprint
 * at the bottom of every scrollable screen.
 */
@Composable
fun DfcBottomBar(
    current: Destination,
    onSelect: (Destination) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.md, vertical = Spacing.sm)
            .navigationBarsPadding(),
        shape = RoundedCornerShape(Radii.sheet),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 0.dp,
        shadowElevation = 12.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = Spacing.sm),
        ) {
            Destination.entries.forEach { dest ->
                NavItem(
                    dest = dest,
                    selected = dest == current,
                    onClick = { onSelect(dest) },
                    // Equal columns. Sized to content, the item that was selected
                    // grew by the width of its label (measured: 162px vs 192px),
                    // so choosing a tab slid every other tab sideways.
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun NavItem(
    dest: Destination,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Icon and label both react, so the state change registers at a glance
    // without a moving indicator that would fight the content.
    val scale by animateFloatAsState(
        targetValue = if (selected) 1.06f else 1f,
        animationSpec = tween(160),
        label = "navScale",
    )
    val tint = if (selected) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.onSurfaceVariant

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(Radii.control))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(vertical = Spacing.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = if (selected) dest.filled else dest.outlined,
            contentDescription = dest.label,
            tint = tint,
            modifier = Modifier
                .size(22.dp)
                .scale(scale),
        )
        Spacer(Modifier.height(3.dp))
        // A fixed-height slot rather than AnimatedVisibility plus a conditional
        // spacer: during the exit animation both the fading label and the spacer
        // occupied layout, so the item grew 16dp and the whole bar jolted on
        // every tab change.
        Box(Modifier.height(16.dp), contentAlignment = Alignment.Center) {
            // Explicitly the top-level overload: inside a ColumnScope (this Box
            // still has it as an implicit receiver) the scoped variant wins
            // resolution and cannot be called from here.
            androidx.compose.animation.AnimatedVisibility(
                visible = selected,
                enter = fadeIn(tween(160)) + scaleIn(tween(160), initialScale = 0.8f),
                exit = fadeOut(tween(100)) + scaleOut(tween(100), targetScale = 0.8f),
            ) {
                Text(
                    text = dest.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = tint,
                )
            }
        }
    }
}

/**
 * Press feedback for the surfaces that behave like objects (cards, tiles,
 * chips). The surface dips under the finger and springs back, which is what
 * makes a tap feel handled. A 3% dip is enough to read; more looks like the
 * card is falling over.
 */
@Composable
fun Modifier.pressFeedback(
    interactionSource: MutableInteractionSource,
    pressedScale: Float = 0.97f,
): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = tween(120),
        label = "press",
    )
    return this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}



/** Page title block, used where a screen has no selection bar of its own. */
@Composable
fun ScreenTitle(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = Spacing.md, end = Spacing.sm, top = Spacing.md, bottom = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        trailing?.invoke()
    }
}



/** Icon button used in every top bar, sized to a 44dp touch target. */
@Composable
fun BarIcon(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(48.dp)
            .clip(RoundedCornerShape(Radii.control))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
    }
}


