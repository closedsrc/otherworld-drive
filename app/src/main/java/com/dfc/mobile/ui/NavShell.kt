package com.dfc.mobile.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PhotoAlbum
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.PhotoAlbum
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.dfc.mobile.ui.components.minTouchSize
import com.dfc.mobile.ui.theme.Spacing
import com.dfc.mobile.ui.theme.currentType
import com.dfc.mobile.ui.theme.windowSize

/**
 * The navigation shell: bottom bar on phones, rail from medium width up.
 *
 * The old shell was a floating pill stretched across 1600px with ~320px tap
 * targets, paired with a gradient scrim whose job — per its own comment — was to
 * hide content bleeding out above the bar. That scrim was a symptom: the bar
 * floated over the content instead of sitting in a layout that reserves space
 * for it. Here the bar/rail is a sibling of the content in a Row/Column, so
 * nothing bleeds and no scrim is needed.
 */
data class NavItem(
    val route: Route,
    val label: String,
    val filled: ImageVector,
    val outlined: ImageVector,
)

val navItems = listOf(
    NavItem(HomeRoute, "Home", Icons.Filled.Home, Icons.Outlined.Home),
    NavItem(PhotosRoute, "Photos", Icons.Filled.PhotoLibrary, Icons.Outlined.PhotoLibrary),
    NavItem(AlbumsRoute, "Albums", Icons.Filled.PhotoAlbum, Icons.Outlined.PhotoAlbum),
    NavItem(FilesRoute(), "Files", Icons.Filled.Folder, Icons.Outlined.Folder),
    NavItem(UploadsRoute, "Uploads", Icons.Filled.CloudUpload, Icons.Outlined.CloudUpload),
    NavItem(SettingsRoute, "Settings", Icons.Filled.Settings, Icons.Outlined.Settings),
)

private fun isSelected(current: Route?, item: NavItem): Boolean = when {
    current == null -> false
    item.route is FilesRoute && current is FilesRoute -> true
    else -> current == item.route
}

/**
 * Lays out [content] beside a rail, or above a bottom bar, depending on width.
 * [currentRoute] is read from the nav back stack so the selection is always
 * truthful instead of tracked in a separate variable.
 */
@Composable
fun DfcScaffold(
    currentRoute: Route?,
    onSelect: (Route) -> Unit,
    content: @Composable () -> Unit,
) {
    val w = windowSize
    if (w.useRail) {
        Row(Modifier.fillMaxHeight().semantics { contentDescription = "Main navigation" }) {
            DfcNavRail(currentRoute = currentRoute, onSelect = onSelect)
            Box(Modifier.weight(1f)) { content() }
        }
    } else {
        Column(Modifier.semantics { contentDescription = "Main navigation" }) {
            Box(Modifier.weight(1f)) { content() }
            DfcBottomBar(currentRoute = currentRoute, onSelect = onSelect)
        }
    }
}

@Composable
private fun DfcBottomBar(currentRoute: Route?, onSelect: (Route) -> Unit) {
    Surface(
        tonalElevation = 3.dp,
        color = MaterialTheme.colorScheme.surface,
    ) {
        NavigationBar(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars),
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            tonalElevation = 0.dp,
        ) {
            navItems.forEach { item ->
                val selected = isSelected(currentRoute, item)
                NavigationBarItem(
                    selected = selected,
                    onClick = { onSelect(item.route) },
                    icon = {
                        Icon(
                            imageVector = if (selected) item.filled else item.outlined,
                            contentDescription = null,
                        )
                    },
                    label = { Text(item.label, style = currentType.meta) },
                    alwaysShowLabel = true,
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                    ),
                    modifier = Modifier.minTouchSize(),
                )
            }
        }
    }
}

@Composable
private fun DfcNavRail(currentRoute: Route?, onSelect: (Route) -> Unit) {
    Surface(
        tonalElevation = 2.dp,
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.width(80.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .padding(vertical = Spacing.md)
                .selectableGroup(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            navItems.forEach { item ->
                val selected = isSelected(currentRoute, item)
                NavigationRailItem(
                    selected = selected,
                    onClick = { onSelect(item.route) },
                    icon = {
                        Icon(
                            imageVector = if (selected) item.filled else item.outlined,
                            contentDescription = null,
                            modifier = Modifier.size(22.dp),
                        )
                    },
                    label = { Text(item.label, style = currentType.meta) },
                    colors = NavigationRailItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                    ),
                    modifier = Modifier.minTouchSize(),
                )
                Spacer(Modifier.size(Spacing.sm))
            }
        }
    }
}
