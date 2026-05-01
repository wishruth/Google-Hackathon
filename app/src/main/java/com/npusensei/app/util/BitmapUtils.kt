package com.npusensei.app.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import android.media.Image
import androidx.camera.core.ImageProxy
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

object BitmapUtils {

    fun ImageProxy.toBitmapUpright(): Bitmap {
        val img = image ?: error("ImageProxy has no underlying Image")
        val argb = when (format) {
            ImageFormat.YUV_420_888 -> yuv420ToBitmap(img)
            else -> error("Unsupported ImageProxy format: $format")
        }
        val rot = imageInfo.rotationDegrees
        return if (rot == 0) argb else argb.rotated(rot.toFloat())
    }

    private fun yuv420ToBitmap(image: Image): Bitmap {
        val nv21 = yuv420ToNv21(image)
        val yuv = YuvImage(nv21, ImageFormat.NV21, image.width, image.height, null)
        val out = ByteArrayOutputStream()
        yuv.compressToJpeg(Rect(0, 0, image.width, image.height), 90, out)
        val bytes = out.toByteArray()
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }

    private fun yuv420ToNv21(image: Image): ByteArray {
        val crop = image.cropRect
        val width = crop.width()
        val height = crop.height()
        val nv21 = ByteArray(width * height * 3 / 2)
        val planes = image.planes

        val yPlane = planes[0]
        val yBuf: ByteBuffer = yPlane.buffer
        val yRowStride = yPlane.rowStride
        val yPixStride = yPlane.pixelStride
        var pos = 0
        for (row in 0 until height) {
            val rowStart = row * yRowStride
            if (yPixStride == 1) {
                yBuf.position(rowStart)
                yBuf.get(nv21, pos, width)
                pos += width
            } else {
                for (col in 0 until width) {
                    nv21[pos++] = yBuf.get(rowStart + col * yPixStride)
                }
            }
        }

        val uPlane = planes[1]
        val vPlane = planes[2]
        val uBuf = uPlane.buffer
        val vBuf = vPlane.buffer
        val uvRowStride = uPlane.rowStride
        val uvPixStride = uPlane.pixelStride
        for (row in 0 until height / 2) {
            val rowStart = row * uvRowStride
            for (col in 0 until width / 2) {
                val uvIndex = rowStart + col * uvPixStride
                nv21[pos++] = vBuf.get(uvIndex)
                nv21[pos++] = uBuf.get(uvIndex)
            }
        }
        return nv21
    }

    fun Bitmap.rotated(degrees: Float): Bitmap {
        if (degrees == 0f) return this
        val m = Matrix().apply { postRotate(degrees) }
        val out = Bitmap.createBitmap(this, 0, 0, width, height, m, true)
        if (out !== this) recycle()
        return out
    }
}
