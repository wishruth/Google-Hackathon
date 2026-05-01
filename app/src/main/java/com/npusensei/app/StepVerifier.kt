package com.npusensei.app

import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.toList

enum class VerificationResult { COMPLETE, INCOMPLETE, UNCERTAIN, ERROR }

data class VerificationOutcome(
    val result: VerificationResult,
    val explanation: String,
)

class StepVerifier(private val engine: GemmaReasoningEngine) {

    suspend fun verify(step: CircuitStep, imagePath: String): VerificationOutcome {
        if (!engine.initialized) {
            return VerificationOutcome(VerificationResult.ERROR, "Gemma engine is not ready yet.")
        }

        val chunks = mutableListOf<String>()
        try {
            engine.sendImageMessage(
                text = step.verificationPrompt,
                imagePath = imagePath,
            ).catch { e ->
                chunks.add("Error: ${e.message}")
            }.toList(chunks)
        } catch (e: Exception) {
            return VerificationOutcome(VerificationResult.ERROR, e.message ?: "Unknown error")
        }

        val response = chunks.joinToString("")
        if (response.isBlank()) {
            return VerificationOutcome(VerificationResult.ERROR, "No response from Gemma.")
        }

        val lower = response.lowercase()
        val result = when {
            lower.startsWith("yes") || lower.contains("yes") && !lower.contains("no") -> VerificationResult.COMPLETE
            lower.startsWith("no") || lower.contains("no") && !lower.contains("yes") -> VerificationResult.INCOMPLETE
            else -> VerificationResult.UNCERTAIN
        }

        return VerificationOutcome(result, response.trim())
    }
}
