package com.ramzi.obdpro.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ramzi.obdpro.ui.theme.DarkBorder
import com.ramzi.obdpro.ui.theme.TextSecondary

/**
 * Circular gauge component for the OBD dashboard.
 *
 * Renders a 270° arc with a colored sweep indicating the current value
 * relative to the maximum. Includes smooth animation when values change.
 *
 * Usage:
 * ```
 * CircularGauge(
 *     value = 3500f,
 *     maxValue = 8000f,
 *     label = "RPM",
 *     unit = "rpm",
 *     color = NeonCyan,
 *     size = 160.dp
 * )
 * ```
 *
 * @param value Current sensor value
 * @param maxValue Maximum possible value (defines full sweep)
 * @param label Sensor name displayed above the value
 * @param unit Unit suffix displayed below the value
 * @param valueText Optional custom display text (overrides value formatting)
 * @param color Accent color for the gauge arc and value text
 * @param size Diameter of the gauge
 * @param strokeWidth Width of the arc stroke
 */
@Composable
fun CircularGauge(
    value: Float,
    maxValue: Float,
    label: String,
    unit: String,
    valueText: String? = null,
    color: Color,
    size: Dp = 160.dp,
    strokeWidth: Dp = 10.dp,
    modifier: Modifier = Modifier
) {
    // Animated sweep fraction (0.0 to 1.0)
    val sweepFraction = remember { Animatable(0f) }
    LaunchedEffect(value) {
        val target = if (maxValue > 0f) (value / maxValue).coerceIn(0f, 1f) else 0f
        sweepFraction.animateTo(
            targetValue = target,
            animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing)
        )
    }

    // Total arc span (270° out of 360°)
    val totalSweep = 270f
    // Starting angle (135° = 7 o'clock position)
    val startAngle = 135f

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        // ── Arc Drawing ─────────────────────────────────────────────
        Canvas(modifier = Modifier.fillMaxSize().padding(8.dp)) {
            val arcSize = Size(
                this.size.width - strokeWidth.toPx(),
                this.size.height - strokeWidth.toPx()
            )
            val topLeft = Offset(strokeWidth.toPx() / 2f, strokeWidth.toPx() / 2f)

            // Background arc (dim track)
            drawArc(
                color = DarkBorder,
                startAngle = startAngle,
                sweepAngle = totalSweep,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round)
            )

            // Foreground arc (value sweep)
            val sweep = totalSweep * sweepFraction.value
            if (sweep > 0f) {
                drawArc(
                    color = color,
                    startAngle = startAngle,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round)
                )
            }
        }

        // ── Center Text ─────────────────────────────────────────────
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Label
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(2.dp))

            // Value
            Text(
                text = valueText ?: formatValue(value),
                style = MaterialTheme.typography.displaySmall.copy(
                    fontSize = when {
                        size >= 180.dp -> 36.sp
                        size >= 140.dp -> 28.sp
                        else -> 22.sp
                    },
                    fontWeight = FontWeight.Bold
                ),
                color = color,
                textAlign = TextAlign.Center
            )

            // Unit
            Text(
                text = unit,
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * Formats a numeric value for gauge display.
 * - Integers: no decimal (e.g., "3500")
 * - Floats: one decimal place (e.g., "12.4")
 */
private fun formatValue(value: Float): String {
    return if (value == value.toLong().toFloat()) {
        value.toLong().toString()
    } else {
        String.format("%.1f", value)
    }
}
