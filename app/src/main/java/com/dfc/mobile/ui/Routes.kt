package com.dfc.mobile.ui

import android.net.Uri
import kotlinx.serialization.Serializable

/**
 * The route graph. Every destination is a typed, serializable object, so
 * arguments are compile-checked and deep-linkable — the app used to hold six
 * overlays in three `mutableStateOf` fields, which meant no back handling, no
 * deep links, no URLs, and no restoration after rotation or process death.
 *
 * Screen        — a full destination (tab or pushed screen)
 * Dialog        — a modal on top of the current screen
 */
sealed interface Route

// ---- top-level destinations (tab / rail) -------------------------------

@Serializable
data object HomeRoute : Route

@Serializable
data object PhotosRoute : Route

@Serializable
data object AlbumsRoute : Route

@Serializable
data class FilesRoute(val folderId: String = "", val refresh: Long = 0L) : Route

@Serializable
data object UploadsRoute : Route

@Serializable
data object SettingsRoute : Route

// ---- pushed screens ----------------------------------------------------

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

/** The five surfaces shown in the bottom bar / nav rail. */
val topLevelRoutes = listOf(
    HomeRoute, PhotosRoute, FilesRoute(), UploadsRoute, SettingsRoute,
)

/** Deep-link prefixes accepted by the manifest. */
const val DEEP_LINK_SCHEME = "otherworld"
const val DEEP_LINK_HOST = "drive"

fun shareDeepLink(token: String): Uri =
    Uri.parse("$DEEP_LINK_SCHEME://$DEEP_LINK_HOST/share/$token")

fun fileDeepLink(fileId: String): Uri =
    Uri.parse("$DEEP_LINK_SCHEME://$DEEP_LINK_HOST/file/$fileId")
