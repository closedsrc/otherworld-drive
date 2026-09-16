package com.dfc.mobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.LinkOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dfc.mobile.DfcApi
import com.dfc.mobile.ui.BarIcon
import com.dfc.mobile.ui.DfcViewModel
import com.dfc.mobile.ui.ScreenTitle
import com.dfc.mobile.ui.components.EmptyState
import com.dfc.mobile.ui.components.SectionHeader
import com.dfc.mobile.ui.theme.Radii
import com.dfc.mobile.ui.theme.Spacing

/**
 * Public links, which is the sharing the server actually implements: one link
 * per file, with an expiry and a download count. There are no per-person
 * permissions on this backend, and the screen says so rather than implying a
 * collaborator model that does not exist.
 */
@Composable
fun LinksScreen(
    ui: DfcViewModel.Ui,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onRevoke: (DfcApi.Share) -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(Unit) { onRefresh() }

    LazyColumn(
        // Full-screen overlay: pads its own inset, same as Setup and Preview.
        modifier = modifier.statusBarsPadding(),
        contentPadding = PaddingValues(bottom = Spacing.navClearance),
    ) {
        item(key = "bar") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BarIcon(
                    icon = Icons.Outlined.LinkOff,
                    contentDescription = "Back",
                    onClick = onBack,
                )
                ScreenTitle(
                    title = "Public links",
                    subtitle = "One link per file, with an expiry date",
                    modifier = Modifier.weight(1f),
                )
            }
        }

        when {
            !ui.sharesLoaded -> item(key = "loading") {
                Box(
                    Modifier.fillMaxWidth().padding(Spacing.xl),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator(color = MaterialTheme.colorScheme.primary) }
            }

            ui.sharesError != null -> item(key = "error") {
                EmptyState(
                    icon = Icons.Outlined.LinkOff,
                    title = "Links could not be loaded",
                    body = ui.sharesError!!,
                    action = { TextButton(onClick = onRefresh) { Text("Try again") } },
                )
            }

            ui.shares.isEmpty() -> item(key = "empty") {
                EmptyState(
                    icon = Icons.Outlined.LinkOff,
                    title = "No links yet",
                    body = "Select a file on the Files tab and choose Link to create one. " +
                        "Links can be revoked here at any time.",
                )
            }

            else -> {
                item(key = "hdr") {
                    Box(Modifier.padding(horizontal = Spacing.md, vertical = Spacing.md)) {
                        SectionHeader(
                            title = "Active links",
                            caption = "${ui.shares.count { !it.expired }} active, " +
                                "${ui.shares.count { it.expired }} expired",
                        )
                    }
                }
                items(ui.shares, key = { it.id }) { share ->
                    ShareRow(share = share, onRevoke = { onRevoke(share) })
                }
            }
        }
    }
}

@Composable
private fun ShareRow(share: DfcApi.Share, onRevoke: () -> Unit) {
    val shape = RoundedCornerShape(Radii.card)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.md, vertical = Spacing.xs)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = share.fileName.ifBlank { "Untitled file" },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = buildString {
                    append(if (share.expired) "Expired " else "Expires ")
                    append(expiryText(share.expiresAt))
                    append("  ·  ")
                    append("${share.downloads} downloads")
                },
                style = MaterialTheme.typography.labelSmall,
                color = if (share.expired) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(Spacing.sm))
        TextButton(onClick = onRevoke) {
            Icon(
                imageVector = Icons.Outlined.Delete,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(Spacing.xs))
            Text("Revoke", color = MaterialTheme.colorScheme.error)
        }
    }
}

/** Absolute expiry, because "in 3 days" is not checkable after a week. */
private fun expiryText(epochSeconds: Long): String {
    if (epochSeconds <= 0) return "no expiry set"
    val fmt = java.text.SimpleDateFormat("d MMM yyyy, HH:mm", java.util.Locale.getDefault())
    return fmt.format(java.util.Date(epochSeconds * 1000))
}
