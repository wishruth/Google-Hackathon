package app.npusensei.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import app.npusensei.core.models.BoundingBox
import app.npusensei.vision.EfficientDetVisionManager
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.Executors

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val inferenceExecutor = Executors.newSingleThreadExecutor()

    private val visionManager = EfficientDetVisionManager(
        context = application.applicationContext,
        inferenceExecutor = inferenceExecutor,
    )

    val latestBoundingBoxes: StateFlow<List<BoundingBox>> = visionManager.latestBoundingBoxes
    val imageAnalysis = visionManager.imageAnalysis
    val selectedBackend: String
        get() = visionManager.selectedBackend

    override fun onCleared() {
        super.onCleared()
        visionManager.close()
        inferenceExecutor.shutdown()
    }
}
