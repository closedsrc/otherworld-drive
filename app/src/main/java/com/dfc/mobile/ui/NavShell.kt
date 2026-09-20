package com.dfc.mobile.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PhotoAlbum
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.PhotoAlbum
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.dfc.mobile.ui.components.minTouchSize
import com.dfc.mobile.ui.theme.Spacing
import com.dfc.mobile.ui.theme.currentType
import com.dfc.mobile.ui.theme.windowSize

/**
 * Four destinations, ordered by how often they are opened.
 *
 * Photos is first and is the app's start destination: this is a photo product
 * that also stores files, not a file manager that happens to hold photos.
 * Uploads is no longer a tab — it is a state of the library, so it opens from
 * the status line rather than holding a permanent slot the user visits once.
 */
data class NavItem(
    val route: Route,
    val label: String,
    val filled: ImageVector,
    val outlined: ImageVector,
)

val navItems = listOf(
    NavItem(PhotosRoute, "Photos", Icons.Filled.PhotoLibrary, Icons.Outlined.PhotoLibrary),
    NavItem(AlbumsRoute, "Albums", Icons.Filled.PhotoAlbum, Icons.Outlined.PhotoAlbum),
    NavItem(FilesRoute(), "Files", Icons.Filled.Folder, Icons.Outlined.Folder),
    NavItem(SettingsRoute, "Settings", Icons.Filled.Settings, Icons.Outlined.Settings),
)

private fun isSelected(current: Route?, item: NavItem): Boolean = when {
    current == null -> false
    item.route is FilesRoute && current is FilesRoute -> true
    else -> current == item.route
}

/**
 * The navigation shell: a bottom bar, always, on every window size.
 *
 * There was a navigation rail for wide windows. It is gone. This is a phone
 * app — the rail appeared on a 600dp portrait phone as an 80dp slab of
 * vertically stacked labels, the single worst thing the UI ever looked like,
 * and on a tablet it bought nothing a bottom bar does not. A bottom bar is the
 * one navigation control every Android user already knows, keeps the same
 * muscle memory from phone to tablet, and costs a fixed strip at the bottom
 * instead of a column down the side of every screen.
 *
 * Content is capped and centred on a wide window so a four-across photo grid
 * does not stretch its tiles to 400dp and strand a "See all" link a hand-span
 * from the heading it belongs to.
 */
@Composable
fun DfcScaffold(
    currentRoute: Route?,
    onSelect: (Route) -> Unit,
    content: @Composable () -> Unit,
) {
    val w = windowSize
    Column(Modifier.semantics { contentDescription = "Main navigation" }) {
        Box(
            Modifier.weight(1f),
            contentAlignment = Alignment.TopCenter,
        ) {
            Box(
                Modifier
                    .widthIn(max = w.maxContentWidth)
                    .fillMaxHeight(),
            ) {
                content()
            }
        }
        DfcBottomBar(currentRoute = currentRoute, onSelect = onSelect)
    }
}

@Composable
private fun DfcBottomBar(currentRoute: Route?, onSelect: (Route) -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
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
                            modifier = Modifier.size(23.dp),
                        )
                    },
                    label = { Text(item.label, style = currentType.meta) },
                    alwaysShowLabel = true,
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                    modifier = Modifier.minTouchSize(),
                )
            }
        }
    }
}
