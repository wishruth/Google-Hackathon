package com.npusensei.app

import android.content.Context
import android.util.Log
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.withContext
import java.io.File

class GemmaReasoningEngine(private val context: Context) {

    companion object {
        private const val TAG = "GemmaReasoningEngine"
        // Update this path if the model is located elsewhere
        private const val DEFAULT_MODEL_PATH = "/data/local/tmp/Gemma3-1B-IT_q4_ekv1280_sm8750.litertlm"
        private const val TEST_PROMPT = "List 3 steps to wire an LED to a Raspberry Pi GPIO pin"
    }

    private var engine: Engine? = null
    private var isInitialized = false

    /**
     * Initializes the LiteRT-LM Engine explicitly using the NPU delegate.
     */
    suspend fun initializeAndRunTest(modelPath: String = DEFAULT_MODEL_PATH) = withContext(Dispatchers.IO) {
        if (isInitialized) {
            Log.i(TAG, "Engine already initialized.")
            runInferenceTest()
            return@withContext
        }

        Log.i(TAG, "Initializing Gemma Engine on NPU. Model path: $modelPath")
        val file = File(modelPath)
        if (!file.exists()) {
            Log.e(TAG, "Model file not found at $modelPath. Please ensure the model is pushed to the device.")
            return@withContext
        }

        try {
            val libDir = context.applicationInfo.nativeLibraryDir
            // Required native runtime config for Hexagon NPU
            try {
                android.system.Os.setenv("LD_LIBRARY_PATH", libDir, true)
                android.system.Os.setenv("ADSP_LIBRARY_PATH", libDir, true)
                Log.i(TAG, "Set native library paths to $libDir")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to set native library paths: ${e.message}")
            }

            val npuBackend = Backend.NPU(nativeLibraryDir = libDir)
            val engineConfig = EngineConfig(
                modelPath = modelPath,
                backend = npuBackend,
                cacheDir = context.cacheDir.path
            )

            val startTime = System.currentTimeMillis()
            engine = Engine(engineConfig)
            engine?.initialize()
            val initDuration = System.currentTimeMillis() - startTime
            Log.i(TAG, "NPU delegate created and engine initialized SUCCEEDED in ${initDuration}ms")

            isInitialized = true
            runInferenceTest()
            
        } catch (e: Throwable) {
            Log.e(TAG, "Initialization FAILED: ${e.message}", e)
        }
    }

    /**
     * Runs the hardcoded test prompt and measures first-token latency.
     */
    private suspend fun runInferenceTest() = withContext(Dispatchers.IO) {
        val currentEngine = engine ?: return@withContext
        Log.i(TAG, "Running hardcoded inference test...")
        Log.i(TAG, "Prompt: $TEST_PROMPT")

        try {
            val conversation = currentEngine.createConversation(ConversationConfig())
            var firstTokenReceived = false
            var firstTokenTime: Long = 0
            val startTime = System.currentTimeMillis()

            conversation.sendMessageAsync(TEST_PROMPT)
                .onStart {
                    Log.i(TAG, "Stream started...")
                }
                .catch { e ->
                    Log.e(TAG, "Error during inference: ${e.message}", e)
                }
                .onCompletion {
                    Log.i(TAG, "Inference complete.")
                    conversation.close()
                }
                .collect { message ->
                    if (!firstTokenReceived) {
                        firstTokenTime = System.currentTimeMillis() - startTime
                        Log.i(TAG, "First-token latency: ${firstTokenTime}ms")
                        firstTokenReceived = true
                    }
                    val textChunk = message.contents.contents.joinToString("") { content ->
                        when (content) {
                            is com.google.ai.edge.litertlm.Content.Text -> content.text
                            else -> ""
                        }
                    }
                    Log.i(TAG, "Gemma chunk: $textChunk")
                }
        } catch (e: Exception) {
            Log.e(TAG, "Inference execution failed: ${e.message}", e)
        }
    }

    fun cleanup() {
        engine?.close()
        engine = null
        isInitialized = false
    }
}
