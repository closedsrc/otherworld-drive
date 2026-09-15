package com.dfc.mobile

import androidx.compose.foundation.background
import androidx.compose.ui.unit.dp
import com.dfc.mobile.backup.BackupWorker
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dfc.mobile.RemoteFile
import com.dfc.mobile.data.MediaItem
import com.dfc.mobile.ui.Destination
import com.dfc.mobile.ui.DfcBottomBar
import com.dfc.mobile.ui.DfcViewModel
import com.dfc.mobile.ui.theme.DfcTheme
import com.dfc.mobile.ui.screens.DeleteDialog
import com.dfc.mobile.ui.screens.FilesLayout
import com.dfc.mobile.ui.screens.FilesScreen
import com.dfc.mobile.ui.screens.GalleryPreview
import com.dfc.mobile.ui.screens.HomeScreen
import com.dfc.mobile.ui.screens.LinksScreen
import com.dfc.mobile.ui.screens.NewFolderDialog
import com.dfc.mobile.ui.screens.SearchScreen
import com.dfc.mobile.ui.screens.SettingsScreen
import com.dfc.mobile.ui.screens.SetupScreen
import com.dfc.mobile.ui.screens.ShareResultDialog
import com.dfc.mobile.ui.screens.UploadsScreen
import kotlinx.coroutines.launch

/**
 * Single activity, five destinations. The screens are composed here rather than
 * pushed as separate activities so state (folder path, selection, upload
 * progress) survives tab switches without being rebuilt.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DfcTheme {
                // The root surface paints the background for the active theme.
                // Without it the window background shows through, which is a
                // different colour in the other theme.
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    DfcRoot()
                }
            }
        }
    }
}

/** Which full-screen surface is on top of the tab content, if any. */
private sealed interface Overlay {
    data object None : Overlay
    data object Setup : Overlay
    data object Links : Overlay
    data class Preview(val items: List<MediaItem>, val index: Int) : Overlay
}

