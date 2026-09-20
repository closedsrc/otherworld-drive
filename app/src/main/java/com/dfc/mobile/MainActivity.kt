package com.dfc.mobile

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.dfc.mobile.ui.SnackbarOverlay
import com.dfc.mobile.ui.ActionHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.dfc.mobile.ui.AlbumRoute
import com.dfc.mobile.ui.AlbumsRoute
import com.dfc.mobile.ui.DfcScaffold
import com.dfc.mobile.ui.DfcViewModel
import com.dfc.mobile.ui.FilesRoute
import com.dfc.mobile.ui.LinksRoute
import com.dfc.mobile.ui.PhotosRoute
import com.dfc.mobile.ui.Route
import com.dfc.mobile.ui.SearchRoute
import com.dfc.mobile.ui.SettingsRoute
import com.dfc.mobile.ui.SetupRoute
import com.dfc.mobile.ui.TrashRoute
import com.dfc.mobile.ui.UploadsRoute
import com.dfc.mobile.ui.ViewerRoute
import com.dfc.mobile.ui.wearsShell
import com.dfc.mobile.ui.screens.AlbumDetailScreen
import com.dfc.mobile.ui.screens.AlbumsScreen
import com.dfc.mobile.ui.screens.FilesLayout
import com.dfc.mobile.ui.screens.FilesScreen
import com.dfc.mobile.ui.screens.GalleryPreview
import com.dfc.mobile.ui.screens.LinksScreen
import com.dfc.mobile.ui.screens.PermissionPrimer
import com.dfc.mobile.ui.screens.PhotosScreen
import com.dfc.mobile.ui.screens.SearchScreen
import com.dfc.mobile.ui.screens.SettingsScreen
import com.dfc.mobile.ui.screens.SetupScreen
import com.dfc.mobile.ui.screens.SortKey
import com.dfc.mobile.ui.screens.TrashScreen
import com.dfc.mobile.ui.screens.UploadsScreen
import com.dfc.mobile.ui.theme.DfcTheme

