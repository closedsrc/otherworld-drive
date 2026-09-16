package com.dfc.mobile.ui.screens

import com.dfc.mobile.ui.formatBytes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dfc.mobile.ui.DfcViewModel
import com.dfc.mobile.ui.ScreenTitle
import com.dfc.mobile.ui.components.PrimaryButton
import com.dfc.mobile.ui.theme.Spacing

/**
 * Settings groups by what the user came to change: where the data goes, how it
 * gets there, and what the app is doing. Anything the server cannot back is
 * labelled as such rather than offered as a switch that does nothing.
 */
@Composable
fun SettingsScreen(
    ui: DfcViewModel.Ui,
    wifiOnly: Boolean,
    onToggleWifiOnly: (Boolean) -> Unit,
    onOpenServerSetup: () -> Unit,
    onBackupNow: () -> Unit,
    onOpenUploads: () -> Unit,
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
                title = "Write token",
                subtitle = if (ui.configured) "Set. Tap to replace it."
                else "Not set. Tap to paste your token.",
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
            GroupHeader("Storage")
            when (val s = ui.storage) {
                null -> SettingRow(
                    icon = Icons.Outlined.PhotoLibrary,
                    title = "Drive totals unavailable",
                    subtitle = "The server did not answer the status request",
                    onClick = {},
                )
                else -> Column {
                    SettingRow(
                        icon = Icons.Outlined.PhotoLibrary,
                        title = formatBytes(s.totalBytes) + " stored",
                        subtitle = "${s.filesCount} files on device",
                        onClick = {},
                    )
                    SettingRow(
                        icon = Icons.Outlined.AllInclusive,
                        title = "Unlimited storage",
                        subtitle = "Private infrastructure",
                        onClick = {},
                    )
                }
            }
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
                subtitle = "Coming soon. A PIN or biometric gate needs its own screen " +
                    "and is not built yet.",
                onClick = {},
                muted = true,
            )
            SettingRow(
                icon = Icons.Outlined.Star,
                title = "Active sessions",
                subtitle = "Coming soon. The server does not report per-device sessions yet.",
                onClick = {},
                muted = true,
            )
        }

        item(key = "about") {
            Spacer(Modifier.height(Spacing.lg))
            GroupHeader("About")
            SettingRow(
                icon = Icons.Outlined.Description,
                title = "Token storage",
                subtitle = "The write token is kept in this app's private preferences. " +
                    "Anything with root access to this phone can read it.",
                onClick = {},
            )
            Box(Modifier.padding(horizontal = Spacing.md, vertical = Spacing.md)) {
                Text(
                    text = "Otherworld Drive 3.0  ·  Companion app for a self-hosted drive",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun GroupHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(
            start = Spacing.md, top = Spacing.xs, bottom = Spacing.sm
        ),
    )
}

/**
 * One settings row. [muted] marks rows that are informational or not yet built,
 * so a tap never implies a feature that does not exist.
 */
@Composable
private fun SettingRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    trailing: @Composable (() -> Unit)? = null,
    muted: Boolean = false,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !muted, onClick = onClick)
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (muted) MaterialTheme.colorScheme.outline
            else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(Spacing.md))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (muted) MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = if (muted) MaterialTheme.colorScheme.outline
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        trailing?.invoke()
    }
}
