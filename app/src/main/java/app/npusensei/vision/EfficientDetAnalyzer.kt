package app.npusensei.vision

import android.graphics.Bitmap
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import app.npusensei.core.models.BoundingBox
import java.util.concurrent.ExecutorService
import java.util.concurrent.atomic.AtomicBoolean

class EfficientDetAnalyzer(
    private val detector: EfficientDetDetector,
    private val inferenceExecutor: ExecutorService,
    private val onResult: (List<BoundingBox>) -> Unit,
    private val onError: (Throwable) -> Unit,
) : ImageAnalysis.Analyzer {
    private val inferenceBusy = AtomicBoolean(false)

    override fun analyze(image: ImageProxy) {
        if (!inferenceBusy.compareAndSet(false, true)) {
            image.close()
            return
        }

        val bitmap = try {
            EfficientDetPreprocessor.imageProxyToBitmap(image)
        } catch (throwable: Throwable) {
            image.close()
            inferenceBusy.set(false)
            onError(throwable)
            return
        }
        image.close()

        inferenceExecutor.execute {
            try {
                onResult(detector.detect(bitmap))
            } catch (throwable: Throwable) {
                Log.e(TAG, "EfficientDet inference failed", throwable)
                onError(throwable)
            } finally {
                if (!bitmap.isRecycled) bitmap.recycle()
                inferenceBusy.set(false)
            }
        }
    }

    private companion object {
        const val TAG = "EfficientDetAnalyzer"
    }
}
