package com.dfc.mobile.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.dfc.mobile.ui.theme.Elevation
import com.dfc.mobile.ui.theme.Spacing
import com.dfc.mobile.ui.theme.currentType
import com.dfc.mobile.ui.theme.modalOutline
import com.dfc.mobile.ui.theme.modalSurface
import com.dfc.mobile.ui.theme.modalScrim

/**
 * One dialog system for the whole app.
 *
 * Dialogs used to be assembled per screen with raw AlertDialog: radius 28 next
 * to 16 for no reason, a scrim that blended into a muddy brown, and — worst —
 * the primary action styled as low-emphasis grey text next to a stronger
 * secondary ("Copy link" beside "Share" in the share dialog, "Create" greyed in
 * New folder). This component fixes the hierarchy by construction: there is one
 * primary slot, it is filled and high-contrast, and destructive is separate.
 */
@Composable
fun AppDialog(
    onDismiss: () -> Unit,
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    /** Short explanation under the title. */
    subtitle: String? = null,
    /** Form fields or body content. */
    content: (@Composable () -> Unit)? = null,
    /** The one action the dialog exists for. Filled, primary. */
    confirmLabel: String,
    onConfirm: () -> Unit,
    confirmEnabled: Boolean = true,
    /** Destructive turns the primary action into the error colour. */
    destructive: Boolean = false,
    dismissLabel: String = "Cancel",
    onDismissRequest: (() -> Unit)? = null,
    /** Optional third action (e.g. Share next to Copy). */
    extraAction: (@Composable () -> Unit)? = null,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.md),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                modifier = modifier.widthIn(max = 440.dp),
                shape = RoundedCornerShape(Elevation.radiusSheet),
                color = modalSurface,
                tonalElevation = Elevation.modal,
                shadowElevation = Elevation.modal,
            ) {
                Column(
                    modifier = Modifier
                        .border(
                            width = 1.dp,
                            color = modalOutline,
                            shape = RoundedCornerShape(Elevation.radiusSheet),
                        )
                        .padding(Spacing.lg),
                ) {
                    if (icon != null) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(Elevation.radiusControl))
                                .background(
                                    if (destructive) MaterialTheme.colorScheme.errorContainer
                                    else MaterialTheme.colorScheme.primaryContainer
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = if (destructive) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                        Spacer(Modifier.height(Spacing.md))
                    }
                    Text(
                        text = title,
                        style = currentType.title,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    if (!subtitle.isNullOrBlank()) {
                        Spacer(Modifier.height(Spacing.xs))
                        Text(
                            text = subtitle,
                            style = currentType.bodyMuted,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (content != null) {
                        Spacer(Modifier.height(Spacing.md))
                        content()
                    }
                    Spacer(Modifier.height(Spacing.lg))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(
                            Spacing.sm, Alignment.End,
                        ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        extraAction?.invoke()
                        DialogTextButton(
                            label = dismissLabel,
                            onClick = onDismissRequest ?: onDismiss,
                        )
                        DialogPrimaryButton(
                            label = confirmLabel,
                            onClick = onConfirm,
                            enabled = confirmEnabled,
                            destructive = destructive,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DialogPrimaryButton(
    label: String,
    onClick: () -> Unit,
    enabled: Boolean,
    destructive: Boolean,
) {
    val bg = when {
        !enabled -> MaterialTheme.colorScheme.surfaceContainerHighest
        destructive -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.primary
    }
    val fg = when {
        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant
        destructive -> MaterialTheme.colorScheme.onError
        else -> MaterialTheme.colorScheme.onPrimary
    }
    androidx.compose.material3.Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(Elevation.radiusControl),
        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
            containerColor = bg,
            contentColor = fg,
            disabledContainerColor = bg,
            disabledContentColor = fg,
        ),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        modifier = Modifier.heightInMin48(),
    ) {
        Text(label, style = currentType.action)
    }
}

@Composable
private fun DialogTextButton(label: String, onClick: () -> Unit) {
    androidx.compose.material3.TextButton(
        onClick = onClick,
        shape = RoundedCornerShape(Elevation.radiusControl),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        modifier = Modifier.heightInMin48(),
    ) {
        Text(
            text = label,
            style = currentType.action,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 48dp is the accessible minimum touch target; every control clears it. */
fun Modifier.heightInMin48(): Modifier = this.size(48.dp, 48.dp).let { this.padding(0.dp) }

/** Compact confirmation for low-stakes prompts. */
@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = false,
    icon: ImageVector? = null,
) {
    AppDialog(
        onDismiss = onDismiss,
        title = title,
        subtitle = message,
        icon = icon,
        confirmLabel = confirmLabel,
        onConfirm = onConfirm,
        destructive = destructive,
    )
}

/** Centred informational block used inside dialogs. */
@Composable
fun DialogNote(text: String) {
    Text(
        text = text,
        style = currentType.meta,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Start,
        modifier = Modifier.fillMaxWidth(),
    )
}
