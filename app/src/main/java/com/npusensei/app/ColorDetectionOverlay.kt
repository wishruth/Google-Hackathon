package com.npusensei.app

import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors

data class ColorBlob(
    val centerX: Float,
    val centerY: Float,
    val width: Float,
    val height: Float,
    val color: DetectedColor,
)

enum class DetectedColor(val displayName: String, val overlayColor: Color) {
    RED("Red (LED/wire)", Color(0xAAFF4444)),
    GREEN("Green (LED/wire)", Color(0xAA44FF88)),
    BLUE("Blue (wire)", Color(0xAA4488FF)),
    YELLOW("Yellow (component)", Color(0xAAFFDD44)),
}

class ColorDetectionAnalyzer(
    private val onBlobsDetected: (List<ColorBlob>) -> Unit,
) : ImageAnalysis.Analyzer {

    private var frameCount = 0

    override fun analyze(image: ImageProxy) {
        frameCount++
        // Process every 3rd frame to reduce CPU load
        if (frameCount % 3 != 0) {
            image.close()
            return
        }

        val bitmap = imageToBitmap(image)
        if (bitmap != null) {
            val blobs = detectColorBlobs(bitmap)
            onBlobsDetected(blobs)
            bitmap.recycle()
        }
        image.close()
    }

    private fun imageToBitmap(image: ImageProxy): Bitmap? {
        if (image.format != ImageFormat.YUV_420_888) return null
        val yBuffer = image.planes[0].buffer
        val uBuffer = image.planes[1].buffer
        val vBuffer = image.planes[2].buffer
        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()
        val nv21 = ByteArray(ySize + uSize + vSize)
        yBuffer.get(nv21, 0, ySize)
        vBuffer.get(nv21, ySize, vSize)
        uBuffer.get(nv21, ySize + vSize, uSize)

        val yuvImage = YuvImage(nv21, ImageFormat.NV21, image.width, image.height, null)
        val out = ByteArrayOutputStream()
        yuvImage.compressToJpeg(Rect(0, 0, image.width, image.height), 50, out)
        val bytes = out.toByteArray()
        return android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }

    private fun detectColorBlobs(bitmap: Bitmap): List<ColorBlob> {
        val blobs = mutableListOf<ColorBlob>()
        val w = bitmap.width
        val h = bitmap.height

        // Sample grid (don't scan every pixel — too slow)
        val gridSize = 16
        val cellW = w / gridSize
        val cellH = h / gridSize

        val colorCounts = mutableMapOf<DetectedColor, MutableList<Pair<Int, Int>>>()

        for (gy in 0 until gridSize) {
            for (gx in 0 until gridSize) {
                val px = (gx * cellW + cellW / 2).coerceIn(0, w - 1)
                val py = (gy * cellH + cellH / 2).coerceIn(0, h - 1)
                val pixel = bitmap.getPixel(px, py)

                val r = android.graphics.Color.red(pixel)
                val g = android.graphics.Color.green(pixel)
                val b = android.graphics.Color.blue(pixel)

                val detected = classifyColor(r, g, b)
                if (detected != null) {
                    colorCounts.getOrPut(detected) { mutableListOf() }.add(Pair(gx, gy))
                }
            }
        }

        // Cluster nearby cells of same color into blobs
        for ((color, cells) in colorCounts) {
            if (cells.size < 3) continue // skip noise
            val minX = cells.minOf { it.first }
            val maxX = cells.maxOf { it.first }
            val minY = cells.minOf { it.second }
            val maxY = cells.maxOf { it.second }

            blobs.add(
                ColorBlob(
                    centerX = ((minX + maxX) / 2f) / gridSize,
                    centerY = ((minY + maxY) / 2f) / gridSize,
                    width = ((maxX - minX + 1).toFloat()) / gridSize,
                    height = ((maxY - minY + 1).toFloat()) / gridSize,
                    color = color,
                ),
            )
        }

        return blobs
    }

    private fun classifyColor(r: Int, g: Int, b: Int): DetectedColor? {
        val max = maxOf(r, g, b)
        val min = minOf(r, g, b)
        val saturation = if (max > 0) (max - min).toFloat() / max else 0f
        val brightness = max / 255f

        // Need decent saturation and brightness to avoid detecting shadows/whites
        if (saturation < 0.35f || brightness < 0.25f) return null
        if (brightness > 0.92f && saturation < 0.4f) return null // too white

        return when {
            r > g * 1.6 && r > b * 1.6 && r > 100 -> DetectedColor.RED
            g > r * 1.4 && g > b * 1.4 && g > 80 -> DetectedColor.GREEN
            b > r * 1.4 && b > g * 1.2 && b > 80 -> DetectedColor.BLUE
            r > 150 && g > 130 && b < 80 && r > b * 2 -> DetectedColor.YELLOW
            else -> null
        }
    }
}

@Composable
fun ColorHighlightOverlay(blobs: List<ColorBlob>, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.fillMaxSize()) {
        for (blob in blobs) {
            val x = blob.centerX * size.width
            val y = blob.centerY * size.height
            val w = blob.width * size.width
            val h = blob.height * size.height

            // Glow fill
            drawRect(
                color = blob.color.overlayColor.copy(alpha = 0.15f),
                topLeft = Offset(x - w / 2, y - h / 2),
                size = Size(w, h),
            )
            // Border
            drawRect(
                color = blob.color.overlayColor,
                topLeft = Offset(x - w / 2, y - h / 2),
                size = Size(w, h),
                style = Stroke(width = 2f),
            )
        }
    }
}

fun createColorAnalysis(onBlobsDetected: (List<ColorBlob>) -> Unit): ImageAnalysis {
    return ImageAnalysis.Builder()
        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
        .build()
        .also { analysis ->
            analysis.setAnalyzer(
                Executors.newSingleThreadExecutor(),
                ColorDetectionAnalyzer(onBlobsDetected),
            )
        }
}
