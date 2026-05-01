package com.npusensei.app

import android.app.Application
import android.util.Log
import com.npusensei.app.circuit.BlueprintRepository
import com.npusensei.app.gemma.GemmaCoach
import com.npusensei.app.gemma.GemmaOnDevice
import com.npusensei.app.gemma.OfflineTemplateCoach
import com.npusensei.app.ml.ObjectDetector

class NpuSenseiApplication : Application() {

    val blueprints by lazy { BlueprintRepository(this) }

    val detector: ObjectDetector by lazy {
        try {
            ObjectDetector(this)
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to load detector", t)
            throw t
        }
    }

    val coach: GemmaCoach by lazy {
        runCatching {
            val file = GemmaOnDevice.resolveModelFile(this)
                ?: error("no model file")
            GemmaOnDevice(this, file)
        }.getOrElse { onDeviceErr ->
            Log.w(TAG, "On-device Gemma unavailable: ${onDeviceErr.message}; using offline templates")
            OfflineTemplateCoach()
        }
    }

    override fun onTerminate() {
        super.onTerminate()
        runCatching { detector.close() }
        runCatching { coach.close() }
    }

    companion object {
        private const val TAG = "NpuSenseiApplication"
    }
}
