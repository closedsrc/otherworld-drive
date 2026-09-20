package com.dfc.mobile.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import com.dfc.mobile.ui.theme.Spacing
import com.dfc.mobile.RemoteFile
import com.dfc.mobile.ui.components.ConfirmDialog
import com.dfc.mobile.ui.screens.DeleteDialog
import com.dfc.mobile.ui.screens.DetailsDialog
import com.dfc.mobile.ui.screens.NewFolderDialog
import com.dfc.mobile.ui.screens.RenameDialog
import com.dfc.mobile.ui.screens.ShareResultDialog

/**
 * Hosts the dialogs a screen asks for through [DfcViewModel.action].
 *
 * This was missing entirely. The view model has always published UiAction —
 * NewFolder, Rename, Details, Download — and the dialog composables for all of
 * them exist, but no code ever rendered one, so tapping New folder, Rename,
 * Details or Save-to-device set a value nobody read and the screen did nothing
 * at all. Delete was worse: it went straight through to the server with no
 * confirmation, because the confirmation dialog was equally unreachable.
 *
 * One host, mounted once above the navigation graph, so every screen gets the
 * same dialogs and no screen has to own the plumbing.
 */
@Composable
fun ActionHost(
    vm: DfcViewModel,
    snackbar: SnackbarHostState,
) {
    val context = LocalContext.current
    val action by vm.action.collectAsState()

    // The "shown once" share link.
    var shareUrl by remember { mutableStateOf<String?>(null) }

    // A share link that arrives while a dialog is open still has to be shown.
    LaunchedEffect(vm.lastShareUrl) {
        vm.lastShareUrl?.let { shareUrl = it }
    }

    when (val a = action) {
        null -> Unit

        is UiAction.NewFolder -> NewFolderDialog(
            onDismiss = { vm.consumeAction() },
            onConfirm = { name ->
                vm.consumeAction()
                vm.createFolder(name) { ok ->
                    vm.setMessage(if (ok) "Folder created" else "Could not create the folder")
                }
            },
        )

        is UiAction.Rename -> RenameDialog(
            currentName = a.file.name,
            onDismiss = { vm.consumeAction() },
            onConfirm = { newName ->
                vm.consumeAction()
                vm.rename(a.file, newName) { ok ->
                    vm.setMessage(if (ok) "Renamed to $newName" else "Could not rename the file")
                }
            },
        )

        is UiAction.Details -> DetailsDialog(
            file = a.file,
            onDismiss = { vm.consumeAction() },
        )

        is UiAction.ViewerDetails -> ConfirmDialog(
            title = a.item.displayName,
            message = buildString {
                append(com.dfc.mobile.ui.formatBytes(a.item.size))
                if (a.item.dateTaken > 0) {
                    append("\n")
                    append(fullDateLabel(a.item.dateTaken))
                }
                append("\n")
                append(if (a.item.remoteId.isNullOrBlank()) "On this phone only" else "On your drive")
            },
            confirmLabel = "Close",
            onConfirm = { vm.consumeAction() },
            onDismiss = { vm.consumeAction() },
        )

        is UiAction.Download -> {
            vm.consumeAction()
            vm.download(a.file) { name ->
                vm.setMessage(if (name != null) "Saved $name to Downloads" else "Could not save the file")
            }
        }

        is UiAction.Message -> {
            vm.consumeAction()
            LaunchedEffect(a.text) { snackbar.showSnackbar(a.text) }
        }

        is UiAction.DeleteRequest -> DeleteDialog(
            count = a.files.size,
            onDismiss = { vm.consumeAction() },
            onConfirm = {
                val files = a.files
                vm.consumeAction()
                vm.delete(files) { (ok, total) ->
                    vm.setMessage(
                        if (ok == total) {
                            "Moved ${if (total == 1) "1 item" else "$total items"} to trash"
                        } else {
                            "Moved $ok of $total to trash"
                        }
                    )
                }
            },
        )
    }

    shareUrl?.let { url ->
        ShareResultDialog(
            url = url,
            onDismiss = { shareUrl = null; vm.lastShareUrl = null },
        )
    }
}

/** Convenience for screens that want to confirm before a destructive action. */
@Composable
fun rememberDeleteConfirmation(): Pair<List<RemoteFile>, (List<RemoteFile>) -> Unit> {
    var pending by remember { mutableStateOf<List<RemoteFile>>(emptyList()) }
    return pending to { files -> pending = files }
}

@Composable
fun SnackbarOverlay(snackbar: SnackbarHostState) {
    SnackbarHost(hostState = snackbar) { data ->
        androidx.compose.material3.Surface(
            shape = androidx.compose.foundation.shape.RoundedCornerShape(
                com.dfc.mobile.ui.theme.Radii.control
            ),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
            tonalElevation = 6.dp,
            modifier = androidx.compose.ui.Modifier.padding(horizontal = Spacing.md),
        ) {
            Text(
                data.visuals.message,
                modifier = androidx.compose.ui.Modifier.padding(
                    horizontal = Spacing.md, vertical = Spacing.sm
                ),
            )
        }
    }
}
