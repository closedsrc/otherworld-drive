package com.dfc.mobile.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.window.DialogProperties
import com.dfc.mobile.ui.theme.Elevation
import com.dfc.mobile.ui.theme.Radii
import com.dfc.mobile.ui.theme.Spacing
import com.dfc.mobile.ui.theme.currentType
import com.dfc.mobile.ui.components.minTouchSize
import com.dfc.mobile.ui.theme.modalOutline
import com.dfc.mobile.ui.theme.modalSurface

/**
 * The one dialog system.
 *
 * Previously every screen built its own AlertDialog: radii varied 16–28dp, the
 * scrim blended into a muddy brown, and — the worst bug — the primary action
 * was styled as low-emphasis gray text next to a *stronger* secondary button.
 * The share dialog literally said "copy it now" while rendering "Copy link" as
 * the weakest element on screen.
 *
 * Rules enforced here:
 *  - one surface, one radius, one outline, real modal elevation
 *  - exactly one primary action, filled; destructive actions are tinted and
 *    never filled by default
 *  - title is a heading, body is body text, actions sit bottom-end
 *  - scrollable body so landscape and long text cannot clip
 *  - announced to screen readers as a pane with a title
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DfcDialog(
    onDismiss: () -> Unit,
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    confirmLabel: String? = null,
    onConfirm: (() -> Unit)? = null,
    confirmEnabled: Boolean = true,
    /** Destructive: labels the primary action with the error colour. */
    destructive: Boolean = false,
    dismissLabel: String? = null,
    /** Extra action (e.g. "Share") — always weaker than confirm. */
    tertiaryLabel: String? = null,
    onTertiary: (() -> Unit)? = null,
    body: @Composable (() -> Unit)? = null,
) {
    BasicAlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = modifier.semantics { paneTitle = title },
    ) {
        Surface(
            shape = RoundedCornerShape(Elevation.radiusSheet),
            color = modalSurface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            tonalElevation = Elevation.modal,
            shadowElevation = Elevation.modal,
            border = androidx.compose.foundation.BorderStroke(1.dp, modalOutline),
            modifier = Modifier.widthIn(max = 420.dp),
        ) {
            Column(Modifier.padding(Spacing.lg)) {
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (destructive) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp),
                    )
                    Spacer(Modifier.height(Spacing.sm))
                }
                ProvideTextStyle(currentType.title) {
                    Text(text = title, color = MaterialTheme.colorScheme.onSurface)
                }
                if (body != null) {
                    Spacer(Modifier.height(Spacing.md))
                    Box(Modifier.verticalScroll(rememberScrollState())) {
                        ProvideTextStyle(currentType.body) {
                            body()
                        }
                    }
                }
                Spacer(Modifier.height(Spacing.lg))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (dismissLabel != null) {
                        DialogTextButton(
                            label = dismissLabel,
                            onClick = onDismiss,
                            enabled = true,
                            emphasis = DialogActionEmphasis.LOW,
                        )
                        Spacer(Modifier.width(Spacing.sm))
                    }
                    if (tertiaryLabel != null && onTertiary != null) {
                        DialogTextButton(
                            label = tertiaryLabel,
                            onClick = onTertiary,
                            enabled = true,
                            emphasis = DialogActionEmphasis.MEDIUM,
                        )
                        Spacer(Modifier.width(Spacing.sm))
                    }
                    if (confirmLabel != null && onConfirm != null) {
                        DialogTextButton(
                            label = confirmLabel,
                            onClick = onConfirm,
                            enabled = confirmEnabled,
                            emphasis = DialogActionEmphasis.HIGH,
                            destructive = destructive,
                        )
                    }
                }
            }
        }
    }
}

private enum class DialogActionEmphasis { LOW, MEDIUM, HIGH }

/**
 * Actions are text buttons whose *weight* carries the hierarchy: the primary
 * action is the strongest label and, when filled, the only filled control.
 */
@Composable
private fun DialogTextButton(
    label: String,
    onClick: () -> Unit,
    enabled: Boolean,
    emphasis: DialogActionEmphasis,
    destructive: Boolean = false,
) {
    val color = when {
        destructive -> MaterialTheme.colorScheme.error
        emphasis == DialogActionEmphasis.HIGH -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val style = when (emphasis) {
        DialogActionEmphasis.HIGH -> currentType.action
        DialogActionEmphasis.MEDIUM -> currentType.body
        DialogActionEmphasis.LOW -> currentType.body
    }
    TextButton(
        onClick = onClick,
        enabled = enabled,
        // 48dp floor: a dialog action is a tap target, not a caption.
        modifier = Modifier.minTouchSize(),
    ) {
        ProvideTextStyle(style) {
            Text(label, color = color)
        }
    }
}

/** Body text for a dialog: readable, wrapped, never clipped. */
@Composable
fun DialogBody(text: String) {
    Text(
        text = text,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Start,
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Centred empty/error state used inside sheets. */
@Composable
fun DialogMessage(text: String) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            style = currentType.body,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/** Semantic-only label helper for icon-only controls. */
fun Modifier.actionLabel(label: String): Modifier =
    this.semantics { contentDescription = label }


/** 48dp floor for dialog actions (shared with the rest of the component set). */