/**
 * Single activity, one navigation graph, two chromes.
 *
 * The graph is flat — every destination is a typed route — but each route
 * declares whether it wears the tab shell. The four bar destinations
 * (Photos, Albums, Files, Settings) do. Everything pushed on top of them
 * (viewer, setup, search, trash, links, album detail) renders full-bleed.
 *
 * This replaces the old shape, where a single scaffold wrapped the whole
 * NavHost: that is why opening a photo left the navigation bar and its labels
 * burning beside the image, and why the setup form shared a screen with tabs
 * it had nothing to do with.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DfcTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    DfcApp(intent = intent)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setContent {
            DfcTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    DfcApp(intent = intent)
                }
            }
        }
    }
}

@Composable
private fun DfcApp(intent: Intent?) {
    val context = LocalContext.current
    val vm: DfcViewModel = viewModel()
    val ui by vm.ui.collectAsState()
    val nav = rememberNavController()

    var sort by rememberSaveable { mutableStateOf(SortKey.NAME) }
    var layout by rememberSaveable { mutableStateOf(FilesLayout.GRID) }

    // ---- permissions -------------------------------------------------
    val permissions = remember {
        buildList {
            if (Build.VERSION.SDK_INT >= 33) {
                add(Manifest.permission.READ_MEDIA_IMAGES)
                add(Manifest.permission.READ_MEDIA_VIDEO)
                add(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }
    }
    var showPrimer by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) showPrimer = true
    }

    val current = currentNavRoute(nav)
    val wearsShell = current == null || current.wearsShell
    val snackbar = remember { SnackbarHostState() }

    Box(Modifier.fillMaxSize()) {
        if (wearsShell) {
            DfcScaffold(
                currentRoute = current,
                onSelect = { route ->
                    nav.navigate(route) {
                        popUpTo(PhotosRoute) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
            ) {
                NavGraph(nav, vm, ui, sort, layout, { sort = it }, { layout = it })
            }
        } else {
            NavGraph(nav, vm, ui, sort, layout, { sort = it }, { layout = it })
        }

        // Dialogs, the "copy it now" share sheet and the snackbar, mounted once
        // above every destination. Nothing rendered UiAction before this, so New
        // folder, Rename, Details and Save-to-device were dead taps and Delete
        // fired with no confirmation.
        ActionHost(vm = vm, snackbar = snackbar)
        SnackbarOverlay(snackbar)
    }

    if (showPrimer) {
        PermissionPrimer(
            onContinue = { showPrimer = false; requestPerms(permissions) },
            onSkip = { showPrimer = false },
        )
    }

    // A share link opened the app: land on the file it names.
    LaunchedEffect(intent) {
        val uri = intent?.data ?: return@LaunchedEffect
        val segments = uri.pathSegments
        if (segments.firstOrNull() == "file") {
            val id = segments.getOrNull(1)
            if (!id.isNullOrBlank()) nav.navigate(FilesRoute(folderId = id))
        }
    }
}

@Composable
private fun NavGraph(
    nav: androidx.navigation.NavHostController,
    vm: DfcViewModel,
    ui: DfcViewModel.Ui,
    sort: SortKey,
    layout: FilesLayout,
    onSortChange: (SortKey) -> Unit,
    onLayoutChange: (FilesLayout) -> Unit,
) {
    NavHost(navController = nav, startDestination = PhotosRoute) {
        composable<PhotosRoute> {
            var filter by rememberSaveable { mutableStateOf(DfcViewModel.LibraryFilter.ALL) }
            PhotosScreen(
                ui = ui,
                filter = filter,
                onFilterChange = { filter = it },
                onOpenItem = { item, list -> nav.navigate(viewerRoute(list, list.indexOf(item))) },
                onBackupNow = { vm.backupNow() },
                onOpenUploads = { nav.navigate(UploadsRoute) },
                onOpenAlbums = { nav.navigate(AlbumsRoute) },
                onOpenAlbum = { name -> nav.navigate(AlbumRoute(name)) },
                onOpenSearch = { nav.navigate(SearchRoute) },
                onOpenSetup = { nav.navigate(SetupRoute) },
                onRequestAccess = { },
                onShareId = { vm.shareId(it, 7) { url -> vm.lastShareUrl = url } },
                onRefresh = { vm.refresh() },
                hasPhotoAccess = true,
            )
        }
        composable<AlbumsRoute> {
            AlbumsScreen(
                ui = ui,
                onOpenAlbum = { name -> nav.navigate(AlbumRoute(name)) },
            )
        }
        composable<AlbumRoute> { entry ->
            val route = entry.toRoute<AlbumRoute>()
            val album = ui.albums.firstOrNull { it.name == route.name }
            if (album == null) {
                LaunchedEffect(Unit) { nav.popBackStack() }
            } else {
                AlbumDetailScreen(
                    album = album,
                    items = ui.timeline.filter { it.album == album.name },
                    onBack = { nav.popBackStack() },
                    onOpen = { item, list -> nav.navigate(viewerRoute(list, list.indexOf(item))) },
                )
            }
        }
        composable<FilesRoute> { entry ->
            val route = entry.toRoute<FilesRoute>()
            LaunchedEffect(route.refresh) { vm.loadRootIfNeeded() }
            FilesScreen(
                ui = ui,
                sort = sort,
                onSortChange = onSortChange,
                layout = layout,
                onToggleLayout = {
                    onLayoutChange(if (layout == FilesLayout.GRID) FilesLayout.LIST else FilesLayout.GRID)
                },
                onOpenFolder = { vm.openFolder(it) },
                onNavigateDepth = { vm.navigateToDepth(it) },
                onOpenFile = { file ->
                    when (vm.classify(file)) {
                        DfcViewModel.Kind.IMAGE, DfcViewModel.Kind.VIDEO ->
                            nav.navigate(viewerRoute(listOf(file), 0))
                        else -> vm.requestDownload(file)
                    }
                },
                onDelete = { files -> vm.requestDelete(files) },
                onShare = { file -> vm.share(file, 7) { url -> vm.lastShareUrl = url } },
                onRename = { file -> vm.requestRename(file) },
                onDownload = { file -> vm.requestDownload(file) },
                onDetails = { file -> vm.requestDetails(file) },
                onRefresh = { vm.refreshFolder() },
                onCreateFolder = { vm.requestNewFolder() },
                onOpenTrash = { nav.navigate(TrashRoute) },
                classify = vm::classify,
            )
        }
        composable<SettingsRoute> {
            SettingsScreen(
                ui = ui,
                deviceSubtitle = "Signed in as this phone",
                appLockEnabled = false,
                appLockAvailable = false,
                wifiOnly = ui.wifiOnly,
                onToggleWifiOnly = { vm.setWifiOnly(it) },
                onToggleAppLock = { },
                onSignOut = { vm.signOut() },
                onOpenServerSetup = { nav.navigate(SetupRoute) },
                onOpenUploads = { nav.navigate(UploadsRoute) },
                onOpenLinks = { nav.navigate(LinksRoute) },
                onBackupNow = { vm.backupNow() },
            )
        }

        // ---- full-bleed destinations --------------------------------------
        composable<UploadsRoute> {
            UploadsScreen(
                ui = ui,
                onBack = { nav.popBackStack() },
                onBackupNow = { vm.backupNow() },
                onOpenSettings = { nav.navigate(SettingsRoute) },
            )
        }
        composable<SearchRoute> {
            SearchScreen(
                ui = ui,
                onOpenFile = { file -> vm.requestDownload(file) },
                onOpenFolder = { folder ->
                    vm.openFolder(folder)
                    nav.navigate(FilesRoute(refresh = System.currentTimeMillis()))
                },
                onOpenGallery = { item, list -> nav.navigate(viewerRoute(list, list.indexOf(item))) },
                onSearchRemote = { vm.searchRemote(it) },
                classify = vm::classify,
                onClose = { nav.popBackStack() },
            )
        }
        composable<TrashRoute> {
            TrashScreen(onBack = { nav.popBackStack() }, onMessage = { vm.setMessage(it) })
        }
        composable<LinksRoute> {
            LinksScreen(
                ui = ui,
                onBack = { nav.popBackStack() },
                onRefresh = { vm.loadShares(force = true) },
                onRevoke = { vm.revoke(it) },
            )
        }
        composable<SetupRoute> {
            SetupScreen(
                onConnected = { nav.popBackStack(); vm.refresh() },
                onCancel = { nav.popBackStack() },
            )
        }
        composable<ViewerRoute> { entry ->
            val route = entry.toRoute<ViewerRoute>()
            val items = vm.viewerItems(route)
            GalleryPreview(
                items = items,
                startIndex = route.index.coerceIn(0, items.lastIndex.coerceAtLeast(0)),
                onClose = { nav.popBackStack() },
                onShare = { item -> vm.shareViewer(item) },
                onDownload = { item -> vm.downloadViewer(item) },
                onDelete = { item -> vm.deleteViewer(item); nav.popBackStack() },
                onDetails = { item -> vm.requestViewerDetails(
                    com.dfc.mobile.ui.ViewerRequest(item.displayName, item.size, item.dateTaken, item.remoteId)
                ) },
            )
        }
    }
}

private fun requestPerms(perms: List<String>) {
    PermissionBridge.pending = perms
}

/** Permission requests are driven by the activity; the composable only queues them. */
object PermissionBridge {
    @Volatile var pending: List<String> = emptyList()
}

