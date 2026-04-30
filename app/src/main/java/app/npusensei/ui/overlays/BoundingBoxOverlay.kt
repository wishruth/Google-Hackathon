package app.npusensei.ui.overlays

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import app.npusensei.core.models.BoundingBox
import kotlin.math.max

@Composable
fun BoundingBoxOverlay(
    boxes: List<BoundingBox>,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        boxes.forEach { box ->
            val isNormalized = box.x <= 1f && box.y <= 1f && box.width <= 1f && box.height <= 1f
            val sourceWidth = box.sourceWidth.takeIf { it > 0 }?.toFloat() ?: size.width
            val sourceHeight = box.sourceHeight.takeIf { it > 0 }?.toFloat() ?: size.height
            val scale = if (isNormalized) {
                1f
            } else {
                max(size.width / sourceWidth, size.height / sourceHeight)
            }
            val offsetX = if (isNormalized) 0f else (size.width - sourceWidth * scale) / 2f
            val offsetY = if (isNormalized) 0f else (size.height - sourceHeight * scale) / 2f
            val left = if (isNormalized) box.x * size.width else box.x * scale + offsetX
            val top = if (isNormalized) box.y * size.height else box.y * scale + offsetY
            val width = if (isNormalized) box.width * size.width else box.width * scale
            val height = if (isNormalized) box.height * size.height else box.height * scale
            drawRect(
                color = AccentGreen.copy(alpha = 0.16f),
                topLeft = Offset(left, top),
                size = Size(width, height),
            )
            drawRect(
                color = AccentGreen,
                topLeft = Offset(left, top),
                size = Size(width, height),
                style = Stroke(width = 4f),
            )
        }
    }
}

private val AccentGreen = Color(0xFF00FF88)
