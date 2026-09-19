package com.dfc.mobile.ui.screens

import com.dfc.mobile.ui.formatBytes
import com.dfc.mobile.DfcApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AllInclusive
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.LockClock
import androidx.compose.material.icons.outlined.Logout
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dfc.mobile.ui.DfcViewModel
import com.dfc.mobile.ui.ScreenTitle
import com.dfc.mobile.ui.components.PrimaryButton
import com.dfc.mobile.ui.components.StorageRing
import com.dfc.mobile.ui.theme.Radii
import com.dfc.mobile.ui.theme.Spacing
import com.dfc.mobile.ui.theme.currentType

/**
 * Settings groups by what the user came to change: where the data goes, how it
 * gets there, and what the app is doing. Anything the server cannot back is
 * labelled as such rather than offered as a switch that does nothing.
 */
@Composable
fun SettingsScreen(
    ui: DfcViewModel.Ui,
    deviceSubtitle: String,
    appLockEnabled: Boolean,
    appLockAvailable: Boolean,
    wifiOnly: Boolean,
    onToggleWifiOnly: (Boolean) -> Unit,
    onToggleAppLock: (Boolean) -> Unit,
    onSignOut: () -> Unit,
    onOpenServerSetup: () -> Unit,
    onBackupNow: () -> Unit,
    onOpenUploads: () -> Unit,
    onOpenLinks: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(bottom = Spacing.navClearance),
    ) {
        item(key = "title") { ScreenTitle(title = "Settings") }

        item(key = "account") {
            GroupHeader("Account")
            SettingRow(
                icon = Icons.Outlined.Description,
                title = "This device",
                subtitle = deviceSubtitle,
                onClick = onOpenServerSetup,
            )
            SettingRow(
                icon = Icons.Outlined.PhotoLibrary,
                title = "Photos on this phone",
                subtitle = "${ui.backedUp} backed up, ${ui.pending} waiting",
                onClick = onOpenUploads,
            )
        }

        item(key = "storage") {
            Spacer(Modifier.height(Spacing.lg))
            GroupHeader("Drive")
            // The storage figures moved here from the retired Home tab. Settings
            // is where a storage total is looked for, and the library that
            // replaced Home is about photos, not about the server's disk.
            Box(Modifier.padding(horizontal = Spacing.md)) {
                StorageCard(
                    storage = ui.storage,
                    storageError = ui.storageError,
                    loading = ui.loading,
                )
            }
            Spacer(Modifier.height(Spacing.sm))
            SettingRow(
                icon = Icons.Outlined.AllInclusive,
                title = "Unlimited storage",
                subtitle = "Private infrastructure",
            )
            SettingRow(
                icon = Icons.Outlined.Link,
                title = "Public links",
                subtitle = "Files you shared, with their expiry and download counts",
                onClick = onOpenLinks,
            )
        }

        item(key = "transfers") {
            Spacer(Modifier.height(Spacing.lg))
            GroupHeader("Transfers")
            SettingRow(
                icon = Icons.Outlined.Wifi,
                title = "Wi-Fi only",
                subtitle = if (wifiOnly) "Uploads wait for an unmetered connection"
                else "Uploads may use mobile data",
                trailing = {
                    Switch(checked = wifiOnly, onCheckedChange = onToggleWifiOnly)
                },
                onClick = { onToggleWifiOnly(!wifiOnly) },
            )
            Box(Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm)) {
                PrimaryButton(
                    text = "Back up now",
                    onClick = onBackupNow,
                    enabled = ui.configured,
                )
            }
        }

        item(key = "security") {
            Spacer(Modifier.height(Spacing.lg))
            GroupHeader("Security")
            SettingRow(
                icon = Icons.Outlined.Star,
                title = "App lock",
                subtitle = when {
                    !appLockAvailable ->
                        "No screen lock is set up on this phone. Set a PIN or biometric unlock first."
                    appLockEnabled -> "Biometric or PIN check when the app opens"
                    else -> "Ask for the phone's screen lock when the app opens"
                },
                trailing = {
                    Switch(
                        checked = appLockEnabled,
                        enabled = appLockAvailable,
                        onCheckedChange = onToggleAppLock,
                    )
                },
                onClick = { if (appLockAvailable) onToggleAppLock(!appLockEnabled) },
            )
            SettingRow(
                icon = Icons.Outlined.Logout,
                title = "Sign out",
                subtitle = "Disconnect this phone. Its access key is removed locally; " +
                    "revoke the device from the web dashboard to also cut server access.",
                onClick = onSignOut,
            )
        }

        item(key = "about") {
            Spacer(Modifier.height(Spacing.lg))
            GroupHeader("About")
            SettingRow(
                icon = Icons.Outlined.LockClock,
                title = "Key storage",
                subtitle = "This device's access key is encrypted with the phone's " +
                    "Keystore and never leaves the device except to authenticate uploads.",
            )
            Box(Modifier.padding(horizontal = Spacing.md, vertical = Spacing.md)) {
                Text(
                    text = "Otherworld Drive 3.1  ·  Companion app for a self-hosted drive",
                    style = currentType.meta,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * What the drive holds. The centre figure is the byte total the server measured;
 * the ring only takes a progress value while a transfer is live, so a static card
 * never implies work that is not happening.
 */
@Composable
private fun StorageCard(
    storage: DfcApi.Stats?,
    storageError: String?,
    loading: Boolean,
) {
    val shape = RoundedCornerShape(Radii.card)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(Spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Three honest readings, not one placeholder: a first load, a figure the
        // server did not return, and the real total. A bare "..." with no cause
        // is indistinguishable from a broken app.
        StorageRing(
            // Null: this card reports what the drive holds, and no transfer is in
            // view on this screen, so the arc stays an empty track rather than
            // implying work that is not happening here.
            progress = null,
            centerValue = when {
                storage != null -> formatBytes(storage.totalBytes)
                loading -> "..."
                else -> "unknown"
            },
            centerLabel = when {
                storage != null -> "stored"
                loading -> "reading the drive"
                else -> "no answer from the server"
            },
        )

        Spacer(Modifier.height(Spacing.lg))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Stat(
                value = (storage?.filesCount ?: 0).toString(),
                label = "files",
                modifier = Modifier.weight(1f),
            )
            StatDivider()
            Stat(
                value = "Unlimited",
                label = "capacity",
                modifier = Modifier.weight(1f),
            )
        }

        if (storage == null && storageError != null) {
            Spacer(Modifier.height(Spacing.sm))
            Text(
                text = "Storage figures unavailable: $storageError",
                style = currentType.meta,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** Equal columns, so the figures share one baseline and one rhythm. */
@Composable
private fun RowScope.Stat(value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = value,
            style = currentType.item,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = label,
            style = currentType.meta,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            // One line, always. A label that wrapped made its column taller than
            // its neighbour and pushed that figure off the shared baseline.
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun StatDivider() {
    Box(
        Modifier
            .padding(horizontal = Spacing.sm)
            .width(1.dp)
            .height(28.dp)
            .background(MaterialTheme.colorScheme.outlineVariant)
    )
}

@Composable
private fun GroupHeader(title: String) {
    Text(
        text = title,
        style = currentType.meta,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(
            start = Spacing.md, top = Spacing.xs, bottom = Spacing.sm
        ),
    )
}

/**
 * One settings row. A null [onClick] means the row reports something rather than
 * offering an action, so it is not tappable at all; [muted] additionally dims a
 * row for a feature that is not built yet.
 *
 * Neither text colour here is `outline`: that is a border token, about 1.1:1
 * against the surface in light mode, so a subtitle painted with it was
 * effectively invisible.
 */
@Composable
private fun SettingRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
    muted: Boolean = false,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick == null || muted) Modifier else Modifier.clickable(onClick = onClick))
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(Spacing.md))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = currentType.body,
                color = if (muted) MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                style = currentType.meta,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        trailing?.invoke()
    }
}
