package com.dfc.mobile.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Storage total with an optional progress arc. The arc carries live transfer
 * progress and sits as an empty track the rest of the time, including wherever no
 * transfer is in view: the drive has no quota, so there is no capacity ratio
 * worth drawing. The centre figure is the stored total, which is a real sum the
 * server reports.
 */
@Composable
fun StorageRing(
    progress: Float?,
    centerValue: String,
    centerLabel: String,
    modifier: Modifier = Modifier,
    diameter: androidx.compose.ui.unit.Dp = 168.dp,
) {
    val sweep by animateFloatAsState(
        targetValue = progress?.coerceIn(0f, 1f) ?: 0f,
        // Shorter while a file is moving: the tracker emits per chunk, so a long
        // tween would lag behind and read as a stall.
        animationSpec = tween(durationMillis = if (progress == null) 400 else 220),
        label = "ring",
    )
    val track = MaterialTheme.colorScheme.outlineVariant
    val accent = MaterialTheme.colorScheme.primary

    Box(modifier = modifier.size(diameter), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(diameter)) {
            val stroke = size.minDimension * 0.075f
            // Inset by half the stroke: an arc is drawn on its bounding box, so
            // without this the outer half of the line falls outside the canvas
            // and gets clipped, reading as a thinner, lopsided ring.
            val inset = stroke / 2f
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(
                color = track,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            if (sweep > 0.001f) {
                drawArc(
                    color = accent,
                    startAngle = -90f,
                    sweepAngle = 360f * sweep,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = centerValue,
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            Text(
                text = centerLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}
