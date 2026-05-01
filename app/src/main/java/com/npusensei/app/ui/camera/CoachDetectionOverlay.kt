package com.npusensei.app.ui.camera

import android.graphics.PointF
import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import com.npusensei.app.ml.Detection
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

@Composable
fun CoachDetectionOverlay(
    detections: List<Detection>,
    frameSize: Pair<Int, Int>,
    highlightBox: RectF?,
    highlightPx: PointF?,
    nextStepLabel: String?,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val (imgW, imgH) = frameSize
        if (imgW == 0 || imgH == 0) return@Canvas
        val viewW = size.width
        val viewH = size.height
        val scale = maxOf(viewW / imgW, viewH / imgH)
        val dx = (viewW - imgW * scale) / 2f
        val dy = (viewH - imgH * scale) / 2f

        fun mapX(x: Float) = x * scale + dx
        fun mapY(y: Float) = y * scale + dy

        for (d in detections) {
            val l = mapX(d.box.left)
            val t = mapY(d.box.top)
            val r = mapX(d.box.right)
            val b = mapY(d.box.bottom)
            val color = colorFor(d.label).copy(alpha = 0.85f)
            drawRect(
                color = color,
                topLeft = Offset(l, t),
                size = Size(r - l, b - t),
                style = Stroke(width = 4.dp.toPx()),
            )
            drawContext.canvas.nativeCanvas.let { canvas ->
                val paint = android.graphics.Paint().apply {
                    setColor(android.graphics.Color.WHITE)
                    textSize = 32f
                    isAntiAlias = true
                }
                val bg = android.graphics.Paint().apply {
                    setColor(android.graphics.Color.argb(180, 0, 0, 0))
                }
                val label = buildString {
                    append(d.label)
                    d.resistorOhms?.let { append("  ${formatOhms(it)}") }
                    append("  ").append((d.score * 100).toInt()).append('%')
                }
                val textWidth = paint.measureText(label)
                canvas.drawRect(l, t - 38f, l + textWidth + 16f, t, bg)
                canvas.drawText(label, l + 8f, t - 10f, paint)
            }
        }

        highlightBox?.let { box ->
            val l = mapX(box.left); val t = mapY(box.top)
            val r = mapX(box.right); val b = mapY(box.bottom)
            drawRect(
                color = Color(0xFFFFD400),
                topLeft = Offset(l - 4f, t - 4f),
                size = Size(r - l + 8f, b - t + 8f),
                style = Stroke(width = 6.dp.toPx()),
            )
            nextStepLabel?.let { label ->
                drawContext.canvas.nativeCanvas.let { canvas ->
                    val paint = android.graphics.Paint().apply {
                        setColor(android.graphics.Color.BLACK)
                        textSize = 40f
                        isFakeBoldText = true
                        isAntiAlias = true
                    }
                    val bgPaint = android.graphics.Paint().apply {
                        setColor(android.graphics.Color.parseColor("#FFD400"))
                    }
                    val w = paint.measureText(label) + 24f
                    canvas.drawRect(l, b + 8f, l + w, b + 60f, bgPaint)
                    canvas.drawText(label, l + 12f, b + 50f, paint)
                }
            }
        }

        highlightPx?.let { p ->
            val x = mapX(p.x)
            val y = mapY(p.y)
            val ringR = min(size.width, size.height) * 0.04f
            drawCircle(
                color = Color(0xFFFFD400),
                radius = ringR,
                center = Offset(x, y),
                style = Stroke(width = 6.dp.toPx()),
            )
            drawCircle(
                color = Color(0xFFFFD400).copy(alpha = 0.25f),
                radius = ringR * 1.6f,
                center = Offset(x, y),
            )
            val startX = size.width * 0.1f
            val startY = size.height * 0.1f
            val angle = atan2((y - startY).toDouble(), (x - startX).toDouble())
            val tipX = x - (ringR * 1.5f * cos(angle)).toFloat()
            val tipY = y - (ringR * 1.5f * sin(angle)).toFloat()
            drawLine(
                color = Color(0xFFFFD400),
                start = Offset(startX, startY),
                end = Offset(tipX, tipY),
                strokeWidth = 6.dp.toPx(),
            )
            val headSize = 28f
            val leftAngle = angle + Math.PI * 5 / 6
            val rightAngle = angle - Math.PI * 5 / 6
            val path = Path().apply {
                moveTo(tipX, tipY)
                lineTo(tipX + (headSize * cos(leftAngle)).toFloat(), tipY + (headSize * sin(leftAngle)).toFloat())
                lineTo(tipX + (headSize * cos(rightAngle)).toFloat(), tipY + (headSize * sin(rightAngle)).toFloat())
                close()
            }
            drawPath(path, color = Color(0xFFFFD400))
        }
    }
}

private fun colorFor(label: String): Color = when (label) {
    "breadboard" -> Color(0xFF7AC8FF)
    "ribbon_cable" -> Color(0xFF9F7AFF)
    "red_led" -> Color(0xFFFF4D4D)
    "gpio_breakout" -> Color(0xFF4DCEA0)
    "raspberry_pi" -> Color(0xFFFF85B5)
    "resistor" -> Color(0xFFFFB347)
    "blue_cable" -> Color(0xFF4D8AFF)
    "red_wire" -> Color(0xFFFF7A7A)
    "completed_circuit" -> Color(0xFF6BFF7A)
    else -> Color(0xFFB7B7BC)
}

private fun formatOhms(ohms: Int): String = when {
    ohms >= 1_000_000 -> "${ohms / 1_000_000}MΩ"
    ohms >= 1_000 -> "${ohms / 1_000}kΩ"
    else -> "${ohms}Ω"
}
