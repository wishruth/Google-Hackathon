package com.npusensei.app.gemma

import android.content.Context
import android.util.Log
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.genai.llminference.GraphOptions
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

class GemmaOnDevice(
    context: Context,
    private val modelFile: File,
) : GemmaCoach {

    private val llm: LlmInference
    private val mutex = Mutex()
    override val isReady: Boolean get() = true

    init {
        require(modelFile.exists()) {
            "Gemma model not found at ${modelFile.absolutePath}"
        }
        val options = LlmInference.LlmInferenceOptions.builder()
            .setModelPath(modelFile.absolutePath)
            .setMaxTokens(1024)
            .setPreferredBackend(LlmInference.Backend.GPU)
            .build()
        llm = LlmInference.createFromOptions(context, options)
        Log.i(TAG, "Gemma loaded from ${modelFile.absolutePath}")
    }

    override suspend fun coach(request: CoachRequest): CoachResponse =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val started = System.currentTimeMillis()
                val sessionOptions = LlmInferenceSession.LlmInferenceSessionOptions.builder()
                    .setTopK(40)
                    .setTopP(0.95f)
                    .setTemperature(request.temperature)
                    .setGraphOptions(
                        GraphOptions.builder()
                            .setEnableVisionModality(request.frame != null)
                            .build(),
                    )
                    .build()

                val session = LlmInferenceSession.createFromOptions(llm, sessionOptions)
                try {
                    session.addQueryChunk(request.systemPrompt)
                    session.addQueryChunk("\n\n")
                    session.addQueryChunk(request.userPrompt)
                    request.frame?.let { bm ->
                        session.addImage(BitmapImageBuilder(bm).build())
                    }
                    val text = session.generateResponse().trim()
                    CoachResponse(
                        text = text,
                        source = CoachResponse.Source.ON_DEVICE,
                        latencyMs = System.currentTimeMillis() - started,
                    )
                } finally {
                    session.close()
                }
            }
        }

    override fun close() {
        runCatching { llm.close() }
    }

    companion object {
        private const val TAG = "GemmaOnDevice"

        fun resolveModelFile(context: Context): File? {
            val external = File(context.getExternalFilesDir("models"), MODEL_NAME)
            if (external.exists()) return external

            val internal = File(context.filesDir, "models/$MODEL_NAME")
            if (internal.exists()) return internal

            val assetPath = "models/$MODEL_NAME"
            return try {
                context.assets.open(assetPath).use { input ->
                    internal.parentFile?.mkdirs()
                    internal.outputStream().use { input.copyTo(it) }
                }
                internal
            } catch (_: Exception) {
                null
            }
        }

        const val MODEL_NAME = "gemma3n-E2B-it-int4.task"
    }
}
