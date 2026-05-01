package com.npusensei.app.ml

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.RectF
import kotlin.math.pow
import kotlin.math.roundToInt

class ResistorColorReader {

    fun readOhms(source: Bitmap, box: RectF): Int? {
        val crop = safeCrop(source, box) ?: return null
        val scaled = Bitmap.createScaledBitmap(crop, 240, 60, true)
        val bands = extractBands(scaled) ?: return null
        if (crop !== scaled) crop.recycle()
        scaled.recycle()
        return decode(bands)
    }

    private fun safeCrop(src: Bitmap, box: RectF): Bitmap? {
        val l = box.left.toInt().coerceIn(0, src.width - 1)
        val t = box.top.toInt().coerceIn(0, src.height - 1)
        val r = box.right.toInt().coerceIn(l + 1, src.width)
        val b = box.bottom.toInt().coerceIn(t + 1, src.height)
        val w = r - l
        val h = b - t
        if (w < 16 || h < 8) return null
        return Bitmap.createBitmap(src, l, t, w, h)
    }

    private fun extractBands(crop: Bitmap): List<Band>? {
        val w = crop.width
        val h = crop.height
        val midRow = IntArray(w)
        val y0 = h / 3
        val y1 = 2 * h / 3
        for (x in 0 until w) {
            var rs = 0; var gs = 0; var bs = 0; var n = 0
            for (y in y0 until y1) {
                val px = crop.getPixel(x, y)
                rs += Color.red(px); gs += Color.green(px); bs += Color.blue(px); n++
            }
            midRow[x] = Color.rgb(rs / n, gs / n, bs / n)
        }

        val bodyR = median(midRow.map { Color.red(it) })
        val bodyG = median(midRow.map { Color.green(it) })
        val bodyB = median(midRow.map { Color.blue(it) })

        val isBand = BooleanArray(w)
        for (x in 0 until w) {
            val px = midRow[x]
            val d = colorDistance(Color.red(px), Color.green(px), Color.blue(px), bodyR, bodyG, bodyB)
            isBand[x] = d > BAND_BODY_DELTA
        }

        val runs = mutableListOf<IntRange>()
        var start = -1
        for (x in 0 until w) {
            if (isBand[x] && start < 0) start = x
            else if (!isBand[x] && start >= 0) {
                if (x - start >= MIN_BAND_PX) runs += start until x
                start = -1
            }
        }
        if (start >= 0 && w - start >= MIN_BAND_PX) runs += start until w
        if (runs.size < 3) return null

        val bands = runs.map { range ->
            val rs = mutableListOf<Int>(); val gs = mutableListOf<Int>(); val bs = mutableListOf<Int>()
            for (x in range) {
                val px = midRow[x]
                rs += Color.red(px); gs += Color.green(px); bs += Color.blue(px)
            }
            Band(median(rs), median(gs), median(bs))
        }
        return bands.take(5)
    }

    private val palette = listOf(
        ColorRef(0, "black", 16, 16, 16, digit = 0, multiplier = 1.0),
        ColorRef(1, "brown", 92, 56, 32, digit = 1, multiplier = 10.0),
        ColorRef(2, "red", 200, 40, 40, digit = 2, multiplier = 100.0),
        ColorRef(3, "orange", 230, 120, 40, digit = 3, multiplier = 1_000.0),
        ColorRef(4, "yellow", 240, 220, 60, digit = 4, multiplier = 10_000.0),
        ColorRef(5, "green", 60, 160, 80, digit = 5, multiplier = 100_000.0),
        ColorRef(6, "blue", 40, 80, 200, digit = 6, multiplier = 1_000_000.0),
        ColorRef(7, "violet", 140, 60, 200, digit = 7, multiplier = 10_000_000.0),
        ColorRef(8, "gray", 140, 140, 140, digit = 8, multiplier = 100_000_000.0),
        ColorRef(9, "white", 240, 240, 240, digit = 9, multiplier = 1_000_000_000.0),
        ColorRef(10, "gold", 220, 180, 80, digit = -1, multiplier = 0.1, isTolerance = true),
        ColorRef(11, "silver", 200, 200, 200, digit = -1, multiplier = 0.01, isTolerance = true),
    )

    private fun classify(b: Band): ColorRef? {
        var bestRef: ColorRef? = null
        var bestD = Float.MAX_VALUE
        for (c in palette) {
            val d = colorDistance(b.r, b.g, b.b, c.r, c.g, c.b).toFloat()
            if (d < bestD) { bestD = d; bestRef = c }
        }
        return if (bestD < CLASSIFY_MAX_DELTA) bestRef else null
    }

    private fun decode(bands: List<Band>): Int? {
        val classified = bands.mapNotNull(::classify)
        if (classified.size < 3) return null
        val isFive = classified.size >= 4 &&
            !classified[2].isTolerance &&
            classified[2].digit >= 0 &&
            classified[3].multiplier != 0.0
        val digits: List<Int>
        val multiplier: Double
        if (isFive) {
            digits = listOf(classified[0].digit, classified[1].digit, classified[2].digit).filter { it >= 0 }
            multiplier = classified[3].multiplier
        } else {
            digits = listOf(classified[0].digit, classified[1].digit).filter { it >= 0 }
            multiplier = classified[2].multiplier
        }
        if (digits.size < 2) return null
        val baseValue = digits.fold(0) { acc, d -> acc * 10 + d }
        val ohms = (baseValue.toDouble() * multiplier).roundToInt()
        return if (ohms in 1..1_000_000_000) ohms else null
    }

    private fun colorDistance(r1: Int, g1: Int, b1: Int, r2: Int, g2: Int, b2: Int): Double {
        val rm = (r1 + r2) / 2.0
        val dr = (r1 - r2).toDouble(); val dg = (g1 - g2).toDouble(); val db = (b1 - b2).toDouble()
        return ((2 + rm / 256) * dr.pow(2) + 4 * dg.pow(2) + (2 + (255 - rm) / 256) * db.pow(2)).pow(0.5)
    }

    private fun median(values: List<Int>): Int {
        if (values.isEmpty()) return 0
        val sorted = values.sorted()
        return sorted[sorted.size / 2]
    }

    private data class Band(val r: Int, val g: Int, val b: Int)
    private data class ColorRef(
        val id: Int, val name: String, val r: Int, val g: Int, val b: Int,
        val digit: Int, val multiplier: Double, val isTolerance: Boolean = false,
    )

    companion object {
        private const val MIN_BAND_PX = 4
        private const val BAND_BODY_DELTA = 35.0
        private const val CLASSIFY_MAX_DELTA = 90.0
    }
}
