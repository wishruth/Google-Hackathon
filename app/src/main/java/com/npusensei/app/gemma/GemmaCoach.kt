package com.npusensei.app.gemma

import android.graphics.Bitmap

interface GemmaCoach : AutoCloseable {
    suspend fun coach(request: CoachRequest): CoachResponse
    val isReady: Boolean
    override fun close() = Unit
}

data class CoachRequest(
    val systemPrompt: String,
    val userPrompt: String,
    val frame: Bitmap? = null,
    val maxTokens: Int = 160,
    val temperature: Float = 0.5f,
)

data class CoachResponse(
    val text: String,
    val source: Source,
    val latencyMs: Long,
) {
    enum class Source { ON_DEVICE, OFFLINE_TEMPLATE }
}
