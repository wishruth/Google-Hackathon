package com.npusensei.app.ml

import android.content.Context
import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.Log
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import kotlin.math.min
import kotlin.math.roundToInt

class ObjectDetector(
    context: Context,
    private val modelAssetPath: String = "models/circuit_detector.tflite",
    val inputSize: Int = 640,
    classNames: List<String> = CircuitClasses.ORDERED,
    confThreshold: Float = 0.20f,
) : AutoCloseable {

    private val interpreter: Interpreter
    val postProcessor = YoloPostProcessor(
        classNames = classNames,
        inputSize = inputSize,
        confThreshold = confThreshold,
    )
    private val outputBuffer: Array<Array<FloatArray>>
    private val resistorReader = ResistorColorReader()

    init {
        val model: MappedByteBuffer = try {
            loadMappedFile(context.assets, modelAssetPath)
        } catch (e: IOException) {
            throw IllegalStateException(
                "Detector model missing at assets/$modelAssetPath. " +
                    "Run training/export_tflite.py first.",
                e,
            )
        }

        val options = Interpreter.Options().apply {
            setNumThreads(4)
        }
        interpreter = Interpreter(model, options)

        val outShape = interpreter.getOutputTensor(0).shape()
        require(outShape.size == 3 && outShape[0] == 1) {
            "Unexpected YOLO output shape ${outShape.toList()}"
        }
        outputBuffer = Array(1) { Array(outShape[1]) { FloatArray(outShape[2]) } }
        Log.i(TAG, "Detector ready. Output shape = ${outShape.toList()}")
    }

    fun detect(bitmap: Bitmap): List<Detection> {
        val (input, padX, padY, scale) = letterbox(bitmap, inputSize)
        val inputBuf = bitmapToFloatBuffer(input)

        interpreter.run(inputBuf, outputBuffer)

        val rows = outputBuffer[0].size
        val cols = outputBuffer[0][0].size
        val flat = FloatArray(rows * cols)
        for (r in 0 until rows) {
            System.arraycopy(outputBuffer[0][r], 0, flat, r * cols, cols)
        }

        val detections = postProcessor.process(
            rawOutput = flat,
            srcWidth = bitmap.width,
            srcHeight = bitmap.height,
            padX = padX,
            padY = padY,
            scale = scale,
        )

        return detections.map { d ->
            if (d.label == CircuitClasses.RESISTOR) {
                val ohms = runCatching { resistorReader.readOhms(bitmap, d.box) }
                    .getOrNull()
                d.copy(resistorOhms = ohms)
            } else d
        }
    }

    override fun close() {
        interpreter.close()
    }

    private data class LetterboxResult(
        val bitmap: Bitmap,
        val padX: Float,
        val padY: Float,
        val scale: Float,
    )

    private fun letterbox(src: Bitmap, target: Int): LetterboxResult {
        val scale = min(target / src.width.toFloat(), target / src.height.toFloat())
        val newW = (src.width * scale).roundToInt()
        val newH = (src.height * scale).roundToInt()
        val padX = (target - newW) / 2f
        val padY = (target - newH) / 2f

        val out = Bitmap.createBitmap(target, target, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(Color.rgb(114, 114, 114))
        val resized = Bitmap.createScaledBitmap(src, newW, newH, true)
        canvas.drawBitmap(resized, padX, padY, Paint(Paint.FILTER_BITMAP_FLAG))
        if (resized !== src) resized.recycle()
        return LetterboxResult(out, padX, padY, scale)
    }

    private fun bitmapToFloatBuffer(bm: Bitmap): ByteBuffer {
        val bytes = inputSize * inputSize * 3 * 4
        val buf = ByteBuffer.allocateDirect(bytes).order(ByteOrder.nativeOrder())
        val pixels = IntArray(inputSize * inputSize)
        bm.getPixels(pixels, 0, inputSize, 0, 0, inputSize, inputSize)
        for (p in pixels) {
            buf.putFloat(((p shr 16) and 0xFF) / 255f)
            buf.putFloat(((p shr 8) and 0xFF) / 255f)
            buf.putFloat((p and 0xFF) / 255f)
        }
        buf.rewind()
        return buf
    }

    companion object {
        private const val TAG = "ObjectDetector"

        private fun loadMappedFile(assets: AssetManager, path: String): MappedByteBuffer {
            val fd = assets.openFd(path)
            return FileInputStream(fd.fileDescriptor).channel.map(
                FileChannel.MapMode.READ_ONLY,
                fd.startOffset,
                fd.declaredLength,
            )
        }
    }
}
