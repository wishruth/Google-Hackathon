package app.npusensei.vision

import android.graphics.RectF
import app.npusensei.core.models.BoundingBox

data class Detection(
    val box: RectF,
    val label: String,
    val confidence: Float,
    val sourceWidth: Int,
    val sourceHeight: Int,
) {
    fun toBoundingBox(): BoundingBox = BoundingBox(
        x = box.left,
        y = box.top,
        width = box.width(),
        height = box.height(),
        label = label,
        confidence = confidence,
        sourceWidth = sourceWidth,
        sourceHeight = sourceHeight,
    )
}

data class FrameMetadata(
    val sourceWidth: Int,
    val sourceHeight: Int,
    val inputSize: Int,
    val scale: Float,
    val padX: Float,
    val padY: Float,
)

data class PreprocessedFrame(
    val input: ByteArray,
    val metadata: FrameMetadata,
)