/** Resolve which nav item is selected from the back stack. */
@Composable
private fun currentNavRoute(nav: androidx.navigation.NavHostController): Route? {
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route ?: return null
    return when {
        route.contains("PhotosRoute") -> PhotosRoute
        route.contains("AlbumsRoute") -> AlbumsRoute
        route.contains("AlbumRoute") -> AlbumsRoute
        route.contains("FilesRoute") -> FilesRoute()
        route.contains("SettingsRoute") -> SettingsRoute
        route.contains("UploadsRoute") -> UploadsRoute
        route.contains("SearchRoute") -> SearchRoute
        route.contains("TrashRoute") -> TrashRoute
        route.contains("LinksRoute") -> LinksRoute
        route.contains("SetupRoute") -> SetupRoute
        route.contains("ViewerRoute") -> ViewerRoute()
        else -> null
    }
}

/** Build a viewer route from either local media or drive files. */
private fun viewerRoute(items: List<Any>, index: Int): Route {
    val first = items.getOrNull(index)
    return ViewerRoute(
        fileId = when (first) {
            is com.dfc.mobile.data.MediaItem -> first.remoteId ?: ""
            is com.dfc.mobile.RemoteFile -> first.id
            else -> ""
        },
        remote = first is com.dfc.mobile.RemoteFile,
        index = index,
    )
}
