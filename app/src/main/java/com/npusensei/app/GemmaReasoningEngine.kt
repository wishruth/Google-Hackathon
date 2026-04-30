package com.npusensei.app

import android.content.Context
import android.util.Log
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File

data class InferenceMetrics(
    val initTimeMs: Long = 0,
    val firstTokenMs: Long = 0,
    val activeBackend: String = "Unknown",
)

/**
 * Manages Gemma 4 E2B inference via LiteRT-LM.
 *
 * Supports multimodal inputs (text + image) with an NPU → GPU → CPU
 * fallback chain on Snapdragon 8 Elite.  Vision is routed to NPU when the
 * text backend is NPU, otherwise falls back to CPU.
 */
class GemmaReasoningEngine(private val context: Context) {

    private var engine: Engine? = null
    private var conversation: Conversation? = null
    private var isInitialized = false
    private var activeBackend: String = "Unknown"
    private var nativeRuntimeConfigured = false

    val initialized: Boolean get() = isInitialized
    val backend: String get() = activeBackend

    // ── Initialization ──────────────────────────────────────────────────

    suspend fun initialize(
        modelConfig: GemmaModelConfig = GemmaModelConfig.GEMMA4_E2B_NPU,
    ): Result<InferenceMetrics> = withContext(Dispatchers.IO) {
        if (isInitialized) cleanup()

        val modelPath = modelConfig.resolvedPath(context)
        Log.i(TAG, "Initializing Gemma engine · model=${modelConfig.name} path=$modelPath")

        val file = File(modelPath)
        if (!file.exists() || !file.canRead()) {
            val msg = "Model not found at $modelPath – push the .litertlm to the device first"
            Log.e(TAG, msg)
            return@withContext Result.failure(IllegalStateException(msg))
        }

        val backends = buildBackendChain(modelConfig.preferredBackend)
        val startTime = System.currentTimeMillis()

        try {
            initEngineWithFallback(modelPath, backends, modelConfig)
            val elapsed = System.currentTimeMillis() - startTime
            isInitialized = true
            Log.i(TAG, "Engine ready on $activeBackend in ${elapsed}ms")
            Result.success(InferenceMetrics(initTimeMs = elapsed, activeBackend = activeBackend))
        } catch (e: Throwable) {
            Log.e(TAG, "All backends failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    // ── Conversation management ─────────────────────────────────────────

    fun startConversation(systemPrompt: String? = null) {
        requireEngine()
        conversation?.close()
        val config = ConversationConfig(
            systemInstruction = systemPrompt?.let { Contents.of(it) },
        )
        conversation = engine!!.createConversation(config)
        Log.i(TAG, "Conversation started on $activeBackend")
    }

    fun resetConversation() {
        conversation?.close()
        conversation = null
    }

    // ── Inference ───────────────────────────────────────────────────────

    /** Send a text-only prompt and stream the response. */
    fun sendMessage(text: String): Flow<String> {
        ensureConversation()
        return conversation!!.sendMessageAsync(text).map { it.extractText() }
    }

    /**
     * Send a multimodal prompt (image + text) and stream the response.
     * [imagePath] must point to a JPEG/PNG readable by the runtime.
     */
    fun sendImageMessage(text: String, imagePath: String): Flow<String> {
        ensureConversation()
        val parts = mutableListOf<Content>()
        parts.add(Content.ImageFile(imagePath))
        parts.add(Content.Text(text))
        val contents = Contents.of(*parts.toTypedArray())
        return conversation!!.sendMessageAsync(contents).map { it.extractText() }
    }

    // ── Cleanup ─────────────────────────────────────────────────────────

    fun cleanup() {
        try {
            conversation?.close()
            engine?.close()
        } catch (e: Exception) {
            Log.w(TAG, "Cleanup error: ${e.message}")
        }
        conversation = null
        engine = null
        isInitialized = false
        activeBackend = "Unknown"
    }

    // ── Internals ───────────────────────────────────────────────────────

    private fun buildBackendChain(preferred: String?): List<BackendFactory> {
        val libDir = context.applicationInfo.nativeLibraryDir
        val all = listOf(
            BackendFactory("NPU", libDir) { Backend.NPU(nativeLibraryDir = libDir) },
            BackendFactory("GPU") { Backend.GPU() },
            BackendFactory("CPU") { Backend.CPU() },
        )
        if (preferred == null) return all
        val idx = all.indexOfFirst { it.name.equals(preferred, ignoreCase = true) }
        return if (idx > 0) listOf(all[idx]) + all.filterIndexed { i, _ -> i != idx } else all
    }

    private fun initEngineWithFallback(
        modelPath: String,
        backends: List<BackendFactory>,
        config: GemmaModelConfig,
    ) {
        var lastError: Throwable? = null
        for (factory in backends) {
            try {
                Log.i(TAG, "Trying backend: ${factory.name}")
                tryInitEngine(modelPath, factory, config)
                return
            } catch (e: Throwable) {
                Log.w(TAG, "${factory.name} failed: ${e.message}")
                lastError = e
            }
        }
        throw lastError ?: IllegalStateException("No backends available")
    }

    private fun tryInitEngine(
        modelPath: String,
        factory: BackendFactory,
        config: GemmaModelConfig,
    ) {
        val libDir = factory.nativeLibDir ?: context.applicationInfo.nativeLibraryDir
        if (factory.name == "NPU") configureNativeRuntime(libDir)

        val textBackend = factory.create()

        val visionBackend = when {
            !config.supportsImage -> null
            factory.name == "NPU" -> Backend.NPU(nativeLibraryDir = libDir)
            else -> Backend.CPU()
        }
        val audioBackend = if (config.supportsAudio) Backend.CPU() else null

        val engineConfig = EngineConfig(
            modelPath = modelPath,
            backend = textBackend,
            visionBackend = visionBackend,
            audioBackend = audioBackend,
            maxNumImages = if (config.supportsImage) 1 else 0,
            cacheDir = context.cacheDir.path,
        )

        val candidate = Engine(engineConfig)
        try {
            candidate.initialize()
            // Verify a conversation can be created
            candidate.createConversation(ConversationConfig()).close()
        } catch (e: Throwable) {
            candidate.close()
            throw e
        }

        engine = candidate
        activeBackend = factory.name
    }

    @Synchronized
    private fun configureNativeRuntime(libDir: String) {
        if (nativeRuntimeConfigured) return
        try {
            android.system.Os.setenv("LD_LIBRARY_PATH", libDir, true)
            android.system.Os.setenv("ADSP_LIBRARY_PATH", libDir, true)
            Log.i(TAG, "Native lib paths → $libDir")
        } catch (e: Exception) {
            Log.w(TAG, "Could not set native lib paths: ${e.message}")
        }
        nativeRuntimeConfigured = true
    }

    private fun requireEngine() {
        check(isInitialized && engine != null) { "Call initialize() before using the engine" }
    }

    private fun ensureConversation() {
        requireEngine()
        if (conversation == null) startConversation()
    }

    private fun Message.extractText(): String =
        contents.contents
            .filterIsInstance<Content.Text>()
            .joinToString("") { it.text }

    companion object {
        private const val TAG = "GemmaReasoningEngine"
    }
}

private data class BackendFactory(
    val name: String,
    val nativeLibDir: String? = null,
    val create: () -> Backend,
)
