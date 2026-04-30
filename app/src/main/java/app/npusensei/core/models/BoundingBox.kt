package app.npusensei.core.models

data class BoundingBox(
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val label: String,
    val confidence: Float,
)
