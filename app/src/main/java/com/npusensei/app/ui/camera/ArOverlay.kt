package com.npusensei.app.ui.camera

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import com.npusensei.app.ml.Detection
import kotlinx.coroutines.delay

private const val LERP_FACTOR = 0.14f
private const val ALPHA_IN_FACTOR = 0.18f
private const val ALPHA_OUT_FACTOR = 0.08f
private const val FADE_DELAY_MS = 800L
private const val TICK_MS = 16L

private val ArAccent = Color(0xFF00E676)
private val ArLine = Color(0xFF00C853)
private val ArFill = Color(0xFF00E676).copy(alpha = 0.08f)

private class SmoothedBox(
    val label: String,
) {
    var left: Float = 0f
    var top: Float = 0f
    var right: Float = 0f
    var bottom: Float = 0f
    var targetLeft: Float = 0f
    var targetTop: Float = 0f
    var targetRight: Float = 0f
    var targetBottom: Float = 0f
    var alpha: Float = 0f
    var lastSeenMs: Long = 0L
    var initialized: Boolean = false
}

private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

@Composable
fun ArOverlay(
    detections: List<Detection>,
    requiredLabels: List<String>,
    frameSize: Pair<Int, Int>,
    modifier: Modifier = Modifier,
) {
    val trackers = remember { mutableMapOf<String, SmoothedBox>() }
    var tick by remember { mutableLongStateOf(0L) }

    // Update targets whenever detections change
    LaunchedEffect(detections, requiredLabels) {
        val now = System.currentTimeMillis()
        for (label in requiredLabels) {
            val tracker = trackers.getOrPut(label) { SmoothedBox(label) }
            val det = detections
                .filter { it.label == label }
                .maxByOrNull { it.score }

            if (det != null) {
                tracker.targetLeft = det.box.left
                tracker.targetTop = det.box.top
                tracker.targetRight = det.box.right
                tracker.targetBottom = det.box.bottom
                tracker.lastSeenMs = now

                if (!tracker.initialized) {
                    tracker.left = det.box.left
                    tracker.top = det.box.top
                    tracker.right = det.box.right
                    tracker.bottom = det.box.bottom
                    tracker.initialized = true
                }
            }
        }
        val stale = trackers.keys - requiredLabels.toSet()
        stale.forEach { trackers.remove(it) }
    }

    // Animation tick loop — drives smooth lerp + alpha transitions
    LaunchedEffect(Unit) {
        while (true) {
            val now = System.currentTimeMillis()
            for ((_, tracker) in trackers) {
                if (!tracker.initialized) continue

                val seen = now - tracker.lastSeenMs < FADE_DELAY_MS
                if (seen) {
                    tracker.alpha = lerp(tracker.alpha, 1f, ALPHA_IN_FACTOR)
                } else {
                    tracker.alpha = lerp(tracker.alpha, 0f, ALPHA_OUT_FACTOR)
                }

                tracker.left = lerp(tracker.left, tracker.targetLeft, LERP_FACTOR)
                tracker.top = lerp(tracker.top, tracker.targetTop, LERP_FACTOR)
                tracker.right = lerp(tracker.right, tracker.targetRight, LERP_FACTOR)
                tracker.bottom = lerp(tracker.bottom, tracker.targetBottom, LERP_FACTOR)
            }
            tick = now // triggers recomposition of Canvas
            delay(TICK_MS)
        }
    }

    Canvas(modifier = modifier) {
        // read tick so Canvas redraws each frame
        tick.let { /* force read */ }

        val (imgW, imgH) = frameSize
        if (imgW == 0 || imgH == 0) return@Canvas
        val viewW = size.width
        val viewH = size.height
        val scale = maxOf(viewW / imgW, viewH / imgH)
        val dx = (viewW - imgW * scale) / 2f
        val dy = (viewH - imgH * scale) / 2f

        fun mapX(x: Float) = x * scale + dx
        fun mapY(y: Float) = y * scale + dy

        val visible = requiredLabels
            .mapNotNull { trackers[it] }
            .filter { it.initialized && it.alpha > 0.03f }

        for (box in visible) {
            val l = mapX(box.left)
            val t = mapY(box.top)
            val r = mapX(box.right)
            val b = mapY(box.bottom)
            val a = box.alpha

            drawRoundRect(
                color = ArFill.copy(alpha = ArFill.alpha * a),
                topLeft = Offset(l, t),
                size = Size(r - l, b - t),
                cornerRadius = CornerRadius(12.dp.toPx()),
            )
            drawRoundRect(
                color = ArAccent.copy(alpha = 0.7f * a),
                topLeft = Offset(l, t),
                size = Size(r - l, b - t),
                cornerRadius = CornerRadius(12.dp.toPx()),
                style = Stroke(width = 2.dp.toPx()),
            )

            drawContext.canvas.nativeCanvas.let { canvas ->
                val paint = android.graphics.Paint().apply {
                    color = android.graphics.Color.argb(
                        (220 * a).toInt().coerceIn(0, 255), 0, 230, 118
                    )
                    textSize = 28f
                    isAntiAlias = true
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                }
                val bg = android.graphics.Paint().apply {
                    color = android.graphics.Color.argb(
                        (140 * a).toInt().coerceIn(0, 255), 0, 0, 0
                    )
                }
                val displayLabel = box.label.replace('_', ' ')
                val tw = paint.measureText(displayLabel)
                val px = ((l + r) / 2f - tw / 2f)
                val py = t - 10f
                canvas.drawRoundRect(
                    px - 6f, py - 26f, px + tw + 6f, py + 4f,
                    8f, 8f, bg,
                )
                canvas.drawText(displayLabel, px, py, paint)
            }
        }

        if (visible.size >= 2) {
            val centers = visible.map { box ->
                Offset(
                    (mapX(box.left) + mapX(box.right)) / 2f,
                    (mapY(box.top) + mapY(box.bottom)) / 2f,
                )
            }
            val minAlpha = visible.minOf { it.alpha }

            val dashEffect = PathEffect.dashPathEffect(
                floatArrayOf(14.dp.toPx(), 8.dp.toPx()), 0f
            )

            for (i in 0 until centers.size - 1) {
                val from = centers[i]
                val to = centers[i + 1]
                val midX = (from.x + to.x) / 2f
                val midY = (from.y + to.y) / 2f
                val controlOffset = (to.x - from.x) * 0.15f

                val path = Path().apply {
                    moveTo(from.x, from.y)
                    quadraticTo(
                        midX + controlOffset, midY - controlOffset,
                        to.x, to.y,
                    )
                }

                drawPath(
                    path = path,
                    color = ArLine.copy(alpha = 0.2f * minAlpha),
                    style = Stroke(
                        width = 6.dp.toPx(),
                        cap = StrokeCap.Round,
                    ),
                )
                drawPath(
                    path = path,
                    color = ArLine.copy(alpha = 0.55f * minAlpha),
                    style = Stroke(
                        width = 2.dp.toPx(),
                        cap = StrokeCap.Round,
                        pathEffect = dashEffect,
                    ),
                )

                drawCircle(
                    color = ArAccent.copy(alpha = 0.6f * visible[i].alpha),
                    radius = 5.dp.toPx(),
                    center = from,
                )
                drawCircle(
                    color = ArAccent.copy(alpha = 0.6f * visible[i + 1].alpha),
                    radius = 5.dp.toPx(),
                    center = to,
                )
            }
        }
    }
}