@Composable
private fun DfcRoot() {
    val context = LocalContext.current
    val vm: DfcViewModel = viewModel()
    // Compose 1.6.8 is pinned by the Kotlin 1.9.24 compiler, and it never
    // provides the LocalLifecycleOwner that collectAsStateWithLifecycle()
    // reads, so the flow is collected directly.
    val ui by vm.ui.collectAsState()
    val scope = rememberCoroutineScope()

    var tab by remember { mutableStateOf(Destination.HOME) }
    var layout by remember { mutableStateOf(FilesLayout.GRID) }
    var overlay by remember { mutableStateOf<Overlay>(Overlay.None) }
    var showNewFolder by remember { mutableStateOf(false) }
    var shareUrl by remember { mutableStateOf<String?>(null) }
    var pendingShare by remember { mutableStateOf<RemoteFile?>(null) }

    val snackbar = remember { SnackbarHostState() }

    // Every tab draws edge to edge, so the status bar inset is applied once
    // here instead of in each screen. Without it the page title lands under the
    // clock and the battery icons.
    val screenInsets = Modifier.fillMaxSize().statusBarsPadding()

    // Media permission is requested once, on first composition. A denial is not
    // fatal: browsing the drive still works, only local scanning is blocked.
    val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        if (granted.values.any { it }) vm.refresh()
        else scope.launch {
            snackbar.showSnackbar("Photo access denied. Backing up this phone needs it.")
        }
    }

    LaunchedEffect(Unit) {
        val needed = if (Build.VERSION.SDK_INT >= 33) {
            listOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
        } else {
            listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        val missing = needed.filter {
            androidx.core.content.ContextCompat.checkSelfPermission(context, it) !=
                PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) permissionLauncher.launch(missing.toTypedArray())
    }

    // A fresh install lands straight on setup rather than an empty drive.
    LaunchedEffect(ui.configured) {
        if (!ui.configured && overlay == Overlay.None) overlay = Overlay.Setup
    }

    // The Files tab fetches its root listing the first time it is opened.
    LaunchedEffect(tab) {
        if (tab == Destination.FILES) vm.loadRootIfNeeded()
    }

    Box(Modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = tab,
            transitionSpec = {
                // Moving right along the bar pushes the new screen in from the
                // right; the small offset signals direction without a full
                // page slide that would fight the scroll position.
                val forward = targetState.ordinal > initialState.ordinal
                val push = if (forward) 1 else -1
                (
                    slideInHorizontally(tween(240)) { width -> push * width / 12 } +
                        fadeIn(tween(200))
                    ) togetherWith (
                    slideOutHorizontally(tween(200)) { width -> -push * width / 12 } +
                        fadeOut(tween(140))
                    )
            },
            label = "tab",
        ) { dest ->
            when (dest) {
                Destination.HOME -> HomeScreen(
                    ui = ui,
                    classify = vm::classify,
                    onBackupNow = {
                        if (ui.configured) {
                            vm.backupNow()
                            scope.launch { snackbar.showSnackbar("Backup started") }
                        } else overlay = Overlay.Setup
                    },
                    onOpenUploads = { tab = Destination.UPLOADS },
                    onOpenFiles = { tab = Destination.FILES },
                    onShare = { overlay = Overlay.Links },
                    onCreateFolder = { tab = Destination.FILES; showNewFolder = true },
                    onOpenPreview = { item, list -> overlay = Overlay.Preview(list, list.indexOf(item)) },
                    modifier = screenInsets,
                )

                Destination.FILES -> FilesScreen(
                    ui = ui,
                    layout = layout,
                    onToggleLayout = {
                        layout = if (layout == FilesLayout.GRID) FilesLayout.LIST
                        else FilesLayout.GRID
                    },
                    onOpenFolder = { vm.openFolder(it) },
                    onNavigateDepth = { vm.navigateToDepth(it) },
                    onOpenFile = { file ->
                        val kind = vm.classify(file)
                        if (kind == DfcViewModel.Kind.IMAGE || kind == DfcViewModel.Kind.VIDEO) {
                            overlay = Overlay.Preview(
                                items = listOf(
                                    MediaItem(
                                        id = -1,
                                        isVideo = kind == DfcViewModel.Kind.VIDEO,
                                        size = file.size,
                                        dateTaken = file.modTime,
                                        displayName = file.name,
                                        remoteId = file.id,
                                        state = 1,
                                        updatedAt = file.modTime,
                                    )
                                ),
                                index = 0,
                            )
                        } else {
                            scope.launch {
                                snackbar.showSnackbar(
                                    "\"${file.name}\" has no in-app viewer yet. Download it from the web dashboard."
                                )
                            }
                        }
                    },
                    onOpenGallery = { item, list -> overlay = Overlay.Preview(list, list.indexOf(item)) },
                    onDelete = { files ->
                        vm.delete(files) { (deleted, failed) ->
                            scope.launch {
                                snackbar.showSnackbar(
                                    when {
                                        deleted > 0 && failed == 0 ->
                                            if (deleted == 1) "Moved 1 item to the trash"
                                            else "Moved $deleted items to the trash"
                                        deleted > 0 -> "Moved $deleted, $failed failed"
                                        else -> "Delete failed"
                                    }
                                )
                            }
                        }
                    },
                    onShare = { file ->
                        pendingShare = file
                        // Links expire after a week by default on the server; the
                        // app asks explicitly rather than inventing a duration.
                        vm.share(file, ttlDays = 7) { url -> shareUrl = url; pendingShare = null }
                    },
                    onRefresh = { vm.refresh() },
                    classify = vm::classify,
                    modifier = screenInsets,
                )

                Destination.UPLOADS -> UploadsScreen(
                    ui = ui,
                    onBackupNow = { vm.backupNow() },
                    onOpenFiles = { tab = Destination.FILES },
                    modifier = screenInsets,
                )

                Destination.SEARCH -> SearchScreen(
                    ui = ui,
                    onOpenFile = { file ->
                        scope.launch {
                            snackbar.showSnackbar("\"${file.name}\" is not viewable in the app yet")
                        }
                    },
                    onOpenFolder = { folder -> vm.openFolder(folder); tab = Destination.FILES },
                    onOpenGallery = { item, list -> overlay = Overlay.Preview(list, list.indexOf(item)) },
                    onSearchRemote = { query -> vm.searchRemote(query) },
                    classify = vm::classify,
                    modifier = screenInsets,
                )

                Destination.SETTINGS -> SettingsScreen(
                    ui = ui,
                    serverUrl = Prefs.get(context).serverUrl,
                    wifiOnly = ui.wifiOnly,
                    onToggleWifiOnly = { value ->
                        Prefs.get(context).wifiOnly = value
                        BackupWorker.schedule(context, value)
                        vm.refresh()
                    },
                    onOpenServerSetup = { overlay = Overlay.Setup },
                    onBackupNow = { vm.backupNow() },
                    onOpenUploads = { tab = Destination.UPLOADS },
                    modifier = screenInsets,
                )
            }
        }

        // The bar hides while a full-screen surface owns the screen.
        if (overlay == Overlay.None) {
            DfcBottomBar(
                current = tab,
                onSelect = { tab = it },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }

        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 96.dp),
        )

        when (val o = overlay) {
            Overlay.None -> Unit
            Overlay.Setup -> Box(
                Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            ) {
                SetupScreen(
                    onConnected = {
                        overlay = Overlay.None
                        vm.refresh()
                        scope.launch { snackbar.showSnackbar("Connected") }
                    },
                    onCancel = if (ui.configured) {
                        { overlay = Overlay.None }
                    } else null,
                )
            }
            Overlay.Links -> Box(
                Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            ) {
                LinksScreen(
                    ui = ui,
                    onBack = { overlay = Overlay.None },
                    onRefresh = { vm.loadShares(force = true) },
                    onRevoke = { share ->
                        vm.revoke(share)
                        scope.launch { snackbar.showSnackbar("Link revoked") }
                    },
                )
            }
            is Overlay.Preview -> GalleryPreview(
                items = o.items,
                startIndex = o.index,
                onClose = { overlay = Overlay.None },
            )
        }
    }

    if (showNewFolder) {
        NewFolderDialog(
            onDismiss = { showNewFolder = false },
            onConfirm = { name ->
                showNewFolder = false
                vm.createFolder(name) { ok ->
                    scope.launch {
                        snackbar.showSnackbar(if (ok) "Folder created" else "Could not create the folder")
                    }
                }
            },
        )
    }

    shareUrl?.let { url ->
        ShareResultDialog(url = url, onDismiss = { shareUrl = null })
    }
}
