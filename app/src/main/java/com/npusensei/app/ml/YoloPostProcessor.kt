package com.npusensei.app.ml

import android.graphics.RectF
import kotlin.math.max
import kotlin.math.min

class YoloPostProcessor(
    private val classNames: List<String>,
    private val inputSize: Int = 640,
    private val confThreshold: Float = 0.35f,
    private val iouThreshold: Float = 0.45f,
    private val maxDetections: Int = 100,
) {

    var lastTopScore: Float = 0f
        private set
    var lastTopLabel: String = ""
        private set

    fun process(
        rawOutput: FloatArray,
        srcWidth: Int,
        srcHeight: Int,
        padX: Float,
        padY: Float,
        scale: Float,
    ): List<Detection> {
        val nc = classNames.size
        val rows = 4 + nc
        val numBoxes = rawOutput.size / rows
        if (rawOutput.size % rows != 0) return emptyList()

        val candidates = ArrayList<Detection>(256)
        var topScore = 0f
        var topCls = -1

        fun at(r: Int, b: Int) = rawOutput[r * numBoxes + b]

        for (b in 0 until numBoxes) {
            var bestCls = -1
            var bestScore = 0f
            for (c in 0 until nc) {
                val s = at(4 + c, b)
                if (s > bestScore) {
                    bestScore = s
                    bestCls = c
                }
            }
            if (bestScore > topScore) {
                topScore = bestScore
                topCls = bestCls
            }
            if (bestScore < confThreshold || bestCls < 0) continue

            val cx = at(0, b) * inputSize
            val cy = at(1, b) * inputSize
            val w = at(2, b) * inputSize
            val h = at(3, b) * inputSize

            val x1m = cx - w / 2f
            val y1m = cy - h / 2f
            val x2m = cx + w / 2f
            val y2m = cy + h / 2f

            val x1 = ((x1m - padX) / scale).coerceIn(0f, srcWidth.toFloat())
            val y1 = ((y1m - padY) / scale).coerceIn(0f, srcHeight.toFloat())
            val x2 = ((x2m - padX) / scale).coerceIn(0f, srcWidth.toFloat())
            val y2 = ((y2m - padY) / scale).coerceIn(0f, srcHeight.toFloat())
            if (x2 <= x1 || y2 <= y1) continue

            candidates += Detection(
                classId = bestCls,
                label = classNames[bestCls],
                score = bestScore,
                box = RectF(x1, y1, x2, y2),
            )
        }

        lastTopScore = topScore
        lastTopLabel = if (topCls in classNames.indices) classNames[topCls] else "?"
        return nms(candidates).take(maxDetections)
    }

    private fun nms(input: List<Detection>): List<Detection> {
        if (input.isEmpty()) return emptyList()
        val byClass = input.groupBy { it.classId }
        val out = ArrayList<Detection>(input.size)
        for ((_, group) in byClass) {
            val sorted = group.sortedByDescending { it.score }.toMutableList()
            while (sorted.isNotEmpty()) {
                val best = sorted.removeAt(0)
                out += best
                val it = sorted.iterator()
                while (it.hasNext()) {
                    if (iou(best.box, it.next().box) > iouThreshold) it.remove()
                }
            }
        }
        return out.sortedByDescending { it.score }
    }

    private fun iou(a: RectF, b: RectF): Float {
        val interLeft = max(a.left, b.left)
        val interTop = max(a.top, b.top)
        val interRight = min(a.right, b.right)
        val interBottom = min(a.bottom, b.bottom)
        val interW = max(0f, interRight - interLeft)
        val interH = max(0f, interBottom - interTop)
        val inter = interW * interH
        val areaA = (a.right - a.left) * (a.bottom - a.top)
        val areaB = (b.right - b.left) * (b.bottom - b.top)
        val union = areaA + areaB - inter
        return if (union <= 0f) 0f else inter / union
    }
}
