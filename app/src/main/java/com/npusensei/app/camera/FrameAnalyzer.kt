package com.npusensei.app.camera

import android.graphics.Bitmap
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.npusensei.app.ml.Detection
import com.npusensei.app.ml.ObjectDetector
import com.npusensei.app.ml.SceneState
import com.npusensei.app.util.BitmapUtils.toBitmapUpright
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.ArrayDeque
import java.util.concurrent.atomic.AtomicBoolean

class FrameAnalyzer(
    private val detector: ObjectDetector,
) : ImageAnalysis.Analyzer {

    private val busy = AtomicBoolean(false)
    private val recentScenes = ArrayDeque<SceneState>()
    private var frameCount: Long = 0
    private var lastLogAt: Long = 0L

    private val _liveDetections = MutableStateFlow<List<Detection>>(emptyList())
    val liveDetections: StateFlow<List<Detection>> = _liveDetections.asStateFlow()

    private val _frameSize = MutableStateFlow(0 to 0)
    val frameSize: StateFlow<Pair<Int, Int>> = _frameSize.asStateFlow()

    private val _scene = MutableStateFlow(SceneState.EMPTY)
    val scene: StateFlow<SceneState> = _scene.asStateFlow()

    @Volatile var paused: Boolean = false

    override fun analyze(image: ImageProxy) {
        if (paused || !busy.compareAndSet(false, true)) {
            image.close()
            return
        }
        var bitmap: Bitmap? = null
        try {
            bitmap = image.toBitmapUpright()
            _frameSize.value = bitmap.width to bitmap.height
            val t0 = System.currentTimeMillis()
            val detections = detector.detect(bitmap)
            val tDetect = System.currentTimeMillis() - t0
            _liveDetections.value = detections

            val newScene = SceneState(
                detections = detections,
                frameWidthPx = bitmap.width,
                frameHeightPx = bitmap.height,
                timestampNs = System.nanoTime(),
            )
            recentScenes.addLast(newScene)
            while (recentScenes.size > WINDOW) recentScenes.removeFirst()
            _scene.value = mergeRecent(recentScenes, newScene)

            frameCount++
            val now = System.currentTimeMillis()
            if (now - lastLogAt > 1000) {
                lastLogAt = now
                val summary = if (detections.isEmpty()) "(none)"
                else detections.joinToString(", ") {
                    "${it.label}=${"%.2f".format(it.score)}"
                }
                val pp = detector.postProcessor
                val topGuess = "top=${pp.lastTopLabel}@${"%.3f".format(pp.lastTopScore)}"
                Log.i(TAG, "frame#$frameCount  ${tDetect}ms  detections=$summary  $topGuess")
            }
        } catch (t: Throwable) {
            Log.e(TAG, "frame analysis failed", t)
        } finally {
            bitmap?.recycle()
            image.close()
            busy.set(false)
        }
    }

    private fun mergeRecent(window: ArrayDeque<SceneState>, latest: SceneState): SceneState {
        val countByLabel = HashMap<String, Int>()
        for (s in window) {
            for (d in s.detections.distinctBy { it.label }) {
                countByLabel.merge(d.label, 1) { a, _ -> a + 1 }
            }
        }
        val keepLabels = countByLabel
            .filterValues { it >= (window.size + 1) / 2 }
            .keys

        val mostRecentByLabel = HashMap<String, Detection>()
        for (s in window) {
            for (d in s.detections) {
                if (d.label in keepLabels) mostRecentByLabel[d.label] = d
            }
        }
        return latest.copy(detections = mostRecentByLabel.values.toList())
    }

    companion object {
        private const val TAG = "FrameAnalyzer"
        private const val WINDOW = 5
    }
}
