package app.npusensei.vision

import android.content.Context
import android.util.Log
import androidx.camera.core.ImageAnalysis
import app.npusensei.core.models.BoundingBox
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ExecutorService

/**
 * Main detection manager for the app vision layer.
 *
 * Exposes latest detections as StateFlow so MainViewModel can collect continuously.
 */
class EfficientDetVisionManager(
    context: Context,
    inferenceExecutor: ExecutorService,
    modelAssetName: String = "efficientdet_lite0_detection.tflite",
    labelAssetName: String = "labels.txt",
    confidenceThreshold: Float = 0.35f,
) : AutoCloseable {
    private val detector = EfficientDetDetector(
        context = context,
        modelAssetName = modelAssetName,
        labelAssetName = labelAssetName,
        confidenceThreshold = confidenceThreshold,
    )

    private val _latestBoundingBoxes = MutableStateFlow<List<BoundingBox>>(emptyList())
    val latestBoundingBoxes: StateFlow<List<BoundingBox>> = _latestBoundingBoxes.asStateFlow()

    val imageAnalysis: ImageAnalysis = ImageAnalysis.Builder()
        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
        .build()
        .also { analysis ->
            analysis.setAnalyzer(
                inferenceExecutor,
                EfficientDetAnalyzer(
                    detector = detector,
                    inferenceExecutor = inferenceExecutor,
                    onResult = { detections ->
                        _latestBoundingBoxes.value = detections
                    },
                    onError = { throwable ->
                        Log.e(TAG, "Analyzer error", throwable)
                    },
                ),
            )
        }

    val selectedBackend: String
        get() = detector.selectedBackend

    override fun close() {
        detector.close()
    }

    private companion object {
        const val TAG = "EfficientDetVisionManager"
    }
}
