package com.npusensei.app.gemma

class OfflineTemplateCoach : GemmaCoach {
    override val isReady: Boolean = true

    override suspend fun coach(request: CoachRequest): CoachResponse {
        val statusLine = request.userPrompt.lineSequence()
            .firstOrNull { it.startsWith("STATUS:") }
            ?.removePrefix("STATUS:")
            ?.trim()
            .orEmpty()

        val text = when {
            statusLine.startsWith("Waiting for:") -> {
                val needs = statusLine.removePrefix("Waiting for:").trim()
                "Grab the $needs and place it on the breadboard. Take your time."
            }
            statusLine.startsWith("Step complete") ->
                "Nice — that step is done. Moving on to the next."
            statusLine.startsWith("Wrong component value") ->
                "Heads up — wrong value. ${statusLine.removePrefix("Wrong component value:").trim()}"
            statusLine.startsWith("User placed") -> "That looks off — try again, lining up the rows."
            else -> "Looking good — keep going."
        }
        return CoachResponse(text, CoachResponse.Source.OFFLINE_TEMPLATE, latencyMs = 0L)
    }
}
