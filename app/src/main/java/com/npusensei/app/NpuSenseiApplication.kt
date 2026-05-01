package com.npusensei.app

import android.app.Application
import android.util.Log
import com.npusensei.app.circuit.BlueprintRepository
import com.npusensei.app.gemma.PromptBuilder
import com.npusensei.app.ml.ObjectDetector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class NpuSenseiApplication : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val blueprints by lazy { BlueprintRepository(this) }

    val detector: ObjectDetector by lazy {
        try {
            ObjectDetector(this)
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to load detector", t)
            throw t
        }
    }

    val gemmaEngine: GemmaReasoningEngine by lazy { GemmaReasoningEngine(this) }

    @Volatile
    var gemmaReady: Boolean = false
        private set

    fun initGemmaAsync() {
        appScope.launch {
            val config = GemmaModelConfig.bestAvailable(this@NpuSenseiApplication)
                ?: GemmaModelConfig.GEMMA4_E2B_NPU
            Log.i(TAG, "Initializing Gemma: ${config.name}")
            val result = gemmaEngine.initialize(config)
            result.fold(
                onSuccess = {
                    Log.i(TAG, "Gemma ready on ${it.activeBackend} in ${it.initTimeMs}ms")
                    gemmaEngine.startConversation(PromptBuilder.SYSTEM_PROMPT)
                    gemmaReady = true
                },
                onFailure = { Log.e(TAG, "Gemma init failed: ${it.message}") },
            )
        }
    }

    override fun onTerminate() {
        super.onTerminate()
        runCatching { detector.close() }
        runCatching { gemmaEngine.cleanup() }
    }

    companion object {
        private const val TAG = "NpuSenseiApplication"
    }
}
