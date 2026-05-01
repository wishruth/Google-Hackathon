package com.npusensei.app.camera

import android.content.Context
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.common.util.concurrent.ListenableFuture
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraController(
    private val context: Context,
) {
    val analyzerExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private var providerFuture: ListenableFuture<ProcessCameraProvider>? = null
    private var provider: ProcessCameraProvider? = null

    fun bind(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        analyzer: FrameAnalyzer,
    ) {
        providerFuture = ProcessCameraProvider.getInstance(context).also { future ->
            future.addListener({
                provider = future.get()
                bindUseCases(lifecycleOwner, previewView, analyzer)
            }, ContextCompat.getMainExecutor(context))
        }
    }

    private fun bindUseCases(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        analyzer: FrameAnalyzer,
    ) {
        val cameraProvider = provider ?: return
        cameraProvider.unbindAll()

        val resolutionSelector = ResolutionSelector.Builder()
            .setResolutionStrategy(
                ResolutionStrategy(
                    Size(1280, 720),
                    ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER,
                ),
            ).build()

        val preview = Preview.Builder()
            .setResolutionSelector(resolutionSelector)
            .build()
            .apply { surfaceProvider = previewView.surfaceProvider }

        val analysis = ImageAnalysis.Builder()
            .setResolutionSelector(resolutionSelector)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .also { it.setAnalyzer(analyzerExecutor, analyzer) }

        cameraProvider.bindToLifecycle(
            lifecycleOwner,
            CameraSelector.DEFAULT_BACK_CAMERA,
            preview,
            analysis,
        )
    }

    fun shutdown() {
        provider?.unbindAll()
        analyzerExecutor.shutdown()
    }
}
