package com.dfc.mobile.ui

import android.net.Uri
import kotlinx.serialization.Serializable

/**
 * The route graph. Every destination is a typed, serializable object, so
 * arguments are compile-checked and deep-linkable.
 *
 * Screen — a full destination (tab or pushed screen)
 */
sealed interface Route

// ---- the four bar destinations -----------------------------------------

@Serializable
data object PhotosRoute : Route

@Serializable
data object AlbumsRoute : Route

@Serializable
data class FilesRoute(val folderId: String = "", val refresh: Long = 0L) : Route

@Serializable
data object SettingsRoute : Route

// ---- pushed screens ----------------------------------------------------

@Serializable
data class AlbumRoute(val name: String) : Route

@Serializable
data object UploadsRoute : Route

@Serializable
data object SearchRoute : Route

@Serializable
data object TrashRoute : Route

@Serializable
data object LinksRoute : Route

@Serializable
data object SetupRoute : Route

@Serializable
data class ViewerRoute(
    val fileId: String = "",
    val remote: Boolean = false,
    val index: Int = 0,
) : Route

/**
 * Whether a route wears the tab bar / rail.
 *
 * Only the four bar destinations do. The viewer, setup, search, trash, links
 * and an album detail are pushed *over* the shell and render full-bleed — a
 * photo should not have a navigation bar lit up beside it, and a setup form
 * should not sit in a strip between tabs it does not belong to.
 */
val Route.wearsShell: Boolean
    get() = when (this) {
        is PhotosRoute -> true
        is AlbumsRoute -> true
        is FilesRoute -> true
        is SettingsRoute -> true
        else -> false
    }

/** Deep-link prefixes accepted by the manifest. */
const val DEEP_LINK_SCHEME = "otherworld"
const val DEEP_LINK_HOST = "drive"

fun shareDeepLink(token: String): Uri =
    Uri.parse("$DEEP_LINK_SCHEME://$DEEP_LINK_HOST/share/$token")

fun fileDeepLink(fileId: String): Uri =
    Uri.parse("$DEEP_LINK_SCHEME://$DEEP_LINK_HOST/file/$fileId")
