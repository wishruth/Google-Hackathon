package com.npusensei.app.gemma

import com.npusensei.app.circuit.BlueprintStep
import com.npusensei.app.circuit.CircuitBlueprint
import com.npusensei.app.circuit.StepStatus
import com.npusensei.app.ml.SceneState

object PromptBuilder {

    val SYSTEM_PROMPT: String = """
You are Circuit Coach, a friendly, calm AR assistant guiding a beginner through
building a Raspberry Pi breadboard circuit. The user is watching their workspace
through their phone camera.

RULES:
- Reply in 1–2 short sentences. Never more than 40 words.
- Speak in second person ("Now grab the…").
- If the user is stuck or makes a mistake, point out the specific component and
  the specific row/pin to fix. Never lecture.
- If you don't know something for sure, say so and propose what to look at next.
- Never include code unless explicitly asked.
""".trimIndent()

    fun buildUserPrompt(
        blueprint: CircuitBlueprint,
        step: BlueprintStep,
        status: StepStatus,
        scene: SceneState,
        userQuestion: String? = null,
    ): String {
        val visible = scene.detections.joinToString(", ") {
            buildString {
                append(it.label)
                it.resistorOhms?.let { o -> append(" (${o}Ω)") }
                append(" @${(it.score * 100).toInt()}%")
            }
        }.ifEmpty { "(nothing recognized yet)" }

        val statusLine = when (status) {
            is StepStatus.WaitingFor -> "Waiting for: ${status.missing.joinToString()}"
            is StepStatus.Misplaced ->
                "User placed ${status.component} but it looks wrong: ${status.reason}"
            StepStatus.Complete -> "Step complete — advance to next."
            StepStatus.Ready -> "All required components visible; user is acting."
            is StepStatus.WrongValue ->
                "Wrong component value: expected ${status.expected}, saw ${status.actual}."
        }

        return buildString {
            append("PROJECT: ").appendLine(blueprint.title)
            append("CURRENT STEP ").append(step.n).append("/").append(blueprint.steps.size)
                .append(": ").appendLine(step.instruction)
            append("VISIBLE ON CAMERA: ").appendLine(visible)
            append("STATUS: ").appendLine(statusLine)
            if (!userQuestion.isNullOrBlank()) {
                append("USER ASKED: ").appendLine(userQuestion)
            }
            append("Respond with ONE short coaching message.")
        }
    }
}
