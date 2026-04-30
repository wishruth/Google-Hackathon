package app.npusensei.ui.overlays

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import app.npusensei.core.models.BoundingBox

@Composable
fun BoundingBoxOverlay(
    boxes: List<BoundingBox>,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        boxes.forEach { box ->
            drawRect(
                color = Color.Green,
                topLeft = Offset(box.x, box.y),
                size = androidx.compose.ui.geometry.Size(box.width, box.height),
                style = Stroke(width = 4f),
            )
        }
    }
}
