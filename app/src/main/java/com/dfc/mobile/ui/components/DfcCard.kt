package com.dfc.mobile.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dfc.mobile.ui.theme.Elevation
import com.dfc.mobile.ui.theme.Radii
import com.dfc.mobile.ui.theme.Spacing
import com.dfc.mobile.ui.theme.currentType

/**
 * One card, one radius, one elevation.
 *
 * Cards used to be declared per screen with differing radii and tinted
 * containers (the selection bar alone used a maroon that belonged to no
 * palette). Every elevated surface in the app goes through here.
 */
@Composable
fun DfcCard(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(Radii.card),
        color = containerColor,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = Elevation.card,
        content = content,
    )
}

/**
 * The primary action. Filled, so it is never mistaken for a secondary one —
 * the share dialog's "Copy link" was gray text next to a stronger "Share",
 * which is how primary actions end up looking disabled.
 */
@Composable
fun DfcPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightInMin(48.dp),
        shape = RoundedCornerShape(Radii.control),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(8.dp))
        }
        ProvideTextStyle(currentType.action) { Text(text) }
    }
}

/** Secondary action: outlined, never as prominent as [DfcPrimaryButton]. */
@Composable
fun DfcSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    androidx.compose.material3.OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightInMin(48.dp),
        shape = RoundedCornerShape(Radii.control),
    ) {
        ProvideTextStyle(currentType.action) { Text(text) }
    }
}

/** A control that must clear the 48dp touch-target floor. */
fun Modifier.minTouchSize(): Modifier = this.then(
    Modifier.layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        val w = maxOf(placeable.width, 48.dp.roundToPx())
        val h = maxOf(placeable.height, 48.dp.roundToPx())
        layout(w, h) {
            placeable.place((w - placeable.width) / 2, (h - placeable.height) / 2)
        }
    },
)

fun Modifier.heightInMin(min: Dp): Modifier = this.then(
    Modifier.layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        val h = maxOf(placeable.height, min.roundToPx())
        layout(placeable.width, h) { placeable.place(0, (h - placeable.height) / 2) }
    },
)

/**
 * Empty and error states. Not one template instanced everywhere: the icon,
 * title and body are required so a "queue empty" state cannot end up with a
 * cloud-with-slash glyph that reads as "sync error".
 */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    action: @Composable (() -> Unit)? = null,
) {
    Column(
        modifier = modifier.padding(
            horizontal = Spacing.lg,
            vertical = if (body.isEmpty()) Spacing.md else Spacing.xl,
        ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .background(
                    MaterialTheme.colorScheme.surfaceContainerHigh,
                    RoundedCornerShape(Radii.card),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(26.dp),
            )
        }
        Spacer(Modifier.height(Spacing.md))
        Text(
            text = title,
            style = currentType.item,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        if (body.isNotEmpty()) {
            Spacer(Modifier.height(Spacing.xs))
            Text(
                text = body,
                style = currentType.body,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        if (action != null) {
            Spacer(Modifier.height(Spacing.md))
            action()
        }
    }
}

/** A row of metadata: label on the left, value on the right. */
@Composable
fun MetaRow(label: String, value: String) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = currentType.body,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = currentType.body,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = Spacing.md),
            textAlign = TextAlign.End,
        )
    }
}
