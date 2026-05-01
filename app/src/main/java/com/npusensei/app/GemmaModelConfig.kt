package com.npusensei.app

import android.content.Context
import java.io.File

/**
 * Describes a LiteRT-LM model variant that NPU Sensei can load.
 *
 * Two Gemma 4 E2B variants are pre-configured:
 *   • [GEMMA4_E2B_NPU] – compiled for SM8750 Hexagon NPU (S25 Ultra)
 *   • [GEMMA4_E2B_GPU] – runs on GPU / CPU (any device)
 *
 * Model files are expected under the app's external files directory
 * (`/sdcard/Android/data/com.npusensei.app/files/`) or at a custom
 * absolute path passed to [resolvedPath].
 */
data class GemmaModelConfig(
    val id: String,
    val name: String,
    val filename: String,
    val preferredBackend: String? = null,
    val supportsImage: Boolean = true,
    val supportsAudio: Boolean = false,
    val defaultSystemPrompt: String? = null,
) {
    /** Resolve the absolute path for this model on the device. */
    fun resolvedPath(context: Context): String {
        val dir = context.getExternalFilesDir(null)
            ?: File("/sdcard/Android/data/${context.packageName}/files")
        return File(dir, filename).absolutePath
    }

    fun isAvailable(context: Context): Boolean {
        val f = File(resolvedPath(context))
        return f.exists() && f.canRead() && f.length() > 0
    }

    companion object {
        val GEMMA4_E2B_NPU = GemmaModelConfig(
            id = "gemma4-e2b-npu",
            name = "Gemma 4 E2B (NPU · S25 Ultra)",
            filename = "gemma-4-E2B-it_qualcomm_sm8750.litertlm",
            preferredBackend = "NPU",
            supportsImage = true,
            supportsAudio = true,
            defaultSystemPrompt = "You are NPU Sensei, an electronics expert. " +
                "You look at photos of circuits and components and give direct answers. " +
                "Rules: Keep answers under 3 sentences. NEVER say 'refer to a manual' or " +
                "'consult a datasheet' or 'check the documentation'. " +
                "YOU are the expert — give the answer directly. Be concise.",
        )

        val GEMMA4_E2B_GPU = GemmaModelConfig(
            id = "gemma4-e2b-gpu",
            name = "Gemma 4 E2B (GPU / CPU)",
            filename = "gemma-4-E2B-it.litertlm",
            preferredBackend = "GPU",
            supportsImage = true,
            supportsAudio = true,
            defaultSystemPrompt = GEMMA4_E2B_NPU.defaultSystemPrompt,
        )

        val GEMMA3_1B_NPU = GemmaModelConfig(
            id = "gemma3-1b-npu",
            name = "Gemma 3 1B (NPU · S25 Ultra)",
            filename = "Gemma3-1B-IT_q4_ekv1280_sm8750.litertlm",
            preferredBackend = "NPU",
            supportsImage = false,
            supportsAudio = false,
            defaultSystemPrompt = GEMMA4_E2B_NPU.defaultSystemPrompt,
        )

        /** Models in priority order – the first available one is used by default. */
        val ALL = listOf(GEMMA4_E2B_NPU, GEMMA4_E2B_GPU, GEMMA3_1B_NPU)

        /** Pick the best model that's actually present on disk. */
        fun bestAvailable(context: Context): GemmaModelConfig? =
            ALL.firstOrNull { it.isAvailable(context) }
    }
}
