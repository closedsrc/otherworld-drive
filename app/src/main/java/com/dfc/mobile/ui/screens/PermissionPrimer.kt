package com.dfc.mobile.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.dfc.mobile.ui.components.PrimaryButton
import com.dfc.mobile.ui.theme.Spacing
import com.dfc.mobile.ui.theme.currentType

/**
 * Shown BEFORE the OS photo-permission dialog. The system prompt is the single
 * worst conversion moment to explain a backup product; this is the app's one
 * chance to say what will be uploaded, where it goes, and what it costs the
 * user's privacy — in the app's own words, before the irreversible-feeling
 * system sheet appears.
 */
@Composable
fun PermissionPrimer(
    onContinue: () -> Unit,
    onSkip: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onSkip,
        title = { Text("Back up your photos") },
        text = {
            Column {
                PrimerRow(
                    icon = Icons.Outlined.CloudUpload,
                    text = "Photos and videos on this phone are uploaded to your drive " +
                        "automatically, about every 15 minutes while charging on Wi-Fi.",
                )
                PrimerRow(
                    icon = Icons.Outlined.Wifi,
                    text = "Only your drive receives them. Nothing is posted anywhere " +
                        "and no third party is involved in the transfer.",
                )
                PrimerRow(
                    icon = Icons.Outlined.Lock,
                    text = "You can allow only selected photos, and revoke access in " +
                        "system settings at any time. Backing up stops the moment you do.",
                )
                Spacer(Modifier.height(Spacing.sm))
                Text(
                    text = "Android will now ask for photo access.",
                    style = currentType.meta,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            Column(Modifier.fillMaxWidth()) {
                PrimaryButton(text = "Continue", onClick = onContinue)
                TextButton(onClick = onSkip, modifier = Modifier.align(Alignment.End)) {
                    Text("Not now")
                }
            }
        },
    )
}

@Composable
private fun PrimerRow(icon: ImageVector, text: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = Spacing.xs)) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(Spacing.xs))
        Text(
            text = text,
            style = currentType.bodyMuted,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
