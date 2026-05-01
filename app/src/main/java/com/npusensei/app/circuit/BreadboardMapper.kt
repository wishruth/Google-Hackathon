package com.npusensei.app.circuit

import android.graphics.PointF
import android.graphics.RectF

class BreadboardMapper(
    private val geometry: BreadboardGeometry,
) {
    fun holeCenter(box: RectF, row: Int, col: String): PointF? {
        val colIndex = geometry.cols.indexOf(col)
        if (colIndex < 0 || row < 1 || row > geometry.rows) return null

        val w = box.width()
        val h = box.height()
        val usableTop = box.top + 0.06f * h
        val usableBottom = box.bottom - 0.06f * h
        val usableLeft = box.left + 0.04f * w
        val usableRight = box.right - 0.04f * w

        val rowFrac = (row - 0.5f) / geometry.rows
        val colFrac = (colIndex + 0.5f) / geometry.cols.size

        val y = usableTop + rowFrac * (usableBottom - usableTop)
        val x = usableLeft + colFrac * (usableRight - usableLeft)
        return PointF(x, y)
    }
}
